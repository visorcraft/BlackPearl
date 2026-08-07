import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

// Release signing is configured from release-signing.properties (gitignored,
// lives only on the build host). Keystore is archived under
// ~/.local/share/onex-sugar-backups/blackpearl-release/.
// NB: the import is required — without it `java` resolves to the Project's
// java extension and `java.util.Properties` fails to compile.
val releaseProps = Properties().apply {
    val f = rootProject.file("release-signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.visorcraft.blackpearl"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.visorcraft.blackpearl"
        minSdk = 26
        targetSdk = 34
        versionCode = 11
        versionName = "0.3.0"
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // Short git SHA shown on the About page (Grexa-style build chip).
        val gitSha = runCatching {
            ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(rootProject.projectDir)
                .start().inputStream.bufferedReader().readText().trim()
        }.getOrDefault("").ifEmpty { "unknown" }
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    signingConfigs {
        create("release") {
            keyAlias = releaseProps.getProperty("keyAlias", "blackpearl")
            keyPassword = releaseProps.getProperty("keyPassword", "")
            storeFile = releaseProps.getProperty("storeFile")?.let { file(it) }
            storePassword = releaseProps.getProperty("storePassword", "")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.documentfile:documentfile:1.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
