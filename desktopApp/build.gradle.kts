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
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(compose.desktop.currentOs)
}

val sqlCipherDriver = libs.bitcoin.kit.sqlcipher.driver.get()
val sqlCipherDriverJar = configurations.runtimeClasspath.get().incoming.artifactView {
    componentFilter {
        it is ModuleComponentIdentifier && it.group == sqlCipherDriver.group && it.module == sqlCipherDriver.name
    }
}.files

// The packager re-signs dylibs inside jars, which breaks the driver's own checksum: ship them as app resources.
val stageSqlCipherNatives = tasks.register<Sync>("stageSqlCipherNatives") {
    from({ sqlCipherDriverJar.map(::zipTree) }) {
        include("natives/**")
        exclude("**/*.sha256")
        eachFile { path = path.removePrefix("natives/") }
        includeEmptyDirs = false
    }
    into(layout.buildDirectory.dir("sqlcipher-natives"))
}

val verifySqlCipherNativeResource = tasks.register("verifySqlCipherNativeResource") {
    val prepareAppResources = tasks.named<Sync>("prepareAppResources")
    dependsOn(prepareAppResources)
    doLast {
        val native = File(prepareAppResources.get().destinationDir, System.mapLibraryName("sqlcipher_jni"))
        check(native.isFile) { "SQLCipher native is missing from the app resources: $native" }
    }
}

tasks.withType<AbstractJPackageTask>().configureEach {
    dependsOn(verifySqlCipherNativeResource)
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
            appResourcesRootDir.set(layout.dir(stageSqlCipherNatives.map { it.destinationDir }))
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
        dependsOn(verifySqlCipherNativeResource)
        if (isMacOs) {
            jvmArgs("-Xdock:name=$applicationName")
        }
    }
}
