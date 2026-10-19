package yomira.extensions

/**
 * Repositories offered as one-press shortcuts in the "add repository" dialog, so they can be added
 * without typing. They are third-party repositories: adding one is still the user's choice,
 * and any other compatible repository can be typed, pasted or sent from a phone.
 */
object SuggestedRepos {

    data class Suggestion(val name: String, val indexUrl: String)

    val all = listOf(
        Suggestion(
            name = "Rede (lyraEz)",
            indexUrl = "https://raw.githubusercontent.com/lyraEz/Rede/repo/index.min.json",
        ),
        Suggestion(
            name = "Project Nox Anime (Awerkori)",
            indexUrl = "https://raw.githubusercontent.com/Awerkori/anime-extensoes/repo/index.min.json",
        ),
    )
}
