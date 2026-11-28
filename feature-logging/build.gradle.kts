import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id(libs.plugins.devtools.ksp.get().pluginId)
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/desktopMain/kotlin")
}

kotlin {
    android {
        namespace = "cash.p.terminal.feature.logging"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }

        withHostTest { }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:common"))
                api(libs.paging.common)
                implementation(project(":core:ui-compose"))
                implementation(project(":core:resources"))
                implementation(project(":core:wallet"))
                implementation(project(":feature-premium"))

                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.multiplatform.resources)
                implementation(libs.compose.multiplatform.ui.tooling.preview)
                implementation(libs.jetbrains.lifecycle.viewmodel)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.lifecycle.runtime.compose)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)

                implementation(libs.paging.compose)
                implementation(libs.coil.compose)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kermit)
            }
        }

        androidMain {
            dependencies {
                implementation(project(":core:core"))

                implementation(libs.koin.android)

                implementation(libs.room.runtime)
                implementation(libs.room.ktx)
                implementation(libs.room.paging)

                implementation(libs.accompanist.permissions)
                implementation(libs.kotlinx.coroutines.rx2)
                implementation(libs.timber)
            }
        }

        getByName("desktopMain") {
            dependencies {
                implementation(libs.room.runtime)
            }
        }

        getByName("desktopTest") {
            dependencies {
                implementation(libs.sqlite.bundled)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test.junit)
            }
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test.junit)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}
