import XCTest
@testable import KmpLabSDK

/// Pure-Swift coverage of the public surface. The Kotlin fold in `NetworkBridge`
/// needs a device or simulator, so it is exercised from the sample app instead.
final class SDKErrorTests: XCTestCase {

    func testEachErrorCaseHasAReadableDescription() {
        let errors: [SDKError] = [
            .notFound(resource: "ghost"),
            .rateLimited(resetAt: Date(timeIntervalSince1970: 1_755_600_000)),
            .unauthorized,
            .http(status: 500, message: "Server Error"),
            .offline(message: "no route to host"),
        ]

        for error in errors {
            XCTAssertFalse(
                error.localizedDescription.isEmpty,
                "\(error) produced an empty description"
            )
        }
    }

    func testNotFoundNamesTheResourceThatWasAskedFor() {
        let error = SDKError.notFound(resource: "ghost")

        XCTAssertEqual(error, .notFound(resource: "ghost"))
        XCTAssertTrue(error.localizedDescription.contains("ghost"))
    }

    func testRateLimitStatusReportsExhaustion() {
        let spent = RateLimitStatus(limit: 60, remaining: 0, resetAt: .distantFuture)
        let healthy = RateLimitStatus(limit: 60, remaining: 41, resetAt: .distantFuture)

        XCTAssertTrue(spent.isExhausted)
        XCTAssertFalse(healthy.isExhausted)
    }
}
