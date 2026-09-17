import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.example.imiq"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.imiq"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("localEmulator") {
            dimension = "environment"
            applicationIdSuffix = ".local.emulator"
            versionNameSuffix = "-local-emulator"
            buildConfigField("String", "DYCONET_BASE_URL", "\"http://10.0.2.2:8077\"")
            buildConfigField("boolean", "LOCAL_CORE_MODE", "true")
        }
        create("localUsb") {
            dimension = "environment"
            applicationIdSuffix = ".local.usb"
            versionNameSuffix = "-local-usb"
            buildConfigField("String", "DYCONET_BASE_URL", "\"http://127.0.0.1:8077\"")
            buildConfigField("boolean", "LOCAL_CORE_MODE", "true")
        }
        create("production") {
            dimension = "environment"
            buildConfigField(
                "String",
                "DYCONET_BASE_URL",
                "\"https://imiq-app.et.uni-magdeburg.de\"",
            )
            buildConfigField("boolean", "LOCAL_CORE_MODE", "false")
        }
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
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Core Android dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.0")

    // Compose BOM - this manages all Compose library versions
    implementation(platform("androidx.compose:compose-bom:2024.02.00"))

    // Compose dependencies
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Optional but useful for development
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Testing dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Networking - OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Serialization - Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // Retrofit for API calls
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.11.0")

    // Google Play Services for location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // MapLibre — free vector maps (GL) with a dark style + GPU polylines
    implementation("org.maplibre.gl:android-sdk:11.5.2")
}
