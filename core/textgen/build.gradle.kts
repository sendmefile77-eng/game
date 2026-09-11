plugins { alias(libs.plugins.kotlin.jvm) }
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":core:history"))
    implementation(project(":core:simulation"))
    testImplementation(libs.junit)
}
