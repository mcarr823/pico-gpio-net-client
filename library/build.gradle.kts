import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.dokka)
}

group = "dev.mcarr.pgnc"
version = "1.0.0"

kotlin {
    jvm()
    androidTarget {
        publishLibraryVariants("release")
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    linuxX64()

    // The targets below should be supported, in theory,
    // based on https://ktor.io/docs/server-platforms.html
    // But I don't have a Mac to test on, so...
    iosArm64()
    iosX64()
    iosSimulatorArm64()
    watchosArm32()
    watchosArm64()
    watchosSimulatorArm64()
    tvosArm64()
    tvosX64()
    tvosSimulatorArm64()
    macosX64()
    macosArm64()

    // Deprecated
    //iosArm32()
    //watchosX86()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.ktor.network)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.coroutines.test)
            }
        }
    }
}

android {
    namespace = "dev.mcarr.pgnc.library"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    signAllPublications()

    coordinates(group.toString(), "library", version.toString())

    pom {
        name = "Kotlin PGN Client Library"
        description = "Kotlin Multiplatform Module for communicating with Rasperry Pi Pico micro-controllers"
        inceptionYear = "2025"
        url = "https://github.com/mcarr823/pico-gpio-net-client/"
        licenses {
            license {
                name = "GNU GENERAL PUBLIC LICENSE, Version 3"
                url = "https://www.gnu.org/licenses/gpl-3.0.en.html"
                distribution = "https://www.gnu.org/licenses/gpl-3.0.en.html"
            }
        }
        developers {
            developer {
                id = "mcarr823"
                name = "mcarr823"
                url = "https://github.com/mcarr823/"
            }
        }
        scm {
            url = "https://github.com/mcarr823/pico-gpio-net-client/"
            connection = "scm:git:git://github.com/mcarr823/pico-gpio-net-client.git"
            developerConnection = "scm:git:ssh://git@github.com/mcarr823/pico-gpio-net-client.git"
        }
    }
}
