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

// Older code does not pass spotless (12 modules fail on the recorded base), and reformatting it
// all at once would make the history hard to read. By default only files added since the base in
// .yomira/lint-base.json are checked; -Pyomira.spotless.all=true checks everything.
val yomiraUpstreamBase: String? = rootProject.file(".yomira/lint-base.json")
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

spotless {
    kotlin {
        if (checkAllFiles || yomiraUpstreamBase == null) {
            target("**/*.kt", "**/*.kts")
        } else {
            target(yomiraTargets(".kt", ".kts"))
        }
        targetExclude("**/build/**/*.kt")
        ktlint(libs.ktlint.core.get().version)
        trimTrailingWhitespace()
        endWithNewline()
    }
    format("xml") {
        if (checkAllFiles || yomiraUpstreamBase == null) {
            target("**/*.xml")
        } else {
            target(yomiraTargets(".xml"))
        }
        targetExclude(*xmlFormatExclude)
        trimTrailingWhitespace()
        endWithNewline()
    }
}
