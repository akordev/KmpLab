plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.akordev.kmplab.sdk"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// AGP 9 ships Kotlin support built in, so there is no kotlin-android plugin to
// apply. The `kotlin` extension it registers is still where compiler options live.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
    // This module is a published SDK: every public declaration must say so
    // explicitly and carry an explicit return type.
    explicitApi()
}

dependencies {
    // implementation, not api: the Kotlin Multiplatform layer is an internal
    // detail. Consumers of this SDK never see a `dev.akordev.kmplab.network` type.
    implementation(project(":shared:network"))

    // Exposed, because the public surface hands back a StateFlow.
    api(libs.kotlinx.coroutines.core)

    // kotlin-test on its own carries no framework binding; the junit variant is
    // what maps `kotlin.test.Test` onto the runner AGP actually launches.
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
