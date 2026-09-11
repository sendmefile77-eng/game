plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:simulation"))
    implementation(project(":core:civilization"))
    implementation(project(":core:people"))
    testImplementation(libs.junit)
}
