import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/desktopMain/kotlin")
}

kotlin {
    android {
        namespace = "io.horizontalsystems.core.common"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-P",
                "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=io.horizontalsystems.core.common.CommonParcelize",
            )
        }

        withHostTest { }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:resources"))
                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.kotlinx.coroutines.core)
                api(libs.jetbrains.lifecycle.viewmodel)
                api(libs.rxjava)
                implementation(libs.gson)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.android.core.ktx)
            }
        }
        getByName("desktopMain") {
            dependencies {
                implementation(libs.datastore.preferences.core)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.junit)
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
    add("desktopTestRuntimeOnly", compose.desktop.currentOs)
}
