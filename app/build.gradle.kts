plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.dokka)
}

android {
    namespace = "net.getzit.repti"
    compileSdk = 34

    defaultConfig {
        applicationId = "net.getzit.repti"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    buildFeatures {
        compose = true
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    coreLibraryDesugaring(libs.android.desugar)
    implementation(libs.androidx.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.appcompat)
    implementation(libs.android.material)
    testImplementation(libs.junit)
    testImplementation(libs.kotest.property)
    testImplementation(libs.jsonassert)
    dokkaPlugin(libs.dokka.android)

    val composeBom = platform(libs.compose.bom)
    for (lib in sequenceOf(
        composeBom,
        libs.androidx.espresso.core,
        libs.androidx.junit,
        libs.compose.ui.test.junit4,
        project(":shared-test"),
    )) {
        testImplementation(lib)
        androidTestImplementation(lib)
    }
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.espresso.intents)
    androidTestImplementation(libs.androidx.junit)

    implementation(composeBom)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.espresso.idling.resource)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
