import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.workcalendar"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.workcalendar"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.2"

        resValue(
            "string",
            "google_web_client_id",
            "80721403029-dis4lmbikbo3bvvqqf4sdnvm5utfecpd.apps.googleusercontent.com"
        )
    }

    signingConfigs {
        create("release") {
            val keystoreFile = file("release.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        resources {
            excludes += setOf(
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/license.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/notice.txt",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module",
                "**/*.proto",
                "com/google/api/client/googleapis/json/error.proto"
            )
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.google.android.gms:play-services-auth:21.3.0")

    implementation("com.google.api-client:google-api-client-android:2.7.0")
    implementation("com.google.http-client:google-http-client-gson:1.45.0")
    implementation("com.google.apis:google-api-services-drive:v3-rev20241027-2.0.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
}
