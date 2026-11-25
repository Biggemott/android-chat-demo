plugins {
    alias(libs.plugins.ktlint)
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

ktlint {
    version.set(libs.versions.ktlint)
    val buildDirectory = layout.buildDirectory.get().asFile
    filter { exclude { it.file.startsWith(buildDirectory) } }
}

android {
    namespace = "com.biggemott.chatdemo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.biggemott.chatdemo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions.unitTests.isIncludeAndroidResources = true
    buildFeatures {
        compose = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    constraints {
        androidTestImplementation(libs.androidx.espresso.core) {
            because("Older Espresso versions reflectively call InputManager.getInstance, which Android 17 removed")
        }
        implementation(libs.androidx.lifecycle.runtime) {
            because("Activity Compose's transitive Lifecycle lint checks use obsolete lint APIs")
        }
    }
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    add("testDebugImplementation", platform(libs.androidx.compose.bom))
    add("testDebugImplementation", libs.robolectric)
    add("testDebugImplementation", libs.roborazzi.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
