import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.tasks.JavaExec
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val isMacOs = System.getProperty("os.name").startsWith("Mac")
val applicationName = "P.CASH"
val macIconFile = project.file("icons/p-cash.icns")

plugins {
    alias(libs.plugins.jetbrains.kotlin.jvm)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(project(":core:ui-compose"))
    implementation(project(":core:market"))
    implementation(project(":core:vault"))
    implementation(project(":core:common"))
    implementation(project(":feature-market"))
    implementation(libs.ethereum.kit)
    implementation(libs.bitcoin.kit.sqlcipher.driver)
    implementation(libs.bitcoin.dashlib.native)
    implementation(libs.kermit)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    // Same okhttp as Android: stellar-sdk and ton-kit use okhttp-sse 4.x internals that okhttp 5 removed.
    implementation(enforcedPlatform(libs.okhttp.bom))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(compose.desktop.currentOs)
}

// mwebd ships full-protobuf gencode; a second, lite copy of com.google.protobuf fails its runtime version check.
configurations.configureEach {
    resolutionStrategy.eachDependency {
        if (requested.group == "com.google.protobuf" && requested.name == "protobuf-javalite") {
            useTarget("com.google.protobuf:protobuf-java:${requested.version}")
        }
    }
}

fun runtimeJar(group: String, module: String) = configurations.runtimeClasspath.get().incoming.artifactView {
    componentFilter { it is ModuleComponentIdentifier && it.group == group && it.module == module }
}.files

val sqlCipherDriver = libs.bitcoin.kit.sqlcipher.driver.get()
val sqlCipherDriverJar = runtimeJar(sqlCipherDriver.group, sqlCipherDriver.name)
val beamSdk = libs.beam.sdk.get()
val beamSdkDesktopJar = runtimeJar(beamSdk.group, "${beamSdk.name}-desktop")

// Compose picks the app-resources subdirectory by this name; the BEAM jar names its natives by Rust target triple.
val beamNativeResourceDirs = mapOf(
    "aarch64-apple-darwin" to "macos-arm64",
    "x86_64-pc-windows-msvc" to "windows-x64",
    "x86_64-unknown-linux-gnu" to "linux-x64",
)

// The packager re-signs dylibs inside jars, which breaks the driver's own checksum: ship them as app resources.
val stageNativeResources = tasks.register<Sync>("stageNativeResources") {
    from({ sqlCipherDriverJar.map(::zipTree) }) {
        include("natives/**")
        exclude("**/*.sha256")
        eachFile { path = path.removePrefix("natives/") }
        includeEmptyDirs = false
    }
    from({ beamSdkDesktopJar.map(::zipTree) }) {
        include("native/**")
        eachFile {
            val triple = relativePath.segments[1]
            path = "${beamNativeResourceDirs.getValue(triple)}/$name"
        }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("native-resources"))
}

val verifyNativeResources = tasks.register("verifyNativeResources") {
    val prepareAppResources = tasks.named<Sync>("prepareAppResources")
    dependsOn(prepareAppResources)
    doLast {
        listOf("sqlcipher_jni", "beam_sdk_kmp").forEach { library ->
            val native = File(prepareAppResources.get().destinationDir, System.mapLibraryName(library))
            check(native.isFile) { "Native library is missing from the app resources: $native" }
        }
    }
}

tasks.withType<AbstractJPackageTask>().configureEach {
    dependsOn(verifyNativeResources)
}

val androidBuildGradle = rootProject.file("app/build.gradle")
val desktopBuildInfoDir = layout.buildDirectory.dir("generated/desktopBuildInfo")

// The desktop shows and reports the version of the Android app it is built from.
val generateDesktopBuildInfo = tasks.register("generateDesktopBuildInfo") {
    inputs.file(androidBuildGradle)
    outputs.dir(desktopBuildInfoDir)
    doLast {
        val versionName = Regex("""versionName\s+"([^"]+)"""").find(androidBuildGradle.readText())?.groupValues?.get(1)
            ?: throw GradleException("versionName is not found in ${androidBuildGradle.path}")
        val packageDir = desktopBuildInfoDir.get().dir("cash/p/terminal/desktop").asFile.apply { mkdirs() }
        File(packageDir, "DesktopBuildInfo.kt").writeText(
            "package cash.p.terminal.desktop\n\ninternal const val APP_VERSION_NAME = \"$versionName\"\n"
        )
    }
}

kotlin.sourceSets.named("main") {
    kotlin.srcDir(generateDesktopBuildInfo.map { desktopBuildInfoDir })
}

compose.desktop {
    application {
        mainClass = "cash.p.terminal.desktop.MainKt"
        // The storage keys stay in memory while the app is locked: no same-user attach and heap dump.
        jvmArgs += "-XX:+DisableAttachMechanism"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            // jlink keeps only the modules listed here; DataStore's protobuf needs sun.misc.Unsafe
            // (jdk.unsupported), which the full JDK of `:desktopApp:run` masks. Source: suggestRuntimeModules.
            modules("java.compiler", "java.instrument", "java.management", "java.naming", "java.sql", "jdk.unsupported")
            packageName = applicationName
            // CI passes 1.0.<run number> so every MSI gets a new ProductCode and upgrades the previous build;
            // macOS rejects MAJOR == 0, so the Android 0.x versionName cannot be reused here.
            packageVersion = providers.gradleProperty("desktopPackageVersion").getOrElse("1.0.0")
            appResourcesRootDir.set(layout.dir(stageNativeResources.map { it.destinationDir }))
            macOS {
                iconFile.set(macIconFile)
            }
            windows {
                iconFile.set(project.file("icons/p-cash.ico"))
            }
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (isMacOs && name == "hotRun") {
        jvmArgs("-Xdock:name=$applicationName")
        jvmArgs("-Xdock:icon=${macIconFile.absolutePath}")
    }
}

afterEvaluate {
    tasks.named<JavaExec>("run") {
        dependsOn(verifyNativeResources)
        if (isMacOs) {
            jvmArgs("-Xdock:name=$applicationName")
        }
    }
}
