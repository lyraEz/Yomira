package tachiyomi.domain.release.interactor

/**
 * Yomira version names: "1.12.0" for stable releases and "1.13.0-beta.2" for betas, with or
 * without a leading "v". A stable release is newer than every beta of the same number.
 */
data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val beta: Int? = null,
) : Comparable<AppVersion> {

    val isBeta: Boolean get() = beta != null

    override fun compareTo(other: AppVersion): Int = compareValuesBy(
        this,
        other,
        { it.major },
        { it.minor },
        { it.patch },
        { it.beta ?: Int.MAX_VALUE },
    )

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)(?:\.(\d+))?(?:-beta\.?(\d+))?""", RegexOption.IGNORE_CASE)

        fun parse(name: String): AppVersion? {
            val match = PATTERN.find(name.trim()) ?: return null
            val (major, minor, patch, beta) = match.destructured
            return AppVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toIntOrNull() ?: 0,
                beta = beta.toIntOrNull(),
            )
        }

        /** True when [candidate] is a later version than [current]; unreadable names never are. */
        fun isNewer(candidate: String, current: String): Boolean {
            val new = parse(candidate) ?: return false
            val old = parse(current) ?: return false
            return new > old
        }
    }
}
