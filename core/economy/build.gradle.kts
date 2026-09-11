plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:civilization"))
    implementation(project(":core:worldgen"))
    implementation(project(":core:simulation"))
    testImplementation(libs.junit)
}
