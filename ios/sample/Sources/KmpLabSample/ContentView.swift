import SwiftUI
import KmpLabSDK

struct ContentView: View {
    @StateObject private var viewModel = SampleViewModel()

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            header

            searchBar

            content
                .frame(maxWidth: .infinity, alignment: .leading)

            Spacer(minLength: 0)

            if let rateLimit = viewModel.rateLimit {
                RateLimitFooter(rateLimit: rateLimit)
            }
        }
        .padding(.horizontal, 16)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("KmpLab SDK")
                .font(.title2.weight(.semibold))
            Text("Every call below goes through the shared network layer.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }

    private var searchBar: some View {
        HStack(spacing: 12) {
            TextField("GitHub login", text: $viewModel.query)
                .textFieldStyle(.roundedBorder)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .onSubmit { viewModel.load() }

            Button("Load") { viewModel.load() }
                .buttonStyle(.borderedProminent)
                .disabled(viewModel.isLoading)
        }
    }

    @ViewBuilder
    private var content: some View {
        if viewModel.isLoading {
            ProgressView()
                .frame(maxWidth: .infinity)
        } else if let message = viewModel.errorMessage {
            Text(message)
                .font(.callout)
                .foregroundStyle(.red)
                .padding(12)
                .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
        } else if let user = viewModel.user {
            VStack(alignment: .leading, spacing: 16) {
                UserCard(user: user)
                Text("Repositories (\(viewModel.repositories.count))")
                    .font(.headline)
                List(viewModel.repositories) { RepositoryRow(repository: $0) }
                    .listStyle(.plain)
            }
        } else {
            Text("Enter a login and press Load.")
                .font(.callout)
                .foregroundStyle(.secondary)
        }
    }
}

private struct UserCard: View {
    let user: GitHubUser

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(user.displayName).font(.title3.weight(.semibold))
            Text("@\(user.login)")
                .font(.subheadline)
                .foregroundStyle(.secondary)

            if let bio = user.bio {
                Text(bio).font(.callout)
            }

            Text("\(user.publicRepoCount) repos · \(user.followers) followers · \(user.following) following")
                .font(.caption.weight(.medium))
                .padding(.top, 4)

            if let location = user.location {
                Text(location).font(.caption).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
    }
}

private struct RepositoryRow: View {
    let repository: Repository

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(repository.name).font(.subheadline.weight(.semibold))

            if let description = repository.description {
                Text(description).font(.caption).foregroundStyle(.secondary)
            }

            Text(subtitle)
                .font(.caption2)
        }
        .padding(.vertical, 4)
    }

    private var subtitle: String {
        var parts: [String] = []
        if let language = repository.language { parts.append(language) }
        parts.append("★ \(repository.stars)")
        parts.append("⑂ \(repository.forks)")
        if repository.isFork { parts.append("fork") }
        if repository.isArchived { parts.append("archived") }
        return parts.joined(separator: " · ")
    }
}

private struct RateLimitFooter: View {
    let rateLimit: RateLimitStatus

    var body: some View {
        VStack(spacing: 0) {
            Divider()
            Text("Rate limit \(rateLimit.remaining)/\(rateLimit.limit) remaining")
                .font(.caption2.monospaced())
                .foregroundStyle(rateLimit.isExhausted ? AnyShapeStyle(.red) : AnyShapeStyle(.secondary))
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 8)
        }
    }
}

#Preview {
    ContentView()
}
