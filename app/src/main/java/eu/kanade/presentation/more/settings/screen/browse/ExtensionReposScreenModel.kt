package eu.kanade.presentation.more.settings.screen.browse

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import dev.icerock.moko.resources.StringResource
import eu.kanade.tachiyomi.extension.ExtensionManager
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import mihon.domain.extensionrepo.interactor.CreateExtensionRepo
import mihon.domain.extensionrepo.interactor.DeleteExtensionRepo
import mihon.domain.extensionrepo.interactor.GetExtensionRepo
import mihon.domain.extensionrepo.interactor.ReplaceExtensionRepo
import mihon.domain.extensionrepo.interactor.ToggleExtensionRepoVisibility
import mihon.domain.extensionrepo.interactor.UpdateExtensionRepo
import mihon.domain.extensionrepo.model.ExtensionRepo
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import yomira.i18n.YMR

class ExtensionReposScreenModel(
    private val getExtensionRepo: GetExtensionRepo = Injekt.get(),
    private val createExtensionRepo: CreateExtensionRepo = Injekt.get(),
    private val deleteExtensionRepo: DeleteExtensionRepo = Injekt.get(),
    private val replaceExtensionRepo: ReplaceExtensionRepo = Injekt.get(),
    private val updateExtensionRepo: UpdateExtensionRepo = Injekt.get(),
    private val toggleRepoVisibility: ToggleExtensionRepoVisibility = Injekt.get(),
    // YMR -->
    private val extensionManager: ExtensionManager = Injekt.get(),
    // YMR <--
) : StateScreenModel<RepoScreenState>(RepoScreenState.Loading) {

    private val _events: Channel<RepoEvent> = Channel(Int.MAX_VALUE)
    val events = _events.receiveAsFlow()

    init {
        screenModelScope.launchIO {
            getExtensionRepo.subscribeAll()
                .collectLatest { repos ->
                    mutableState.update {
                        RepoScreenState.Success(
                            repos = repos.toImmutableSet(),
                        )
                    }
                }
        }
    }

    /**
     * Creates and adds a new repo to the database.
     *
     * @param baseUrl The baseUrl of the repo to create.
     */
    fun createRepo(baseUrl: String) {
        screenModelScope.launchIO {
            // YMR -->
            mutableState.update { if (it is RepoScreenState.Success) it.copy(isAdding = true) else it }
            val result = createExtensionRepo.await(baseUrl)
            mutableState.update { if (it is RepoScreenState.Success) it.copy(isAdding = false) else it }
            // YMR <--
            when (result) {
                CreateExtensionRepo.Result.InvalidUrl -> _events.send(RepoEvent.InvalidUrl)
                CreateExtensionRepo.Result.RepoAlreadyExists -> _events.send(RepoEvent.RepoAlreadyExists)
                is CreateExtensionRepo.Result.DuplicateFingerprint -> {
                    showDialog(RepoDialog.Conflict(result.oldRepo, result.newRepo))
                }
                // YMR -->
                is CreateExtensionRepo.Result.Added -> {
                    _events.send(RepoEvent.Formatted(YMR.strings.repo_added, listOf(result.repo.name, result.extensionCount)))
                    // The extension list only learns about new repositories when it is refreshed,
                    // which on a TV has no pull-to-refresh gesture to trigger it.
                    extensionManager.findAvailableExtensions()
                }
                is CreateExtensionRepo.Result.Unreachable ->
                    showDialog(RepoDialog.Error(YMR.strings.repo_error_unreachable, result.url, result.reason))
                is CreateExtensionRepo.Result.NotARepository ->
                    showDialog(RepoDialog.Error(YMR.strings.repo_error_not_repository, result.url, result.reason))
                CreateExtensionRepo.Result.UnsupportedStoreV2 ->
                    showDialog(RepoDialog.Error(YMR.strings.repo_error_store_v2, baseUrl, null))
                // YMR <--
                else -> {}
            }
        }
    }

    /**
     * Inserts a repo to the database, replace a matching repo with the same signing key fingerprint if found.
     *
     * @param newRepo The repo to insert
     */
    fun replaceRepo(newRepo: ExtensionRepo) {
        screenModelScope.launchIO {
            replaceExtensionRepo.await(newRepo)
        }
    }

    /**
     * Refreshes information for each repository.
     */
    fun refreshRepos() {
        val status = state.value

        if (status is RepoScreenState.Success) {
            screenModelScope.launchIO {
                updateExtensionRepo.awaitAll()
                // YMR -->
                extensionManager.findAvailableExtensions()
                // YMR <--
            }
        }
    }

    /**
     * Deletes the given repo from the database
     */
    fun deleteRepo(baseUrl: String) {
        screenModelScope.launchIO {
            deleteExtensionRepo.await(baseUrl)
        }
    }

    fun onToggleRepoVisibility(baseUrl: String, isVisible: Boolean) {
        screenModelScope.launchIO {
            toggleRepoVisibility.await(baseUrl, isVisible)
        }
    }

    fun showDialog(dialog: RepoDialog) {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = dialog)
            }
        }
    }

    fun dismissDialog() {
        mutableState.update {
            when (it) {
                RepoScreenState.Loading -> it
                is RepoScreenState.Success -> it.copy(dialog = null)
            }
        }
    }
}

sealed class RepoEvent {
    sealed class LocalizedMessage(val stringRes: StringResource) : RepoEvent()
    data object InvalidUrl : LocalizedMessage(MR.strings.invalid_repo_name)
    data object RepoAlreadyExists : LocalizedMessage(MR.strings.error_repo_exists)

    // YMR -->
    data class Formatted(val stringRes: StringResource, val args: List<Any>) : RepoEvent()
    // YMR <--
}

sealed class RepoDialog {
    data object Create : RepoDialog()
    data class Delete(val repo: String) : RepoDialog()
    data class Conflict(val oldRepo: ExtensionRepo, val newRepo: ExtensionRepo) : RepoDialog()
    data class Confirm(val url: String) : RepoDialog()

    // YMR -->
    data class Error(val message: StringResource, val url: String, val detail: String?) : RepoDialog()
    // YMR <--
}

sealed class RepoScreenState {

    @Immutable
    data object Loading : RepoScreenState()

    @Immutable
    data class Success(
        val repos: ImmutableSet<ExtensionRepo>,
        val oldRepos: ImmutableSet<String>? = null,
        val dialog: RepoDialog? = null,
        // YMR -->
        val isAdding: Boolean = false,
        // YMR <--
    ) : RepoScreenState() {

        val isEmpty: Boolean
            get() = repos.isEmpty()
    }
}
