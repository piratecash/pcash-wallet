import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("kotlin-parcelize")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

androidComponents {
    onVariants { variant ->
        variant.sources.res?.addStaticSourceDirectory("src/main/res")
    }
}

detekt {
    source.setFrom("src/commonMain/kotlin", "src/androidMain/kotlin", "src/main/java")
}

kotlin {
    android {
        namespace = "cash.p.terminal.ui_compose"
        compileSdk = rootProject.ext.get("compile_sdk_version") as Int
        minSdk = rootProject.ext.get("min_sdk_version") as Int
        androidResources.enable = true

        optimization {
            keepRules.file("proguard-rules.pro")
            consumerKeepRules.file("consumer-rules.pro")
            consumerKeepRules.publish = true
        }

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm("desktop") {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":core:resources"))
                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.ui)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.material3)
            }
        }

        androidMain {
            kotlin.srcDir("src/main/java")

            dependencies {
                implementation(project.dependencies.platform(libs.koin.bom))
                implementation(libs.koin.core)
                implementation(libs.koin.android)
                implementation(libs.koin.compose)

                implementation(project.dependencies.platform(libs.compose.bom))
                implementation(libs.compose.material)
                implementation(libs.coil.compose)

                implementation(libs.lifecycle.viewmodel.ktx)
                implementation(libs.androidx.fragment.ktx)
                implementation(libs.androidx.navigation.runtime.ktx)
                implementation(libs.androidx.navigation.fragment.ktx)

                implementation(libs.androidx.material3.android)
                implementation(libs.material)
                implementation(libs.androidx.ui.tooling)
                implementation(libs.androidx.ui.tooling.preview)

                implementation(project(":components:icons"))
                implementation(project(":core:strings"))
                implementation(project(":core:navigation"))
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

dependencies {
    add("desktopTestImplementation", compose.desktop.uiTestJUnit4)
    add("desktopTestRuntimeOnly", compose.desktop.currentOs)
}
