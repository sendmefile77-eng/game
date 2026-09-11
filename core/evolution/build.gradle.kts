plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:simulation"))
    implementation(project(":core:worldgen"))
    implementation(project(":core:civilization"))
    testImplementation(libs.junit)
}
