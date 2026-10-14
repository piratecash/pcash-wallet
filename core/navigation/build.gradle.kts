import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/main/res")
    }
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/desktopMain/kotlin")
}

kotlin {
    android {
        namespace = "cash.p.terminal.navigation"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int
        androidResources.enable = true

        optimization {
            keepRules.file("proguard-rules.pro")
            consumerKeepRules.file("consumer-rules.pro")
            consumerKeepRules.publish = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-P",
                "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=io.horizontalsystems.core.common.CommonParcelize",
            )
        }

        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:common"))
                implementation(project(":core:resources"))

                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.jetbrains.lifecycle.viewmodel)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.lifecycle.runtime.compose)
                implementation(libs.androidx.savedstate.compose)
                api(libs.androidx.navigation3.runtime)
                api(libs.jetbrains.navigation3.ui)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.junit)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.robolectric)
                implementation(libs.androidx.espresso.core)
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.compose.ui.test.manifest)
            }
        }

        getByName("desktopTest") {
            dependencies {
                implementation(libs.kotlin.test.junit)
            }
        }
    }
}

dependencies {
    add("desktopTestImplementation", compose.desktop.uiTestJUnit4)
    add("desktopTestRuntimeOnly", compose.desktop.currentOs)
}
