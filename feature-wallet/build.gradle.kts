import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    kotlin("plugin.serialization")
}

compose.resources {
    packageOfResClass = "cash.p.terminal.feature.wallet.resources"
}

// The changelog stays at the repository root: the app also fetches it from GitHub by release tag.
val copyChangelogs by tasks.registering(Copy::class) {
    from(rootProject.layout.projectDirectory) { include("changelog_*.md") }
    into(layout.projectDirectory.dir("src/commonMain/composeResources/files"))
}
tasks.matching { it.name.endsWith("ForCommonMain") }.configureEach { dependsOn(copyChangelogs) }

// Kit versions printed in Blockchain Status reports, taken from the catalog the build resolves.
val generateKitVersions by tasks.registering {
    val versions = mapOf(
        "BITCOIN_KIT" to libs.versions.piratecash.bitcoin.get(),
        "ETHEREUM_KIT" to libs.versions.piratecash.ethereum.get(),
        "SOLANA_KIT" to libs.versions.piratecash.solana.get(),
        "TON_KIT" to libs.versions.piratecash.ton.get(),
        "TRON_KIT" to libs.versions.piratecash.tron.get(),
        "MONERO_KIT" to libs.versions.monero.kit.android.get(),
        "STELLAR_KIT" to libs.versions.stellar.kit.get(),
        "THORCHAIN_KIT" to libs.versions.thorchain.kit.get(),
        "ZCASH_SDK" to libs.versions.zcashSdkKmp.get(),
        "BEAM_SDK" to libs.versions.beam.sdk.get(),
    )
    val outputDir = layout.buildDirectory.dir("generated/kitVersions/kotlin")
    inputs.property("versions", versions)
    outputs.dir(outputDir)
    doLast {
        val file = outputDir.get().file("cash/p/terminal/modules/blockchainstatus/KitVersions.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            buildString {
                appendLine("package cash.p.terminal.modules.blockchainstatus")
                appendLine()
                appendLine("internal object KitVersions {")
                versions.forEach { (name, version) -> appendLine("    const val $name = \"$version\"") }
                appendLine("}")
            }
        )
    }
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/desktopMain/kotlin")
}

kotlin {
    android {
        namespace = "cash.p.terminal.feature.wallet"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int
        androidResources.enable = true

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

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateKitVersions)
            dependencies {
                api(project(":core:common"))
                implementation(project(":core:market"))
                implementation(project(":core:wallet"))
                implementation(project(":core:storage"))
                implementation(project(":core:navigation"))
                implementation(project(":core:ui-compose"))
                implementation(project(":core:resources"))
                implementation(project(":core:network"))
                implementation(project(":core:vault"))
                implementation(project(":feature-market"))
                implementation(project(":feature-premium"))
                // AdapterFactory takes StackingManager in its public constructor.
                api(project(":feature-stacking"))
                implementation(project(":components:chartview"))
                implementation(project(":core:qr-multipart"))

                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.material)
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.multiplatform.resources)
                implementation(libs.qrose)
                implementation(libs.richtext.markdown)
                implementation(libs.richtext.ui.material3)
                implementation(libs.richtext.commonmark)
                implementation(libs.zxing.core)
                implementation(libs.jetbrains.lifecycle.viewmodel)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.lifecycle.runtime.compose)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.rx2)
                implementation(libs.coil.compose)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kermit)
                implementation(libs.androidx.annotation)
                implementation(libs.gson)
                implementation(libs.retrofit)
                implementation(libs.retrofit.gson)
                implementation(libs.retrofit.scalars)
                implementation(libs.retrofit.rxjava2)
                implementation(libs.okhttp.logging.interceptor)
                implementation(libs.rxjava)
                implementation(libs.ktor.client.core)
                implementation(libs.bouncycastle.bcprov)
                implementation(libs.bitcoinj)
                implementation(libs.web3j.crypto)
                implementation(libs.resolution.java)

                implementation(libs.bitcoin.core)
                implementation(libs.bitcoin.kit)
                implementation(libs.bitcoin.cash.kit)
                implementation(libs.bitcoin.ecash.kit)
                implementation(libs.bitcoin.litecoin.kit)
                implementation(libs.bitcoin.dash.kit)
                implementation(libs.bitcoin.dogecoin.kit)
                implementation(libs.bitcoin.cosanta.kit)
                implementation(libs.bitcoin.piratecash.kit)
                implementation(libs.hd.wallet.kit)
                implementation(libs.ethereum.kit)
                implementation(libs.ethereum.kit.erc20)
                implementation(libs.ethereum.kit.nft)
                implementation(libs.ethereum.kit.merkleio)
                implementation(libs.ethereum.kit.uniswap)
                implementation(libs.ethereum.kit.oneinch)
                implementation(libs.solana.kit)
                implementation(libs.stellar.kit)
                implementation(libs.thorchain.kit)
                implementation(libs.monero.kit)
                implementation(libs.tron.kit)
                implementation(libs.ton.kit)
                implementation(libs.ton.kotlin.contract)
                implementation(libs.zcash.sdk.kmp)
                implementation(libs.beam.sdk)
            }
        }

        androidMain {
            dependencies {
                implementation(project(":core:core"))
                implementation(project(":feature-tangem"))
                implementation(project(":feature-mini-app"))
                implementation(project(":feature-trezor"))
                implementation(libs.android.core.ktx)
                implementation(libs.appcompat)
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.androidx.ui.tooling.preview)
            }
        }

        getByName("desktopMain") {
            dependencies {
                // Android ships org.json in the framework; bundling it there would shadow the platform classes.
                implementation(libs.org.json)
            }
        }

        commonTest {
            dependencies {
                implementation(project(":core:test-fixtures"))
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test.junit)
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.test.junit4)
            }
        }
        getByName("desktopTest") {
            dependencies {
                runtimeOnly(libs.secp256k1.jni.jvm)
                runtimeOnly(compose.desktop.currentOs)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                runtimeOnly(libs.secp256k1.jni.jvm)
                implementation(libs.robolectric)
                implementation(libs.androidx.espresso.core)
                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.ui.test.junit4)
                implementation(libs.compose.ui.test.manifest)
            }
        }
    }
}
