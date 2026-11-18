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

// ---- UADE player sets ------------------------------------------------------------------------
// UADE plays each exotic Amiga format with that format's original replay routine, a small Amiga
// program from third_party/uade/players. Only a few of them carry a free licence
// (docs/UADE_LICENSING.md), so there are two builds: "free" ships just those, "full" ships all.
val uadeDir = rootProject.file("third_party/uade")
val uadeFreePlayers = setOf("PreTracker", "Fred", "ManiacsOfNoise", "MusicAssembler", "SIDMon1.0")

// Left out of every build for now: formats whose songs need a whole set of instrument files.
// Two-file formats (TFMX, Richard Joseph, Audio Sculpture) are handled, see ModlandCompanions.kt.
val uadeMultiFilePlayers = listOf(
    "SonixMusicDriver", "MusicMaker", "Quartet",
    "ScottJohnston", "DirkBialluch", "DynamicSynthesizer", "MIDI-Loriciel", "PaulSummers",
)

// Extensions that stay with the tracker replayers when a file is imported from the device.
val uadeIgnoredExtensions = setOf("mod", "med", "ahx", "thx", "hvl", "okt", "okta", "digi", "sfx", "dbm")

/** The "player<TAB>prefixes=a,b ..." lines of eagleplayer.conf for the players of one build. */
fun uadePlayerLines(full: Boolean): List<String> {
    val conf = File(uadeDir, "eagleplayer.conf")
    if (!conf.isFile) return emptyList()
    return conf.readLines().filter { line ->
        val name = line.substringBefore('\t').trim()
        name.isNotEmpty() && !name.startsWith("#") && File(uadeDir, "players/$name").isFile &&
            uadeMultiFilePlayers.none { name.startsWith(it) } && (full || name in uadeFreePlayers)
    }
}

fun uadePlayerNames(full: Boolean): String =
    uadePlayerLines(full).map { it.substringBefore('\t').trim() }.toSortedSet().joinToString(",")

fun uadeExtensions(full: Boolean): String = uadePlayerLines(full)
    .flatMap { line -> Regex("prefixes=(\\S+)").find(line)?.groupValues?.get(1)?.split(',') ?: emptyList() }
    .map { it.lowercase() }
    .filter { it.isNotEmpty() && it !in uadeIgnoredExtensions }
    .toSortedSet()
    .joinToString(",")

/** Collects what UADE needs at run time into one assets folder: players, sound core, configuration. */
abstract class UadeAssetsTask : DefaultTask() {
    @get:InputDirectory abstract val uade: DirectoryProperty
    @get:Input abstract val playerLines: ListProperty<String>
    @get:Input abstract val withVolumes: Property<Boolean>
    @get:OutputDirectory abstract val output: DirectoryProperty

    @TaskAction
    fun run() {
        val source = uade.get().asFile
        val target = File(output.get().asFile, "uade")
        target.deleteRecursively()
        File(target, "players").mkdirs()
        for (line in playerLines.get()) {
            val name = line.substringBefore('\t').trim()
            // One player can serve several formats and then has several lines.
            File(source, "players/$name").copyTo(File(target, "players/$name"), overwrite = true)
        }
        // Settings and data files some players read from the Amiga volumes ENV: and S:. None of the
        // freely licensed players needs them, and they carry no licence of their own.
        if (withVolumes.get()) {
            for (volume in listOf("ENV", "S")) {
                File(source, "players/$volume").takeIf { it.isDirectory }?.copyRecursively(File(target, "players/$volume"))
            }
        }
        File(target, "eagleplayer.conf").writeText(playerLines.get().joinToString("\n", postfix = "\n"))
        File(source, "amigasrc/score/score").copyTo(File(target, "score"))
        File(source, "uaerc").copyTo(File(target, "uaerc"))
    }
}

android {
    namespace = "io.github.yoldaskelbow.amplatypus"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    ndkVersion = "30.0.16248370"

    defaultConfig {
        // Final. The applicationId can never be changed once the app is in a store.
        applicationId = "io.github.yoldaskelbow.amplatypus"
        minSdk = 26
        targetSdk = 36
        versionCode = 16
        versionName = "0.5.0-pre1"

        buildConfigField("String", "REPO_URL", "\"https://github.com/YoldasKelboW/amplatypus\"")
    }

    flavorDimensions += "players"
    productFlavors {
        create("free") {
            dimension = "players"
            buildConfigField("String", "UADE_EXTENSIONS", "\"${uadeExtensions(full = false)}\"")
            buildConfigField("String", "UADE_PLAYERS", "\"${uadePlayerNames(full = false)}\"")
        }
        create("full") {
            dimension = "players"
            versionNameSuffix = "-full"
            buildConfigField("String", "UADE_EXTENSIONS", "\"${uadeExtensions(full = true)}\"")
            buildConfigField("String", "UADE_PLAYERS", "\"${uadePlayerNames(full = true)}\"")
        }
    }

    // uadecore is a program, not a library. It is packaged as libuadecore.so so that Android
    // installs it into the app's native library folder, the only place it may be started from.
    packaging {
        jniLibs { useLegacyPackaging = true }
    }

    buildTypes {
        debug {
            ndk { abiFilters += debugAbi?.let { listOf(it) } ?: releaseAbis }
            buildConfigField("boolean", "AMP_SOURCE_ENABLED", "true")
        }
        release {
            // AMP is used with the staff's permission, documented in docs/PERMISSIONS.md.
            buildConfigField("boolean", "AMP_SOURCE_ENABLED", "true")
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

androidComponents {
    onVariants { variant ->
        val full = variant.productFlavors.any { it.second == "full" }
        val task = tasks.register<UadeAssetsTask>("uadeAssets${variant.name.replaceFirstChar { it.uppercase() }}") {
            uade.set(uadeDir)
            playerLines.set(uadePlayerLines(full))
            withVolumes.set(full)
        }
        variant.sources.assets?.addGeneratedSourceDirectory(task, UadeAssetsTask::output)
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
    // Android's own org.json is only a stub in unit tests; this is the same API for the JVM.
    testImplementation(libs.json)
}
