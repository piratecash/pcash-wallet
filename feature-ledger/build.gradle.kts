import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization")
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom("src/commonMain/kotlin", "src/jvmCommonMain/kotlin", "src/androidMain/kotlin")
}

kotlin {
    android {
        namespace = "cash.p.terminal.ledger"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int
        androidResources.enable = true

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // jvmCommon: blocking bridges to the synchronous kit signer APIs, shared by both JVM targets.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                api(libs.ledger.kit)
                implementation(project(":core:common"))
                implementation(project(":core:market"))
                implementation(project(":core:wallet"))
                implementation(project(":core:navigation"))
                implementation(project(":core:ui-compose"))
                implementation(project(":core:resources"))
                implementation(libs.hd.wallet.kit)
                implementation(libs.bitcoin.core)
                implementation(libs.ethereum.kit)
                implementation(libs.solanakt)
                implementation(libs.tron.kit)
                implementation(libs.stellar.kit)
                implementation(libs.ktor.client.core)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.datastore.preferences.core)

                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.jetbrains.lifecycle.viewmodel)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kermit)
            }
        }

        getByName("jvmCommonMain") {
            dependencies {
                implementation(libs.ton.kit)
                implementation(libs.ton.kotlin.contract)
                implementation(libs.thorchain.kit)
                implementation(libs.zcash.sdk.kmp)
            }
        }

        androidMain {
            dependencies {
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.androidx.ui.tooling.preview)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":core:test-fixtures"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test.junit)
                implementation(libs.ktor.client.mock)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.kotlinx.serialization)
            }
        }
        getByName("desktopTest") {
            dependencies {
                runtimeOnly(libs.secp256k1.jni.jvm)
                implementation(libs.solana.kit)
                // ton-kit's swap requests are org.json objects, which Android ships in the platform.
                implementation(libs.org.json)
            }
        }
    }
}
