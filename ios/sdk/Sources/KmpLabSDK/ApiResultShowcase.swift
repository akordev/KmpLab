// What `ApiResult<T>` looks like once SKIE has been through it.
//
// Nothing here is part of the SDK surface — it is a showcase, kept compiling so
// the Kotlin-to-Swift shape stays visible. Everything it touches is a Kotlin
// type, which the real surface is supposed to hide.
import Foundation
import KmpLabNetwork

enum ApiResultShowcase {

    /// The sealed class arrives as a Swift enum, switched exhaustively.
    static func plain() {
        switch onEnum(of: MockApi.shared.user()) {
        case .success(let success):
            let user: User = success.data
            print("user:", user.name, user.email)
        case .failure(let failure):
            print("failed:", failure.error)
        }
    }

    /// `ApiError` is sealed too, so the same treatment nests.
    static func errors() {
        guard case .failure(let failure) = onEnum(of: MockApi.shared.failingUser()) else { return }

        switch onEnum(of: failure.error) {
        case .network(let error): print("offline:", error.message)
        case .http(let error): print("http \(error.code):", error.message)
        case .decoding(let error): print("bad body:", error.message)
        }
    }

    /// The wart: Objective-C generics carry `T`, but not `T`'s own arguments.
    /// `ApiResult<List<User>>` lands as `ApiResult<NSArray>`, so the element
    /// type has to be recovered by hand.
    static func collections() {
        guard case .success(let success) = onEnum(of: MockApi.shared.users()) else { return }

        let users = success.data as? [User] ?? []
        print("users:", users.map(\.name))
    }

    /// Kotlin primitives and `Unit` box on the way across.
    static func boxedPayloads() {
        if case .success = onEnum(of: MockApi.shared.signOut()) {
            print("signed out")
        }
        if case .success(let success) = onEnum(of: MockApi.shared.unreadCount()) {
            let count: Int32 = success.data.int32Value
            print("unread:", count)
        }
    }

    /// `suspend` becomes `async throws`. The throw is for cancellation — the
    /// failure the caller cares about is still a value in the result.
    static func suspending() async throws {
        let result = try await MockApi.shared.fetchUser(id: "u-1")

        if case .success(let success) = onEnum(of: result) {
            print("fetched:", success.data.name)
        }
    }

    /// `Flow` becomes an `AsyncSequence`, so this is a plain `for await`.
    static func streaming() async {
        for await result in MockApi.shared.observeUsers() {
            switch onEnum(of: result) {
            case .success(let success):
                print("batch of", (success.data as? [User])?.count ?? 0)
            case .failure(let failure):
                print("stream failed:", failure.error)
            }
        }
    }

    /// Building the cases from the Swift side. This is why `Failure` carries an
    /// unused type parameter in Kotlin: as `ApiResult<Nothing>` it would be
    /// `ApiResult<KotlinNothing>` here, which will not stand in for
    /// `ApiResult<User>` — Objective-C generics are invariant in Swift.
    static func construct() {
        let ok: ApiResult<User> = ApiResultSuccess(data: User(id: "u-9", name: "Local", email: "local@example.com"))
        let bad: ApiResult<User> = ApiResultFailure(error: ApiError.Network(message: "offline"))

        print(ok, bad)
    }
}
