import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    id("io.gitlab.arturbosch.detekt")
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom("src/commonMain/kotlin", "src/commonTest/kotlin", "src/desktopMain/kotlin")
}

kotlin {
    jvmToolchain(21)

    android {
        namespace = "cash.p.terminal.shared"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(project(":core:network"))
                api(project(":core:resources"))
                api(project(":core:common"))
                implementation(project(":core:ui-compose"))
                implementation(project(":core:navigation"))
                implementation(project(":core:market"))
                implementation(project(":core:wallet"))
                implementation(project(":core:storage"))
                implementation(project(":feature-market"))
                implementation(project(":feature-wallet"))
                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.multiplatform.adaptive.navigation.suite)
                implementation(libs.jetbrains.navigation3.ui)
                implementation(libs.jetbrains.lifecycle.runtime.compose)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kermit)
            }
        }
        val desktopMain by getting {
            dependencies {
                implementation(libs.compose.multiplatform.ui.tooling.preview)
                implementation(project(":core:vault"))
                implementation(project(":feature-premium"))
                implementation(libs.hd.wallet.kit)
                implementation(libs.monero.kit)
                implementation(libs.bitcoin.core)
                implementation(libs.ethereum.kit)
                implementation(libs.solana.kit)
                implementation(libs.stellar.kit)
                implementation(libs.thorchain.kit)
                implementation(libs.ton.kit)
                implementation(libs.tron.kit)
                implementation(libs.gson)
                implementation(libs.rxjava)
                implementation(libs.datastore.preferences.core)
                implementation(libs.room.runtime)
                implementation(libs.bitcoin.kit.sqlcipher.driver)
                implementation(libs.openpnp.capture.java)
                implementation(libs.zxing.core)
            }
        }
        commonTest {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(project(":core:test-fixtures"))
                implementation(kotlin("test"))
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.koin.test.junit4)
                implementation(libs.ktor.client.core)
                implementation(project(":components:chartview"))
                implementation(libs.ethereum.kit)
                implementation(libs.beam.sdk)
            }
        }
    }
}

dependencies {
    add("desktopTestImplementation", compose.desktop.uiTestJUnit4)
    add("desktopTestRuntimeOnly", compose.desktop.currentOs)
}
