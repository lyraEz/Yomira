package eu.kanade.tachiyomi.extension.model

enum class InstallStep {
    Idle,
    Pending,
    Downloading,

    // YMR --> the system installer is showing its confirmation screen
    AwaitingConfirmation,
    // YMR <--
    Installing,
    Installed,
    Error,
    ;

    fun isCompleted(): Boolean {
        return this == Installed || this == Error || this == Idle
    }
}
