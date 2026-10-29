plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.spike.android"
    compileSdk = 35
    ndkVersion = "30.0.16248370" // NDK r30

    defaultConfig {
        applicationId = "dev.spike.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64") }
        externalNativeBuild { cmake { cppFlags += "-std=c++17" } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    // One universal APK: no ABI or density splits, all three ABIs inside the same file.
    splits { abi { isEnable = false }; density { isEnable = false } }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets["main"].java.srcDir("../shared-ui/src/main/kotlin")
    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed so the release APK installs directly; swap in your own keystore for stores.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    packaging {
        resources.excludes += setOf(
            "META-INF/INDEX.LIST", "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*",
            "META-INF/io.netty.versions.properties", "META-INF/*.kotlin_module",
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
