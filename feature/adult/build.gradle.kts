plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":core:adult-contracts"))
    implementation(project(":core:scene"))
    testImplementation(libs.junit)
}
