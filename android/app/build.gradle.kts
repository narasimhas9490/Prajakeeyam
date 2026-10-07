import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun prop(name: String, default: String = ""): String = providers.gradleProperty(name).orElse(default).get()
fun str(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "app.prajakeeyam"
    compileSdk = 37

    defaultConfig {
        applicationId = prop("APP_ID", "in.prajakeeyam.app")
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", str(prop("GOOGLE_WEB_CLIENT_ID")))
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", str(prop("CLOUDINARY_CLOUD_NAME")))
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", str(prop("CLOUDINARY_UPLOAD_PRESET", "problems")))
        buildConfigField("String", "PRIVACY_URL", str(prop("PRIVACY_URL", "https://example.github.io/prajakeeyam/privacy.html")))
        vectorDrawables.useSupportLibrary = true
    }

    // Optional release signing: android/keystore.properties (git-ignored) with
    // storeFile=..., storePassword=..., keyAlias=..., keyPassword=...
    val keystoreProps = rootProject.file("keystore.properties")
    if (keystoreProps.exists()) {
        val p = Properties().apply { keystoreProps.inputStream().use { load(it) } }
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(p.getProperty("storeFile"))
                storePassword = p.getProperty("storePassword")
                keyAlias = p.getProperty("keyAlias")
                keyPassword = p.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "API_BASE_URL", str(prop("API_BASE_URL_DEBUG", "http://10.0.2.2:8000")))
        }
        release {
            buildConfigField("String", "API_BASE_URL", str(prop("API_BASE_URL_RELEASE", "https://prajakeeyam-api.onrender.com")))
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    androidResources {
        localeFilters += listOf("en", "te")
    }
    bundle {
        language { enableSplit = false } // Telugu and English must always ship together
    }
    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/*.version",
            "DebugProbesKt.bin",
            "kotlin/**",
        )
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.material.icons.extended) // R8 keeps only the icons we reference
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
