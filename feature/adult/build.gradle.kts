plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

val generatedPack = layout.buildDirectory.dir("generated/scenePackPngs")
val decodeAdultPack = tasks.register("decodeAdultPack") {
    val inputDir = file("src/main/resources-b64")
    inputs.dir(inputDir)
    outputs.dir(generatedPack)
    doLast {
        val outRoot = generatedPack.get().asFile
        outRoot.deleteRecursively()
        inputDir.walkTopDown().filter { it.isFile && it.name.endsWith(".png.b64") }.forEach { source ->
            val relative = source.relativeTo(inputDir).path.removeSuffix(".b64")
            val dest = outRoot.resolve(relative)
            dest.parentFile.mkdirs()
            dest.writeBytes(java.util.Base64.getDecoder().decode(source.readText().trim()))
        }
    }
}

sourceSets.named("main") {
    resources.srcDir(generatedPack)
}

tasks.named("processResources") { dependsOn(decodeAdultPack) }
tasks.named("compileTestKotlin") { dependsOn(decodeAdultPack) }

dependencies {
    implementation(project(":core:adult-contracts"))
    implementation(project(":core:scene"))
    testImplementation(libs.junit)
}
