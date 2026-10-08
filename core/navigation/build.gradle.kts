plugins {
    id(libs.plugins.android.library.get().pluginId)

    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "cash.p.terminal.navigation"
    compileSdk = rootProject.ext.get("compile_sdk_version") as Int

    defaultConfig {
        minSdk = rootProject.ext.get("min_sdk_version") as Int
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        targetSdk = rootProject.ext.get("target_sdk_version") as Int
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(project(":core:resources"))

    implementation(libs.androidx.annotation)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.androidx.material3.android)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.navigation3.ui)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
