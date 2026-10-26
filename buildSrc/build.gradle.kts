plugins {
    `kotlin-dsl`
}

dependencies {
    // R8 that understands Kotlin 2.3 metadata. AGP 8.11 bundles R8 8.11 (made for
    // Kotlin 2.2), which cannot read the metadata of classes built with Kotlin 2.3.
    implementation("com.android.tools:r8:8.13.25")
    implementation(androidx.gradle)
    implementation(kotlinx.gradle)
    implementation(kotlinx.compose.compiler.gradle)
    implementation(libs.spotless.gradle)
    implementation(gradleApi())

    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(androidx.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(compose.javaClass.superclass.protectionDomain.codeSource.location))
    implementation(files(kotlinx.javaClass.superclass.protectionDomain.codeSource.location))
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
}
