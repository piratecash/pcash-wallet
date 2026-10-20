plugins {
    id(libs.plugins.android.library.get().pluginId)

    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.compose.compiler)
}

android {
    val minSdkVersion: Int = rootProject.ext.get("min_sdk_version") as Int
    val targetSdkVersion: Int = rootProject.ext.get("compile_sdk_version") as Int

    compileSdk = targetSdkVersion
    namespace = "cash.p.terminal.core"

    defaultConfig {
        minSdk = minSdkVersion

        consumerProguardFiles("consumer-rules.pro")
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api(project(":core:common"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material)
    debugImplementation(libs.androidx.ui.tooling)
    implementation(libs.androidx.ui.tooling.preview)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)

    implementation(libs.androidx.material3.android)

    // Paging (for ILoginRecordRepository interface)
    api(libs.paging.runtime)

    implementation(libs.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.android.core.ktx)

    implementation(libs.androidx.fragment.ktx)

    implementation(libs.rxjava)
    implementation(libs.androidx.biometric)
    implementation(libs.material)

    implementation(project(":core:strings"))
    implementation(project(":core:ui-compose"))
    implementation(project(":core:navigation"))
    implementation(project(":core:storage"))

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
}