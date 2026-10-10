package eu.kanade.tachiyomi.extension.model

sealed interface LoadResult {
    data class Success(val extension: Extension.Installed) : LoadResult
    data class Untrusted(val extension: Extension.Untrusted) : LoadResult

    // YMR --> carries why loading failed instead of dropping the extension silently
    data class Error(val failure: ExtensionLoadFailure? = null) : LoadResult
    // YMR <--
}
