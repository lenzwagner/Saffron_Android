import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProps = Properties().also { props ->
    rootProject.file("local.properties").takeIf { it.exists() }
        ?.inputStream()?.use { props.load(it) }
}

android {
    namespace = "com.zephron.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zephron.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 51
        versionName = "5.5"
    }

    signingConfigs {
        create("release") {
            storeFile = file(localProps["RELEASE_STORE_FILE"] as String)
            storePassword = localProps["RELEASE_STORE_PASSWORD"] as String
            keyAlias = localProps["RELEASE_KEY_ALIAS"] as String
            keyPassword = localProps["RELEASE_KEY_PASSWORD"] as String
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = false   // skip lintVitalRelease — biggest time sink
        abortOnError = false
    }

    buildTypes {
        val geminiKey = localProps.getProperty("GEMINI_API_KEY", "")
        val pexelsKey = localProps.getProperty("PEXELS_API_KEY", "")
        debug {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
            buildConfigField("String", "BACKEND_URL", "\"https://saffron-backend-zxqb.onrender.com\"")
            buildConfigField("String", "PEXELS_API_KEY", "\"$pexelsKey\"")
        }
        release {
            buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
            buildConfigField("String", "BACKEND_URL", "\"https://saffron-backend-zxqb.onrender.com\"")
            buildConfigField("String", "PEXELS_API_KEY", "\"$pexelsKey\"")
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

// ── Auto-copy release APK to Google Drive after every assembleRelease ────────
val driveApkDir = providers.systemProperty("user.home").map { home ->
    "$home/Library/CloudStorage/GoogleDrive-lorenz.wagner99@gmail.com/Meine Ablage"
}

tasks.register("copyApkToDrive") {
    notCompatibleWithConfigurationCache("Copies APK to local Google Drive folder")
    dependsOn("assembleRelease")
    doLast {
        val versionName = android.defaultConfig.versionName ?: "unknown"
        val destDir = file(driveApkDir.get())
        destDir.mkdirs()
        val src = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        val dest = destDir.resolve("Zephron-v$versionName.apk")
        src.copyTo(dest, overwrite = true)
        println("✅  APK → Google Drive: ${dest.name}")
    }
}

afterEvaluate {
    tasks.named("assembleRelease") {
        finalizedBy("copyApkToDrive")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.crashlytics)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    debugImplementation(libs.androidx.ui.tooling)
}
