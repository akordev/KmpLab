package dev.akordev.kmplab.network

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Hardcoded stand-ins for the real network layer. They exist to pin down what
 * [ApiResult] looks like from Swift — every interesting position the type can
 * appear in, and nothing else.
 */
object MockApi {

    /** The plain case: a concrete type argument, returned synchronously. */
    fun user(): ApiResult<User> = ApiResult.Success(SAMPLE_USER)

    /** The failure branch, declared as `ApiResult<User>` even though it carries no user. */
    fun failingUser(): ApiResult<User> = ApiResult.Failure(ApiError.Http(code = 404, message = "Not Found"))

    /** A collection as the type argument — `List<T>` crosses as `NSArray`. */
    fun users(): ApiResult<List<User>> = ApiResult.Success(SAMPLE_USERS)

    /** No payload. The stand-in for `ApiResult<Void>`, which Objective-C cannot express. */
    fun signOut(): ApiResult<Unit> = ApiResult.Success(Unit)

    /** A primitive type argument, which has to box on the way across. */
    fun unreadCount(): ApiResult<Int> = ApiResult.Success(42)

    /** Suspend: SKIE turns this into a Swift `async` method. */
    suspend fun fetchUser(id: String): ApiResult<User> {
        delay(300)
        return if (id == SAMPLE_USER.id) {
            ApiResult.Success(SAMPLE_USER)
        } else {
            ApiResult.Failure(ApiError.Http(code = 404, message = "No user with id $id"))
        }
    }

    /**
     * Flow: SKIE retypes the Objective-C result through apinotes, so Swift gets a
     * `SkieSwiftFlow` — an `AsyncSequence` — rather than the bare Kotlin `Flow`
     * the generated header still shows.
     */
    fun observeUsers(): Flow<ApiResult<List<User>>> = flow {
        emit(ApiResult.Success(emptyList()))
        delay(300)
        emit(ApiResult.Success(SAMPLE_USERS))
        delay(300)
        emit(ApiResult.Failure(ApiError.Network("Connection lost")))
    }

    private val SAMPLE_USER = User(
        id = "u-1",
        name = "Ada Lovelace",
        email = "ada@example.com",
    )

    private val SAMPLE_USERS = listOf(
        SAMPLE_USER,
        User(id = "u-2", name = "Grace Hopper", email = "grace@example.com"),
        User(id = "u-3", name = "Alan Turing", email = "alan@example.com"),
    )
}

/**
 * A *function* type parameter rather than a class one. Objective-C generics only
 * exist on classes, so this is where `T` erases; it is here to make that visible
 * in the generated Swift rather than to be used.
 */
fun <T : Any> succeed(value: T): ApiResult<T> = ApiResult.Success(value)
