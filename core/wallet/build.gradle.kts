import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id(libs.plugins.kotlin.parcelize.get().pluginId)
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom(
        "src/commonMain/kotlin",
        "src/androidMain/kotlin",
    )
}

kotlin {
    android {
        namespace = "cash.p.terminal.wallet"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int

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
            dependencies {
                api(project(":core:market"))
                implementation(project(":core:common"))
                implementation(project(":core:network"))
                implementation(project(":core:navigation"))

                implementation(libs.rxjava)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.coroutines.rx2)

                implementation(libs.ton.kit)
                implementation(libs.hd.wallet.kit)
                implementation(libs.ethereum.kit)
                implementation(libs.monero.kit)
                implementation(libs.stellar.kit)

                implementation(libs.retrofit)
                implementation(libs.retrofit.gson)
                implementation(libs.okhttp.logging.interceptor)
                implementation(libs.retrofit.rxjava2)
                implementation(libs.retrofit.scalars)
                implementation(libs.gson)

                implementation(libs.room.runtime)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.kermit)
            }
        }
        androidMain {
            dependencies {
                implementation(libs.koin.android)
            }
        }
        commonTest {
            dependencies {
                implementation(libs.junit)
                implementation(libs.mockk)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.kotlin.test.junit)
            }
        }
        getByName("desktopTest") {
            dependencies {
                runtimeOnly(libs.secp256k1.jni.jvm)
            }
        }
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.robolectric)
                implementation(libs.androidx.test.core)
                implementation(libs.koin.android)
                runtimeOnly(libs.secp256k1.jni.jvm)
            }
        }
    }
}
