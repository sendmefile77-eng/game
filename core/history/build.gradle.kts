plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:simulation"))
    implementation(project(":core:civilization"))
    implementation(project(":core:people"))
    implementation(project(":core:economy"))
    implementation(project(":core:evolution"))
    testImplementation(libs.junit)
}
