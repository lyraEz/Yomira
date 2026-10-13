import mihon.buildlogic.Config
import mihon.buildlogic.getBuildTime
import mihon.buildlogic.getCommitCount
import mihon.buildlogic.getGitSha
import java.util.Properties

plugins {
    id("mihon.android.application")
    id("mihon.android.application.compose")
    id("com.github.zellius.shortcut-helper")
    kotlin("plugin.parcelize")
    kotlin("plugin.serialization")
    alias(libs.plugins.aboutLibraries)
    id("com.github.ben-manes.versions")
}

if (Config.includeTelemetry && file("google-services.json").exists()) {
    pluginManager.apply {
        apply(libs.plugins.google.services.get().pluginId)
        apply(libs.plugins.firebase.crashlytics.get().pluginId)
    }
}

shortcutHelper.setFilePath("./shortcuts.xml")

// YMR --> ABIs built as split APKs (and kept in the release universal APK). Override with -PabiList=...
val yomiraAbis: List<String> =
    (project.findProperty("abiList") as? String)?.split(",") ?: listOf("armeabi-v7a", "arm64-v8a")
// YMR <--

android {
    namespace = "eu.kanade.tachiyomi"

    // YMR -->
    // Yomira release signing. The keystore and its passwords never live in the repository:
    // they come from `signing.properties` (git-ignored) or from environment variables in CI.
    // Without them the release APK is produced unsigned instead of failing the build.
    val yomiraSigning = Properties().apply {
        val propFile = rootProject.file("signing.properties")
        if (propFile.exists()) {
            propFile.inputStream().use { load(it) }
        }
    }
    fun signingValue(prop: String, env: String): String? =
        yomiraSigning.getProperty(prop)?.takeIf { it.isNotBlank() }
            ?: System.getenv(env)?.takeIf { it.isNotBlank() }

    val yomiraStoreFile = signingValue("storeFile", "YOMIRA_KEYSTORE_FILE")?.let { rootProject.file(it) }
    val hasYomiraSigning = yomiraStoreFile?.exists() == true &&
        signingValue("storePassword", "YOMIRA_KEYSTORE_PASSWORD") != null &&
        signingValue("keyAlias", "YOMIRA_KEY_ALIAS") != null

    signingConfigs {
        create("release") {
            if (hasYomiraSigning) {
                storeFile = yomiraStoreFile
                storePassword = signingValue("storePassword", "YOMIRA_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "YOMIRA_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "YOMIRA_KEY_PASSWORD")
                    ?: signingValue("storePassword", "YOMIRA_KEYSTORE_PASSWORD")
            }

            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }
    // YMR <--

    compileSdk = 36

    defaultConfig {
        // YMR -->
        applicationId = "com.lyraez.yomira"

        // versionCode must grow with every published APK and is shared by all ABI splits.
        // Scheme: major * 10000 + minor * 100 + patch (1.0.0 -> 10000). See docs/yomira/RELEASES.md.
        versionCode = 10100
        versionName = "1.1.0"

        manifestPlaceholders["author"] = "@lyraEz"
        // YMR <--

        buildConfigField("String", "COMMIT_COUNT", "\"${getCommitCount()}\"")
        buildConfigField("String", "COMMIT_SHA", "\"${getGitSha()}\"")
        buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        buildConfigField("boolean", "TELEMETRY_INCLUDED", "${Config.includeTelemetry}")

        minSdk = 26
        targetSdk = 36

        buildConfigField("boolean", "UPDATER_ENABLED", "${Config.enableUpdater}")

        // YMR --> Services tied to someone else's credentials stay off unless configured.
        val discordAppId = (project.findProperty("yomira.discordAppId") as? String).orEmpty()
        buildConfigField("String", "DISCORD_RPC_APP_ID", "\"$discordAppId\"")
        buildConfigField("boolean", "DISCORD_RPC_ENABLED", "${discordAppId.isNotBlank()}")
        val hasGoogleDriveClient = file("src/main/assets/client_secrets.json").exists()
        buildConfigField("boolean", "GOOGLE_DRIVE_SYNC_ENABLED", "$hasGoogleDriveClient")
        // YMR <--

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        val debug by getting {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-${getCommitCount()}"
            isPseudoLocalesEnabled = true
        }
        val release by getting {
            isMinifyEnabled = Config.enableCodeShrink
            isShrinkResources = Config.enableCodeShrink

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-android-optimize.txt",
                "proguard-rules.pro",
            )

            // YMR -->
            signingConfig = if (hasYomiraSigning) signingConfigs.getByName("release") else null
            // YMR <--

            buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = true)}\"")
        }

        val commonMatchingFallbacks = listOf(release.name)

        maybeCreate("releaseTest").apply {
            initWith(release)

            applicationIdSuffix = ".rt"

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }
        maybeCreate("foss").apply {
            initWith(release)

            applicationIdSuffix = ".foss"

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }
        getByName("preview").apply {
            initWith(release)

            applicationIdSuffix = ".beta"

            versionNameSuffix = debug.versionNameSuffix
            signingConfig = release.signingConfig

            matchingFallbacks.addAll(commonMatchingFallbacks)

            buildConfigField("String", "BUILD_TIME", "\"${getBuildTime(useLastCommitTime = false)}\"")
        }
        getByName("benchmark").apply {
            initWith(release)

            isDebuggable = false
            isProfileable = true
            versionNameSuffix = "${debug.versionNameSuffix}-benchmark"
            applicationIdSuffix = ".benchmark"

            signingConfig = debug.signingConfig

            matchingFallbacks.addAll(commonMatchingFallbacks)
        }
    }

    sourceSets {
        getByName("preview").res.srcDirs("src/beta/res")
        getByName("benchmark").res.srcDirs("src/debug/res")
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = true
            reset()
            // YMR --> Fire TV / TV boxes: 32-bit ARM first; x86 only on request (emulators).
            include(*yomiraAbis.toTypedArray())
            // YMR <--
        }
    }

    packaging {
        jniLibs {
            keepDebugSymbols += listOf(
                "libandroidx.graphics.path",
                "libarchive-jni",
                "libconscrypt_jni",
                "libimagedecoder",
                "libquickjs",
                "libsqlite3x",
                "libavcodec",
                "libavdevice",
                "libavfilter",
                "libavformat",
                "libavutil",
                "libc++_shared",
                "libffmpegkit",
                "libffmpegkit_abidetect",
                "libgojni",
                "libmpv",
                "libplayer",
                "libpostproc",
                "libsqlcipher",
                "libswresample",
                "libswscale",
                "libxml2",
            )
                .map { "**/$it.so" }
        }
        resources {
            excludes += setOf(
                "kotlin-tooling-metadata.json",
                "LICENSE.txt",
                "META-INF/**/*.properties",
                "META-INF/**/LICENSE.txt",
                "META-INF/*.properties",
                "META-INF/*.version",
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/NOTICE",
                "META-INF/README.md",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
            )
        }
    }

    dependenciesInfo {
        includeInApk = Config.includeDependencyInfo
        includeInBundle = Config.includeDependencyInfo
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true

        // Disable some unused things
        aidl = true
        renderScript = false
        shaders = false
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    applicationVariants.all {
        if (buildType.name == "preview") {
            outputs.all {
                val output = this as? com.android.build.gradle.api.ApkVariantOutput
                output?.versionNameOverride = "r-${getCommitCount()}"
            }
        }
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-P=plugin:androidx.compose.compiler.plugins.kotlin:strongSkipping=true",
            "-opt-in=androidx.compose.animation.ExperimentalAnimationApi",
            "-opt-in=androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi",
            "-opt-in=coil3.annotation.ExperimentalCoilApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
            "-opt-in=kotlinx.coroutines.InternalCoroutinesApi",
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

dependencies {
    implementation(projects.i18n)
    // KMK -->
    implementation(projects.i18nKmk)
    implementation(projects.i18nAnk)
    // KMK <-
    // SY -->
    implementation(projects.i18nSy)
    // SY <-
    // YMR -->
    implementation(projects.i18nYmr)
    // YMR <--
    implementation(projects.core.archive)
    implementation(projects.core.common)
    implementation(projects.coreMetadata)
    implementation(projects.sourceApi)
    implementation(projects.sourceLocal)
    implementation(projects.data)
    implementation(projects.domain)
    implementation(projects.presentationCore)
    implementation(projects.presentationWidget)
    implementation(projects.telemetry)

    // Compose
    implementation(compose.activity)
    implementation(compose.foundation)
    implementation(compose.material3.core)
    implementation(compose.material.icons)
    implementation(compose.animation)
    implementation(compose.animation.graphics)
    implementation(compose.runtime)
    debugImplementation(compose.ui.tooling)
    implementation(compose.ui.tooling.preview)
    implementation(compose.ui.util)

    implementation(androidx.interpolator)

    implementation(androidx.paging.runtime)
    implementation(androidx.paging.compose)

    implementation(libs.bundles.sqlite)
    // SY -->
    implementation(sylibs.sqlcipher)
    // SY <-

    implementation(kotlinx.reflect)
    implementation(kotlinx.immutables)

    implementation(platform(kotlinx.coroutines.bom))
    implementation(kotlinx.bundles.coroutines)

    // AndroidX libraries
    implementation(androidx.annotation)
    implementation(androidx.appcompat)
    implementation(androidx.biometricktx)
    implementation(androidx.constraintlayout)
    implementation(aniyomilibs.compose.constraintlayout)
    implementation(androidx.corektx)
    implementation(androidx.splashscreen)
    implementation(androidx.recyclerview)
    implementation(androidx.viewpager)
    implementation(androidx.profileinstaller)
    implementation(aniyomilibs.mediasession)

    implementation(androidx.bundles.lifecycle)

    // Job scheduling
    implementation(androidx.workmanager)

    // RxJava
    implementation(libs.rxjava)

    // Networking
    implementation(libs.bundles.okhttp)
    implementation(libs.okio)
    implementation(libs.conscrypt.android) // TLS 1.3 support for Android < 10

    // Data serialization (JSON, protobuf, xml)
    implementation(kotlinx.bundles.serialization)

    // HTML parser
    implementation(libs.jsoup)

    // Disk
    implementation(libs.disklrucache)
    implementation(libs.unifile)

    // Preferences
    implementation(libs.preferencektx)

    // Dependency injection
    implementation(libs.injekt)

    // Image loading
    implementation(platform(libs.coil.bom))
    implementation(libs.bundles.coil)
    implementation(libs.subsamplingscaleimageview) {
        exclude(module = "image-decoder")
    }
    implementation(libs.image.decoder)

    // UI libraries
    implementation(libs.material)
    implementation(libs.flexible.adapter.core)
    implementation(libs.photoview)
    implementation(libs.directionalviewpager) {
        exclude(group = "androidx.viewpager", module = "viewpager")
    }
    implementation(libs.insetter)
    implementation(libs.richeditor.compose)
    implementation(libs.aboutLibraries.compose)
    implementation(libs.bundles.voyager)
    implementation(libs.compose.materialmotion)
    implementation(libs.swipe)
    implementation(libs.compose.webview)
    implementation(libs.compose.grid)
    implementation(libs.reorderable)
    implementation(libs.bundles.markdown)

    // KMK -->
    implementation(libs.palette.ktx)
    implementation(libs.materialKolor)
    implementation(libs.haze)
    implementation(libs.backdrop)
    implementation(libs.backdrop.shapes)
    implementation(compose.colorpicker)
    implementation(projects.flagkit)
    // KMK <-

    // Logging
    implementation(libs.timber)
    implementation(libs.logcat)

    // Shizuku
    implementation(libs.bundles.shizuku)

    // Tests
    testImplementation(libs.bundles.test)
    testRuntimeOnly(libs.junit.platform.launcher)

    // For detecting memory leaks; see https://square.github.io/leakcanary/
    // debugImplementation(libs.leakcanary.android)
    implementation(libs.leakcanary.plumber)

    testImplementation(kotlinx.coroutines.test)

    // SY -->
    // Text distance (EH)
    implementation(sylibs.similarity)

    // Better logging (EH)
    implementation(sylibs.xlog)

    // RatingBar (SY)
    implementation(sylibs.ratingbar)
    implementation(sylibs.composeRatingbar)

    // Google drive
    implementation(sylibs.google.api.services.drive)

    // ZXing Android Embedded
    implementation(sylibs.zxing.android.embedded)

    // mpv-android
    implementation(aniyomilibs.aniyomi.mpv)
    // FFmpeg-kit
    implementation(aniyomilibs.ffmpeg.kit)
    implementation(aniyomilibs.arthenica.smartexceptions)
    // seeker seek bar
    implementation(aniyomilibs.seeker)
    // true type parser
    implementation(aniyomilibs.truetypeparser)
    // torrserver
    implementation(aniyomilibs.torrentserver)
    // Cast
    implementation(aniyomilibs.bundles.cast)
    // nanohttpd server
    implementation(aniyomilibs.nanohttpd)
}

androidComponents {
    onVariants(selector().withFlavor("default" to "standard")) {
        // Only excluding in standard flavor because this breaks
        // Layout Inspector's Compose tree
        it.packaging.resources.excludes.add("META-INF/*.version")
    }
    // YMR --> Keep x86 libraries out of the release universal APK unless they were asked for:
    // Fire TV and TV boxes are ARM, and the x86 mpv libraries add about 100 MB.
    onVariants(selector().withBuildType("release")) { variant ->
        listOf("x86", "x86_64").filterNot { it in yomiraAbis }.forEach { abi ->
            variant.packaging.jniLibs.excludes.add("lib/$abi/**")
        }
    }
    // YMR <--
}


buildscript {
    dependencies {
        classpath(kotlinx.gradle)
    }
}
