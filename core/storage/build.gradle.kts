plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:civilization"))
    implementation(project(":core:history"))
    implementation(project(":core:people"))
    implementation(project(":core:economy"))
    implementation(project(":core:evolution"))
    implementation(project(":core:simulation"))
    testImplementation(project(":core:worldgen"))
    testImplementation(project(":core:society"))
    testImplementation(libs.junit)
}
