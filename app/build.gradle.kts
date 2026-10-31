plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.rhythme"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.rhythme"
        minSdk = 24
        targetSdk = 35
        // CI passes BUILD_NUMBER (the GitHub Actions run number); local builds stay at 1
        val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull?.toIntOrNull() ?: 1
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One key for every build (GitHub and, once the key file is on the PC, Android Studio),
    // so any APK can update the installed app. The key is NOT in git:
    // CI writes it from the SIGNING_KEYSTORE_BASE64 secret; the password comes from the
    // SIGNING_PASSWORD env var or "rhythmeSigningPassword" in ~/.gradle/gradle.properties.
    val keystoreFile = file("rhythme-shared.keystore")
    val signingPassword = providers.environmentVariable("SIGNING_PASSWORD").orNull
        ?: providers.gradleProperty("rhythmeSigningPassword").orNull
    signingConfigs {
        if (keystoreFile.exists() && signingPassword != null) {
            create("shared") {
                storeFile = keystoreFile
                storePassword = signingPassword
                keyAlias = "rhythme"
                keyPassword = signingPassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("shared")?.let { signingConfig = it }
        }
        release {
            // Without the key the release APK is unsigned (CI stops before that, see the workflow)
            signingConfigs.findByName("shared")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    // The release-time lint check crashes with this AGP on the CI JDK; Android Studio lint still works
    lint {
        checkReleaseBuilds = false
    }
    buildFeatures {
        compose = true
        prefab = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.oboe)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
