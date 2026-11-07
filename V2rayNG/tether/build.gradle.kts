// Amier Tether: small client for the phone that joins the hotspot. It routes all traffic of that
// phone into the SOCKS5 proxy shared by Amier VPN (Proxy Tethering) or another tunnel app, using
// the same hev-socks5-tunnel library as Amier VPN.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// CI run number (from -PcassanovaBuild), shared with Amier VPN; 0 for local builds.
val cassanovaBuildNumber = (project.findProperty("cassanovaBuild") as String?)?.trim()?.toIntOrNull() ?: 0

android {
    namespace = "com.amier.tether"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.amier.tether"
        minSdk = 24
        targetSdk = 37
        versionCode = 1000 + cassanovaBuildNumber
        versionName = "1.0.0"
        buildConfigField("int", "CASSANOVA_BUILD", "$cassanovaBuildNumber")

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include("arm64-v8a", "armeabi-v7a")
                }
                isUniversalApk = false
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    sourceSets {
        getByName("main") {
            // libhev-socks5-tunnel.so is built into app/libs/<abi>/ by compile-hevtun.sh.
            jniLibs.srcDirs("../app/libs")
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            // app/libs also holds Amier VPN's other engines; Tether only needs hev-socks5-tunnel.
            excludes += setOf("**/libcassh.so", "**/libdnstt.so", "**/libhevsockstun.so")
        }
    }

    androidResources {
        localeFilters += listOf("en", "in")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }

    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
            .forEach { output ->
                val abi = output.getFilter("ABI") ?: "universal"
                output.outputFileName = "AmierTether_${variant.versionName}-b${cassanovaBuildNumber}_${abi}.apk"
            }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
