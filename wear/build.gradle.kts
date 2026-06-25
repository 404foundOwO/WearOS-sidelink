plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.found404.sidelink"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.found404.sidelink"
        minSdk = 31
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material3) // Using M3 for Wear as well if possible, or wear compose
    implementation("androidx.wear.compose:compose-material:1.4.0")
    implementation("androidx.wear.compose:compose-foundation:1.4.0")
    implementation("androidx.wear.compose:compose-navigation:1.4.0")
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.media)
    implementation(libs.wear)
    implementation(libs.wear.ongoing)
    implementation(libs.palette)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}
