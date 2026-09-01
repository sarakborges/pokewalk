plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.sarakborges.litewalker"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.sarakborges.litewalker"
        minSdk = 28
        targetSdk = 36
        versionCode = 10105
        versionName = "1.1.5"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.12.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
