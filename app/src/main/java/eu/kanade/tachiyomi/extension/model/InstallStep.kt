package eu.kanade.tachiyomi.extension.model

enum class InstallStep {
    Idle,
    Pending,
    Downloading,

    // the system installer is showing its confirmation screen
    AwaitingConfirmation,
    Installing,
    Installed,
    Error,
    ;

    fun isCompleted(): Boolean {
        return this == Installed || this == Error || this == Idle
    }
}
