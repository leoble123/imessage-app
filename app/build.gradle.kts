plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.leo.imessage"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.leo.imessage"
        minSdk = 26
        targetSdk = 35
        versionCode = 29
        versionName = "0.29.0"
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
        ndk {
            // Only the ABI we actually target - keeps the APK lean.
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-signed so a sideloaded release build installs without
            // needing a keystore set up.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // So Settings can report the real version instead of a literal that
        // drifts - the old hardcoded "0.1.0" had been wrong for ten builds.
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            // The cross-compiled Rust core lands here; UniFFI's generated
            // Kotlin bindings sit alongside the hand-written sources.
            jniLibs.srcDirs("src/main/jniLibs")
            java.srcDirs("src/main/java", "build/generated/uniffi")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // The Rust core is ~20MB uncompressed, and modern AGP stores
            // native libs uncompressed so they can be mapped straight out of
            // the APK. That's the better runtime trade, but it makes the
            // download about three times the size - which matters a lot more
            // when the APK is sideloaded by hand than a few ms of load time.
            useLegacyPackaging = true
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")

    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Real backdrop blur - Compose has no native backdrop-filter.
    implementation("dev.chrisbanes.haze:haze:1.2.2")

    // UniFFI's Kotlin bindings call into libimessage_core.so through JNA.
    // The @aar classifier matters: the plain jar carries desktop natives and
    // no Android ones, so JNA fails to find its own dispatch library at
    // runtime with a NoClassDefFoundError that points nowhere useful.
    implementation("net.java.dev.jna:jna:5.15.0@aar")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
