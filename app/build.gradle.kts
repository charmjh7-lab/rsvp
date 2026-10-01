import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingSource = rootProject.file("signing/rsvp-update-key.b64")
val persistentKeystore = layout.buildDirectory.file("generated-signing/rsvp-update.jks").get().asFile
if (signingSource.exists()) {
    persistentKeystore.parentFile.mkdirs()
    persistentKeystore.writeBytes(Base64.getMimeDecoder().decode(signingSource.readText().trim()))
}

android {
    namespace = "com.example.rsvpreader"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.rsvpreader"
        minSdk = 26
        targetSdk = 35
        versionCode = 14
        versionName = "1.2.1"
    }

    signingConfigs {
        create("persistentDebug") {
            storeFile = persistentKeystore
            storePassword = "rsvpreader2026"
            keyAlias = "rsvpupdate"
            keyPassword = "rsvpreader2026"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("persistentDebug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-text")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
