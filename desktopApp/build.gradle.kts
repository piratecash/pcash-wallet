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
val openpnpCapture = libs.openpnp.capture.java.get()
val openpnpCaptureJar = runtimeJar(openpnpCapture.group, openpnpCapture.name)
val jnaJar = runtimeJar("net.java.dev.jna", "jna")

// Compose picks the app-resources subdirectory by this name; the BEAM jar names its natives by Rust target triple.
val beamNativeResourceDirs = mapOf(
    "aarch64-apple-darwin" to "macos-arm64",
    "x86_64-pc-windows-msvc" to "windows-x64",
    "x86_64-unknown-linux-gnu" to "linux-x64",
)

// openpnp-capture and JNA name their natives by JNA platform prefix.
val jnaNativeResourceDirs = mapOf(
    "darwin-aarch64" to "macos-arm64",
    "win32-x86-64" to "windows-x64",
    "linux-x86-64" to "linux-x64",
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
    from({ openpnpCaptureJar.map(::zipTree) }) {
        include(jnaNativeResourceDirs.keys.map { "$it/*" })
        eachFile { path = "${jnaNativeResourceDirs.getValue(relativePath.segments[0])}/$name" }
        includeEmptyDirs = false
    }
    // JNA loads its own native from jna.boot.library.path instead of unpacking it into a temp directory.
    from({ jnaJar.map(::zipTree) }) {
        include(jnaNativeResourceDirs.keys.map { "com/sun/jna/$it/*jnidispatch*" })
        eachFile { path = "${jnaNativeResourceDirs.getValue(relativePath.segments[3])}/$name" }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("native-resources"))
}

// Every target's natives are staged on every build host, so each target dir is checked with its own file names.
val targetNativeFiles = mapOf(
    "macos-arm64" to listOf(
        "libsqlcipher_jni.dylib", "libbeam_sdk_kmp.dylib", "libopenpnp-capture.dylib", "libjnidispatch.jnilib",
    ),
    "linux-x64" to listOf("libsqlcipher_jni.so", "libbeam_sdk_kmp.so", "libopenpnp-capture.so", "libjnidispatch.so"),
    "windows-x64" to listOf("sqlcipher_jni.dll", "beam_sdk_kmp.dll", "openpnp-capture.dll", "jnidispatch.dll"),
)

val verifyNativeResources = tasks.register("verifyNativeResources") {
    val prepareAppResources = tasks.named<Sync>("prepareAppResources")
    dependsOn(prepareAppResources, stageNativeResources)
    doLast {
        targetNativeFiles.forEach { (target, fileNames) ->
            fileNames.forEach { fileName ->
                val native = File(stageNativeResources.get().destinationDir, "$target/$fileName")
                check(native.isFile) { "Native library is missing from the staged resources: $native" }
            }
        }
        val nativeFiles = listOf("sqlcipher_jni", "beam_sdk_kmp", "openpnp-capture").map(System::mapLibraryName) +
            // JNA names its macOS native .jnilib, the name jna.boot.library.path looks for first.
            System.mapLibraryName("jnidispatch").replace(".dylib", ".jnilib")
        nativeFiles.forEach { fileName ->
            val native = File(prepareAppResources.get().destinationDir, fileName)
            check(native.isFile) { "Native library is missing from the app resources: $native" }
        }
    }
}

tasks.withType<AbstractJPackageTask>().configureEach {
    dependsOn(verifyNativeResources)
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
                // Compose's default entitlements plus the camera: the hardened runtime denies the camera without it.
                entitlementsFile.set(project.file("macos-entitlements.plist"))
                // Without a usage description macOS terminates the app on its first camera access.
                infoPlist {
                    extraKeysRawXml = """
                        <key>NSCameraUsageDescription</key>
                        <string>P.CASH uses the camera to scan QR codes.</string>
                    """.trimIndent()
                }
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
