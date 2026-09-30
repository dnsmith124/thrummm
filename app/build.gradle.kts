import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing comes from keystore.properties (git-ignored) when present; see
// keystore.properties.example. Without it, release builds are signed with the debug key so
// they still install over a debug build without losing data.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.dnsmith.thrummm"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dnsmith.thrummm"
        minSdk = 31
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    // No Google-readable dependency metadata blob in the APK (F-Droid asks for this too).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            // Kotlin reflection metadata; the app doesn't use kotlin-reflect.
            excludes += listOf("kotlin/**", "META-INF/*.version", "META-INF/*.kotlin_module")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
