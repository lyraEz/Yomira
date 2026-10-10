import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("com.diffplug.spotless")
}

val libs = the<LibrariesForLibs>()

val xmlFormatExclude = buildList(2) {
    add("**/build/**/*.xml")

    projectDir
        .resolve("src/commonMain/moko-resources")
        .takeIf { it.isDirectory }
        ?.let(::fileTree)
        ?.matching { exclude("/base/**") }
        ?.let(::add)
}
    .toTypedArray()

// YMR -->
// Inherited AniZen code does not pass spotless (12 modules fail on the upstream base), and
// reformatting it would bury every future upstream merge in conflicts. By default only files
// added since the recorded upstream base are checked; -Pyomira.spotless.all=true restores the
// upstream behaviour of checking everything.
val yomiraUpstreamBase: String? = rootProject.file(".yomira/upstream.json")
    .takeIf { it.exists() }
    ?.readText()
    ?.let { Regex("\"baseCommit\"\\s*:\\s*\"([0-9a-f]+)\"").find(it)?.groupValues?.get(1) }
val checkAllFiles = project.findProperty("yomira.spotless.all")?.toString()?.toBoolean() == true

fun gitLines(vararg args: String): List<String> = runCatching {
    providers.exec {
        workingDir = projectDir
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().lines().map { it.trim() }.filter { it.isNotEmpty() }
}.getOrDefault(emptyList())

val yomiraAddedFiles: List<String> by lazy {
    val base = yomiraUpstreamBase ?: return@lazy emptyList()
    (
        gitLines("diff", "--name-only", "--relative", "--diff-filter=A", base, "--", ".") +
            gitLines("ls-files", "--others", "--exclude-standard", "--", ".")
        )
        .distinct()
        .filter { file(it).exists() }
}

fun yomiraTargets(vararg extensions: String): List<String> =
    yomiraAddedFiles.filter { path -> extensions.any { path.endsWith(it) } && !path.contains("/build/") }
// YMR <--

spotless {
    kotlin {
        // YMR -->
        if (checkAllFiles || yomiraUpstreamBase == null) {
            target("**/*.kt", "**/*.kts")
        } else {
            target(yomiraTargets(".kt", ".kts"))
        }
        // YMR <--
        targetExclude("**/build/**/*.kt")
        ktlint(libs.ktlint.core.get().version)
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        // YMR -->
        if (checkAllFiles || yomiraUpstreamBase == null) {
            target("**/*.xml")
        } else {
            target(yomiraTargets(".xml"))
        }
        // YMR <--
        targetExclude(*xmlFormatExclude)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
