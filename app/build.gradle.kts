plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.bidware"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bidware"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    viewBinding {
        enable = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

}

googleServices { disableVersionCheck = true }

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // Import Firebase BoM (Manages Firebase versions)
    implementation(platform(libs.firebase.bom))

    // Firebase Services
    implementation(libs.firebase.auth) // Authentication
    implementation(libs.firebase.database) // Realtime Database (Live Bidding)
    implementation(libs.firebase.firestore) // Firestore (Vehicle & User Data)
    // implementation(libs.firebase.storage) // Cloud Storage (Images & Documents) - Not needed, using base64
    implementation(libs.firebase.messaging) // Push Notifications
    implementation(libs.firebase.analytics) // Analytics
    implementation(libs.firebase.functions) // Cloud Functions (AI & Chatbot)

    // AI & Chatbot Features
    implementation(libs.mlkit.smart.reply) // ML Kit Smart Reply (Chatbot)
    implementation(libs.mlkit.language.id) // Language Identification (Chatbot)
    implementation(libs.tensorflow.lite) // TensorFlow Lite (Optional AI)

    implementation("com.github.bumptech.glide:glide:4.12.0")
    annotationProcessor("com.github.bumptech.glide:compiler:4.12.0")



}