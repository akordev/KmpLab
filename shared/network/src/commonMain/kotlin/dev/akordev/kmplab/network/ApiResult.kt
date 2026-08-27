package dev.akordev.kmplab.network

/**
 * The shape every SDK call returns. Errors are values, not exceptions: Kotlin
 * exceptions do not survive the bridge as anything a Swift `catch` can match on,
 * so the failure cases live in the type instead.
 *
 * SKIE turns a sealed class into a real Swift enum, switched exhaustively with
 * `onEnum(of:)`. The type argument is what is under test here — Objective-C
 * generics are the only thing carrying `T` across, and they only exist on
 * classes, so `T` is a class type parameter and nothing else.
 *
 * [T] is bounded by `Any` because Objective-C has no representation for a
 * nullable type argument; absence is expressed by `ApiResult<Unit>` or by an
 * optional inside the payload, never by `ApiResult<T?>`.
 */
sealed class ApiResult<out T : Any> {

    data class Success<out T : Any>(val data: T) : ApiResult<T>()

    /**
     * Generic even though it carries no `T`. The idiomatic Kotlin spelling is
     * `Failure : ApiResult<Nothing>()`, and it reads fine from Swift, but
     * Objective-C generics are invariant there: `ApiResultFailure` arrives as
     * `ApiResult<KotlinNothing>`, which Swift will not accept where
     * `ApiResult<User>` is wanted. Carrying `T` keeps the failure case
     * constructible from Swift at the cost of one unused type parameter.
     */
    data class Failure<out T : Any>(val error: ApiError) : ApiResult<T>()
}

/** A nested sealed hierarchy, to see whether the enum treatment survives one level down. */
sealed class ApiError {

    /** No response at all — offline, DNS, TLS, timeout. */
    data class Network(val message: String) : ApiError()

    /** A response arrived and it was not a success. */
    data class Http(val code: Int, val message: String) : ApiError()

    /** The response body did not match what the SDK expects. */
    data class Decoding(val message: String) : ApiError()
}

/** A payload type, so the generic argument is something visible in Swift. */
data class User(
    val id: String,
    val name: String,
    val email: String,
)
