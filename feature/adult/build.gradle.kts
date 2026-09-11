plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

val generatedPack = layout.buildDirectory.dir("generated/scenePackPngs")
val generateAdultPack = tasks.register<JavaExec>("generateAdultPack") {
    group = "build"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.sendmefile77.chronosphere.adult.AdultPackGenerator")
    args(generatedPack.get().asFile.absolutePath)
    outputs.dir(generatedPack)
    dependsOn(tasks.named("compileKotlin"))
}

sourceSets.named("main") {
    resources.srcDir(generatedPack)
}

tasks.named("processResources") { dependsOn(generateAdultPack) }
tasks.named("processTestResources") { dependsOn(generateAdultPack) }

dependencies {
    implementation(project(":core:adult-contracts"))
    implementation(project(":core:scene"))
    testImplementation(libs.junit)
}
