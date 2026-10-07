import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun prop(name: String, default: String = ""): String = providers.gradleProperty(name).orElse(default).get()
fun str(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

val appId = prop("APP_ID", "com.prajakeeyam")

/** Firebase / Google Sign-In config read from app/google-services.json (no google-services Gradle plugin needed). */
data class FirebaseCfg(val projectId: String, val appId: String, val apiKey: String, val webClientId: String)

@Suppress("UNCHECKED_CAST")
val firebaseCfg: FirebaseCfg = run {
    val file = project.file("google-services.json")
    if (!file.exists()) {
        logger.warn("google-services.json not found: Google Sign-In will be unavailable in this build")
        return@run FirebaseCfg("", "", "", "")
    }
    val root = JsonSlurper().parse(file) as Map<String, Any?>
    val projectInfo = root["project_info"] as Map<String, Any?>
    val clients = root["client"] as List<Map<String, Any?>>
    val client = clients.firstOrNull {
        ((it["client_info"] as Map<String, Any?>)["android_client_info"] as Map<String, Any?>)["package_name"] == appId
    } ?: error("google-services.json has no Android app with package $appId")
    val info = client["client_info"] as Map<String, Any?>
    val web = (client["oauth_client"] as? List<Map<String, Any?>>)
        ?.firstOrNull { (it["client_type"] as? Number)?.toInt() == 3 }?.get("client_id") as? String
    val apiKey = (client["api_key"] as? List<Map<String, Any?>>)?.firstOrNull()?.get("current_key") as? String
    if (web.isNullOrBlank()) {
        logger.warn("google-services.json has no web OAuth client: enable the Google provider in Firebase Authentication, add the SHA-1 fingerprints, and re-download the file")
    }
    FirebaseCfg(projectInfo["project_id"] as String, info["mobilesdk_app_id"] as String, apiKey ?: "", web ?: "")
}

android {
    namespace = "app.prajakeeyam"
    compileSdk = 37

    defaultConfig {
        applicationId = appId
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", str(prop("GOOGLE_WEB_CLIENT_ID").ifBlank { firebaseCfg.webClientId }))
        buildConfigField("String", "FIREBASE_PROJECT_ID", str(firebaseCfg.projectId))
        buildConfigField("String", "FIREBASE_APP_ID", str(firebaseCfg.appId))
        buildConfigField("String", "FIREBASE_API_KEY", str(firebaseCfg.apiKey))
        buildConfigField("String", "CLOUDINARY_CLOUD_NAME", str(prop("CLOUDINARY_CLOUD_NAME")))
        buildConfigField("String", "CLOUDINARY_UPLOAD_PRESET", str(prop("CLOUDINARY_UPLOAD_PRESET", "problems")))
        buildConfigField("String", "PRIVACY_URL", str(prop("PRIVACY_URL", "https://narasimhas9490.github.io/Prajakeeyam/privacy.html")))
        vectorDrawables.useSupportLibrary = true
    }

    // Release signing: android/keystore.properties (git-ignored) with
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
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
