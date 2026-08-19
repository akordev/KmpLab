package dev.akordev.kmplab.sample

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.akordev.kmplab.sdk.KmpLabSdk
import dev.akordev.kmplab.sdk.SdkError
import dev.akordev.kmplab.sdk.SdkResult
import dev.akordev.kmplab.sdk.model.GitHubUser
import dev.akordev.kmplab.sdk.model.RateLimitStatus
import dev.akordev.kmplab.sdk.model.Repository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class SampleUiState(
    val query: String = "akordev",
    val isLoading: Boolean = false,
    val user: GitHubUser? = null,
    val repositories: List<Repository> = emptyList(),
    val error: String? = null,
    val rateLimit: RateLimitStatus? = null,
)

/**
 * Drives the sample screen. Note what it does *not* do: no HTTP, no JSON, no
 * retry policy. That all lives behind [KmpLabSdk], which is the point of the
 * exercise.
 */
class SampleViewModel(private val sdk: KmpLabSdk) : ViewModel() {

    private val mutableState = MutableStateFlow(SampleUiState())
    val state: StateFlow<SampleUiState> = mutableState.asStateFlow()

    private var inFlight: Job? = null

    init {
        // The SDK publishes the rate-limit budget it saw on the last call.
        sdk.rateLimit
            .onEach { budget -> mutableState.value = mutableState.value.copy(rateLimit = budget) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(value: String) {
        mutableState.value = mutableState.value.copy(query = value)
    }

    fun load() {
        val login = mutableState.value.query.trim()
        if (login.isEmpty()) return

        // A new search supersedes whatever is still running.
        inFlight?.cancel()
        inFlight = viewModelScope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, error = null)

            when (val user = sdk.user(login)) {
                is SdkResult.Failure -> {
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        user = null,
                        repositories = emptyList(),
                        error = user.error.toMessage(login),
                    )
                }

                is SdkResult.Success -> {
                    val repositories = when (val repos = sdk.repositories(login, limit = 30)) {
                        is SdkResult.Success -> repos.data
                        is SdkResult.Failure -> emptyList()
                    }
                    mutableState.value = mutableState.value.copy(
                        isLoading = false,
                        user = user.data,
                        repositories = repositories,
                        error = null,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        sdk.close()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SampleViewModel(KmpLabSdk.create()) }
        }
    }
}

/**
 * Turning [SdkError] into something a person can read is the app's job, not the
 * SDK's — the SDK's job was to make the distinction available in the first place.
 */
private fun SdkError.toMessage(login: String): String = when (this) {
    is SdkError.NotFound -> "No GitHub account called \"$login\"."
    is SdkError.RateLimited -> "GitHub rate limit reached. Add a token, or wait for the reset."
    SdkError.Unauthorized -> "That token was rejected by GitHub."
    is SdkError.Http -> "GitHub returned $status: $message"
    is SdkError.Offline -> "Could not reach GitHub. Check your connection."
}
