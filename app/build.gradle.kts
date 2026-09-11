plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sendmefile77.chronosphere"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.sendmefile77.chronosphere"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(project(":core:simulation"))
    implementation(project(":core:worldgen"))
    implementation(project(":core:civilization"))
    implementation(project(":core:people"))
    implementation(project(":core:economy"))
    implementation(project(":core:society"))
    implementation(project(":core:history"))
    implementation(project(":core:textgen"))
    implementation(project(":core:storage"))
    implementation(project(":core:adult-contracts"))
    implementation(project(":feature:map"))

    // Full builds package the optional adult implementation when that module exists.
    // The app itself compiles only against core/adult-contracts and loads the implementation reflectively.
    project.findProject(":feature:adult")?.let { runtimeOnly(it) }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
