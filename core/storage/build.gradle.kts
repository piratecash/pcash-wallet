import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    // Room's KSP cannot resolve the @CommonParcelize entities of core:wallet without it.
    id(libs.plugins.kotlin.parcelize.get().pluginId)
    id(libs.plugins.devtools.ksp.get().pluginId)
    kotlin("plugin.serialization")
}

// KMP source directories must be configured explicitly or Detekt reports NO-SOURCE.
detekt {
    source.setFrom("src/commonMain/kotlin")
}

kotlin {
    android {
        namespace = "cash.p.terminal.core.storage"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
            freeCompilerArgs.addAll(
                "-P",
                "plugin:org.jetbrains.kotlin.parcelize:additionalAnnotation=io.horizontalsystems.core.common.CommonParcelize",
            )
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
                api(project(":core:common"))
                api(project(":core:wallet"))
                implementation(project(":core:network"))

                implementation(libs.room.runtime)
                implementation(libs.gson)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.json)

                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
            }
        }
    }
}

// Room rejects blocking DAO functions unless android.content.Context is visible to the processor.
// The DAOs are shared with Android, so KSP gets a bare marker type; it is never packaged.
val roomAndroidMarker = java.sourceSets.create("roomAndroidMarker") {
    java.setSrcDirs(listOf("room-android-marker/java"))
}

dependencies {
    "desktopMainCompileOnly"(roomAndroidMarker.output)
    add("kspAndroid", libs.room.compiler)
    add("kspDesktop", libs.room.compiler)
}
