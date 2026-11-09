@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.fabricloom)
}

// you can put a repositories block here if you need common dependencies from other sources than modrinth

val mcVersion = rootProject.properties["minecraft_version"].toString()
val parchmentVersion = libs.versions.parchment.get()

dependencies {
    minecraft("com.mojang:minecraft:${mcVersion}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${mcVersion}:${parchmentVersion}@zip")
    })

    // mixin extras is included by default in both fabric and neoforge (no additional dependency required)
    val mixinExtras = "io.github.llamalad7:mixinextras-common:0.3.5"
    compileOnly(annotationProcessor(mixinExtras)!!)

    compileOnly("net.fabricmc:sponge-mixin:0.15.3+mixin.0.8.7")
    modImplementation("net.fabricmc:fabric-loader:${rootProject.properties["fabric_loader_version"]}")

    // Compile shared code against Cobblemon's Fabric artifact. The public API is
    // shared by both loader builds; each loader module supplies its own runtime jar.
    modCompileOnly("maven.modrinth:cobblemon:${rootProject.properties["cobblemon_fabric_version_id"]}")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
}

loom {
    // If you need to add access wideners, put the path here
    /** IMPORTANT: these will get added to fabric automatically, but since forge uses a different system      *
    /   (access transformers), make sure to create access transformers in the neoforge submodule if necessary */
    // accessWidenerPath = file("src/main/resources/NAME.accesswidener")

    mixin {
        useLegacyMixinAp = false
    }
}

// don't generate jar files for the common code
tasks {
    jar { enabled = false }
    remapJar { enabled = false }
    test {
        useJUnitPlatform()
    }
}
