import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id(libs.plugins.devtools.ksp.get().pluginId)
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/desktopMain/kotlin")
}

kotlin {
    android {
        namespace = "cash.p.terminal.premium"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int

        optimization {
            consumerKeepRules.file("consumer-rules.pro")
            consumerKeepRules.publish = true
        }

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

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:common"))
                implementation(project(":core:network"))
                implementation(project(":core:wallet"))

                implementation(libs.bitcoinj)
                implementation(libs.room.runtime)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kermit)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
            }
        }
        androidMain {
            dependencies {
                implementation(project(":core:core"))
                implementation(project(":feature-tangem"))
                implementation(libs.tangem.sdk)
                implementation(libs.android.core.ktx)
            }
        }
        commonTest {
            dependencies {
                implementation(project(":core:test-fixtures"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test.junit)
            }
        }
        getByName("desktopTest") {
            dependencies {
                implementation(libs.sqlite.bundled)
            }
        }
    }
}

dependencies {
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}
