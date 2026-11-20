plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciVersionCode = (System.getenv("NL_VERSION_CODE") ?: "1").toInt()

android {
    namespace = "com.nlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nlauncher"
        minSdk = 26
        targetSdk = 35
        versionCode = ciVersionCode
        versionName = "1.0.$ciVersionCode"
    }

    signingConfigs {
        create("release") {
            // 本番署名: 環境変数 NL_KEYSTORE 等が設定されていればそれを使う。
            // 未設定の場合はリポジトリ同梱の公開キーストアで署名する（アップデート互換のため固定）。
            val ksPath = System.getenv("NL_KEYSTORE")
            if (ksPath != null && file(ksPath).exists()) {
                storeFile = file(ksPath)
                storePassword = System.getenv("NL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("NL_KEY_ALIAS")
                keyPassword = System.getenv("NL_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/nlauncher-public.jks")
                storePassword = "nlauncher"
                keyAlias = "nlauncher"
                keyPassword = "nlauncher"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
