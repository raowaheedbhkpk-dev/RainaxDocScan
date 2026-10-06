plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.rainax.docscan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rainax.docscan" // your unique Play Store id
        minSdk = 24
        targetSdk = 35
        versionCode = (project.findProperty("vcode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("vname") as String?) ?: "1.0.0"
        // Google's TEST AdMob app id. Replace with your real one before release.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Document scanner (edge detection, crop, filters, gallery import, PDF) - on device, free
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0-beta1")
    // OCR (Latin scripts), on device, free
    implementation("com.google.mlkit:text-recognition:16.0.1")
    // PDF password protection
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    // Ads
    implementation("com.google.android.gms:play-services-ads:23.3.0")
}
