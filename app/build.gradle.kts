plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Optional: -PdebugAbi=arm64-v8a limits debug builds to one ABI, which keeps native
// builds fast on weak machines. See docs/BUILDING.md.
val debugAbi = providers.gradleProperty("debugAbi").orNull
val releaseAbis = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

// Optional: -PlocalRelease signs the release build with the debug key so it can be installed for testing.
val localRelease = providers.gradleProperty("localRelease").isPresent

android {
    namespace = "io.github.yoldaskelbow.amplatypus"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    ndkVersion = "30.0.16248370"

    defaultConfig {
        // PROVISIONAL: must be finalised before the first public release,
        // the applicationId can never be changed afterwards.
        applicationId = "io.github.yoldaskelbow.amplatypus"
        minSdk = 26
        targetSdk = 36
        versionCode = 9
        versionName = "0.3.0-pre2"

        buildConfigField("String", "REPO_URL", "\"https://github.com/YoldasKelboW/amplatypus\"")
    }

    buildTypes {
        debug {
            ndk { abiFilters += debugAbi?.let { listOf(it) } ?: releaseAbis }
            buildConfigField("boolean", "AMP_SOURCE_ENABLED", "true")
        }
        release {
            // An AMP source stays off in release builds until the permission is
            // documented in docs/PERMISSIONS.md.
            buildConfigField("boolean", "AMP_SOURCE_ENABLED", "false")
            if (localRelease) {
                // For trying the optimised build on your own device: debug key, one ABI.
                signingConfig = signingConfigs.getByName("debug")
                ndk { abiFilters += debugAbi?.let { listOf(it) } ?: releaseAbis }
            } else {
                ndk { abiFilters += releaseAbis }
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    // Native replayers are built from source (git submodules under third_party/).
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // No encrypted dependency metadata block in APK/AAB: F-Droid and IzzyOnDroid
    // cannot inspect it, and it gets in the way of reproducible builds.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        warningsAsErrors = false
        abortOnError = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive.navigation.suite)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.okhttp)
    implementation(libs.coroutines.android)

    testImplementation(libs.junit)
}
