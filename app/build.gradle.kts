import java.util.Properties

plugins {
    id("com.android.application")
}

// Állandó aláírókulcs: a CI a GitHub Secretsből, a helyi gép a ~/.gradle/gradle.properties-ből
// (vagy környezeti változóból) kapja. A tárolóba soha nem kerül kulcs vagy jelszó.
fun secret(prop: String, env: String): String? =
    (findProperty(prop) as String?)?.takeIf { it.isNotBlank() } ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val storeFilePath = secret("gamestv.storeFile", "GAMESTV_STORE_FILE")
val storePass = secret("gamestv.storePassword", "GAMESTV_STORE_PASSWORD")
val keyAliasName = secret("gamestv.keyAlias", "GAMESTV_KEY_ALIAS")
val keyPass = secret("gamestv.keyPassword", "GAMESTV_KEY_PASSWORD")
val hasSigning = listOf(storeFilePath, storePass, keyAliasName, keyPass).all { it != null }

// A versionCode a commitok száma: minden új build nagyobb, így felülírja a régit a TV-n.
val commitCount: Int = runCatching {
    val p = ProcessBuilder("git", "rev-list", "--count", "HEAD").directory(rootDir).start()
    p.inputStream.bufferedReader().readText().trim().toInt()
}.getOrDefault(1)

android {
    namespace = "io.github.nomadbeni.gamestv"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.nomadbeni.gamestv"
        minSdk = 24
        targetSdk = 37
        versionCode = commitCount
        versionName = "1.0.$commitCount"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = file(storeFilePath!!)
                storePassword = storePass
                keyAlias = keyAliasName
                keyPassword = keyPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    dependenciesInfo {
        // Nincs Google-nak szóló függőségi metaadat az APK-ban.
        includeInApk = false
        includeInBundle = false
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        // A TV-hez elég az xhdpi banner és ikon.
        disable += "IconMissingDensityFolder"
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.18.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
}
