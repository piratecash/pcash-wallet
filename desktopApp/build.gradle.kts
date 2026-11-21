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
    implementation(project(":feature-wallet"))
    implementation(libs.ethereum.kit)
    implementation(libs.bitcoin.kit.sqlcipher.driver)
    implementation(libs.bitcoin.dashlib.native)
    implementation(libs.monero.kit)
    implementation(libs.zcash.sdk.kmp)
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
val dashlibNative = libs.bitcoin.dashlib.native.get()
val dashlibNativeJar = runtimeJar(dashlibNative.group, dashlibNative.name)
val moneroKit = libs.monero.kit.get()
val moneroKitDesktopJar = runtimeJar(moneroKit.group, "${moneroKit.name}-desktop")
val zcashSdk = libs.zcash.sdk.kmp.get()
val zcashSdkDesktopJar = runtimeJar(zcashSdk.group, "${zcashSdk.name}-desktop")
val beamSdk = libs.beam.sdk.get()
val beamSdkDesktopJar = runtimeJar(beamSdk.group, "${beamSdk.name}-desktop")
val openpnpCapture = libs.openpnp.capture.java.get()
val openpnpCaptureJar = runtimeJar(openpnpCapture.group, openpnpCapture.name)
val jnaJar = runtimeJar("net.java.dev.jna", "jna")

// Compose picks the app-resources subdirectory by this name; the BEAM and Zcash jars name their natives by Rust target triple.
val rustTripleResourceDirs = mapOf(
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
    // The SQLCipher and Dash BLS jars already name their native dirs like Compose does.
    from({ (sqlCipherDriverJar + dashlibNativeJar).map(::zipTree) }) {
        include("natives/**")
        exclude("**/*.sha256")
        eachFile { path = path.removePrefix("natives/") }
        includeEmptyDirs = false
    }
    from({ moneroKitDesktopJar.map(::zipTree) }) {
        include("native/**")
        eachFile { path = path.removePrefix("native/") }
        includeEmptyDirs = false
    }
    from({ (beamSdkDesktopJar + zcashSdkDesktopJar).map(::zipTree) }) {
        include("native/**")
        eachFile {
            val triple = relativePath.segments[1]
            path = "${rustTripleResourceDirs.getValue(triple)}/$name"
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

val packagedNativeLibraries = listOf(
    "sqlcipher_jni", "beam_sdk_kmp", "openpnp-capture", "jnidispatch", "monerujo", "zcash_sdk_kmp", "dashjbls",
)
val packagedNativeTargets = listOf("macos-arm64", "linux-x64", "windows-x64")

// JNA names its macOS native .jnilib, the name jna.boot.library.path looks for first.
fun packagedNativeFileName(library: String, platformFileName: String) =
    if (library == "jnidispatch") platformFileName.replace(".dylib", ".jnilib") else platformFileName

fun targetNativeFileName(target: String, library: String) = packagedNativeFileName(
    library,
    when (target.substringBefore('-')) {
        "macos" -> "lib$library.dylib"
        "windows" -> "$library.dll"
        else -> "lib$library.so"
    },
)

val verifyNativeResources = tasks.register("verifyNativeResources") {
    val prepareAppResources = tasks.named<Sync>("prepareAppResources")
    dependsOn(prepareAppResources, stageNativeResources)
    doLast {
        // Every target's natives are staged on every build host, so each target dir is checked with its own file names.
        packagedNativeTargets.forEach { target ->
            packagedNativeLibraries.forEach { library ->
                val native = File(stageNativeResources.get().destinationDir, "$target/${targetNativeFileName(target, library)}")
                check(native.isFile) { "Native library is missing from the staged resources: $native" }
            }
        }
        packagedNativeLibraries.forEach { library ->
            val native = File(
                prepareAppResources.get().destinationDir,
                packagedNativeFileName(library, System.mapLibraryName(library)),
            )
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
