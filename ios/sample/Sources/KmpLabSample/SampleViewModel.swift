import Foundation
import KmpLabSDK

/// Drives the sample screen. Note what it does *not* do: no HTTP, no JSON, no
/// retry policy. That all lives behind ``KmpLabClient``, which is the point of
/// the exercise.
@MainActor
final class SampleViewModel: ObservableObject {

    @Published var query: String = "akordev"
    @Published private(set) var isLoading = false
    @Published private(set) var user: GitHubUser?
    @Published private(set) var repositories: [Repository] = []
    @Published private(set) var errorMessage: String?
    @Published private(set) var rateLimit: RateLimitStatus?

    private let client: KmpLabClient
    private var inFlight: Task<Void, Never>?

    init(client: KmpLabClient = KmpLabClient()) {
        self.client = client
    }

    deinit {
        inFlight?.cancel()
    }

    func load() {
        let login = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !login.isEmpty else { return }

        // A new search supersedes whatever is still running.
        inFlight?.cancel()
        inFlight = Task { [weak self] in
            guard let self else { return }

            self.isLoading = true
            self.errorMessage = nil

            do {
                let user = try await self.client.user(login: login)
                // A failure listing repositories should not blank out the profile
                // we already have.
                let repositories = (try? await self.client.repositories(login: login, limit: 30)) ?? []

                guard !Task.isCancelled else { return }
                self.user = user
                self.repositories = repositories
            } catch {
                guard !Task.isCancelled else { return }
                self.user = nil
                self.repositories = []
                self.errorMessage = (error as? SDKError)?.localizedDescription
                    ?? error.localizedDescription
            }

            self.rateLimit = await self.client.rateLimit
            self.isLoading = false
        }
    }
}
