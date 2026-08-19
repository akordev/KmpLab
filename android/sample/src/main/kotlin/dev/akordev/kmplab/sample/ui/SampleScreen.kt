package dev.akordev.kmplab.sample.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.akordev.kmplab.sample.SampleUiState
import dev.akordev.kmplab.sample.SampleViewModel
import dev.akordev.kmplab.sdk.model.GitHubUser
import dev.akordev.kmplab.sdk.model.RateLimitStatus
import dev.akordev.kmplab.sdk.model.Repository

@Composable
fun SampleScreen(viewModel: SampleViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SampleScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::load,
    )
}

@Composable
private fun SampleScreen(
    state: SampleUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        Text(
            text = "KmpLab SDK",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Every call below goes through the shared network layer.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("GitHub login") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
            Spacer(Modifier.width(12.dp))
            Button(onClick = onSubmit, enabled = !state.isLoading) {
                Text("Load")
            }
        }

        Spacer(Modifier.height(16.dp))

        when {
            state.isLoading -> LoadingIndicator()
            state.error != null -> ErrorCard(state.error)
            state.user != null -> Results(state.user, state.repositories)
            else -> Text(
                text = "Enter a login and press Load.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.weight(1f, fill = false))
        state.rateLimit?.let { RateLimitFooter(it) }
    }
}

@Composable
private fun LoadingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorCard(message: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Results(user: GitHubUser, repositories: List<Repository>) {
    Column {
        UserCard(user)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Repositories (${repositories.size})",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(repositories, key = { it.fullName }) { RepositoryRow(it) }
        }
    }
}

@Composable
private fun UserCard(user: GitHubUser) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(user.displayName, style = MaterialTheme.typography.titleLarge)
            Text(
                text = "@${user.login}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            user.bio?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "${user.publicRepoCount} repos · ${user.followers} followers · " +
                    "${user.following} following",
                style = MaterialTheme.typography.labelLarge,
            )
            user.location?.let {
                Text(it, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun RepositoryRow(repository: Repository) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(repository.name, style = MaterialTheme.typography.titleSmall)
            repository.description?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    repository.language?.let { append(it).append(" · ") }
                    append("★ ${repository.stars}")
                    append(" · ⑂ ${repository.forks}")
                    if (repository.isFork) append(" · fork")
                    if (repository.isArchived) append(" · archived")
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun RateLimitFooter(rateLimit: RateLimitStatus) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider()
        Text(
            text = "Rate limit ${rateLimit.remaining}/${rateLimit.limit} remaining",
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = if (rateLimit.isExhausted) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
