package dev.akordev.kmplab.sdk

/**
 * Every SDK call returns one of these. Nothing in the public surface throws for
 * a failure the caller can reasonably expect — only coroutine cancellation
 * propagates.
 */
public sealed interface SdkResult<out T> {

    public data class Success<out T>(val data: T) : SdkResult<T>

    public data class Failure(val error: SdkError) : SdkResult<Nothing>
}

/** The payload on success, or `null` on any failure. */
public fun <T> SdkResult<T>.getOrNull(): T? = (this as? SdkResult.Success)?.data

/** The error on failure, or `null` on success. */
public fun <T> SdkResult<T>.errorOrNull(): SdkError? = (this as? SdkResult.Failure)?.error

/** Maps the success payload, leaving a failure untouched. */
public inline fun <T, R> SdkResult<T>.map(transform: (T) -> R): SdkResult<R> = when (this) {
    is SdkResult.Success -> SdkResult.Success(transform(data))
    is SdkResult.Failure -> this
}
