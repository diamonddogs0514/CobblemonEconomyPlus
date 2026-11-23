@file:Suppress("UnstableApiUsage")

import net.fabricmc.loom.task.RemapJarTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.fabricloom)
}

// Put a repositories block here for fabric-only dependencies that do not use modrinth maven.

dependencies.project(":common")

val mcVersion = rootProject.properties["minecraft_version"].toString()
val parchmentVersion = libs.versions.parchment.get()
val gameJvmXms = rootProject.properties["game_jvm_xms"].toString()
val gameJvmXmx = rootProject.properties["game_jvm_xmx"].toString()

dependencies {
    minecraft("com.mojang:minecraft:${mcVersion}")
    mappings(loom.layered {
        officialMojangMappings()
        parchment("org.parchmentmc.data:parchment-${mcVersion}:${parchmentVersion}@zip")
    })

    modImplementation("net.fabricmc:fabric-loader:${rootProject.properties["fabric_loader_version"]}")
    // This line can be removed if you don't need fabric api
    modImplementation("net.fabricmc.fabric-api:fabric-api:${rootProject.properties["fabric_api_version"]}")

    // Cobblemon bundles this for released builds, but Loom needs it explicitly on the dev runtime classpath.
    modRuntimeOnly("net.fabricmc:fabric-language-kotlin:${rootProject.properties["fabric_language_kotlin_version"]}")
    listOf(
        "org.graalvm.js:js:${rootProject.properties["cobblemon_graal_version"]}",
        "org.graalvm.sdk:graal-sdk:${rootProject.properties["cobblemon_graal_version"]}",
        "org.graalvm.regex:regex:${rootProject.properties["cobblemon_graal_version"]}",
        "org.graalvm.truffle:truffle-api:${rootProject.properties["cobblemon_graal_version"]}",
        "org.mongodb:mongodb-driver-core:${rootProject.properties["cobblemon_mongodb_version"]}",
        "org.mongodb:mongodb-driver-sync:${rootProject.properties["cobblemon_mongodb_version"]}",
    ).forEach(::modRuntimeOnly)
    minecraftServerLibraries("com.ibm.icu:icu4j:${rootProject.properties["cobblemon_icu4j_version"]}")

    implementation(project.project(":common").sourceSets.getByName("main").output)

    // Required at development runtime; not bundled into this add-on's jar.
    modImplementation("maven.modrinth:cobblemon:${rootProject.properties["cobblemon_fabric_version_id"]}")

    // Optional: %cobblemon_economy_plus:...% placeholders when Text Placeholder API is installed.
    val placeholderApi = "eu.pb4:placeholder-api:${rootProject.properties["placeholder_api_version"]}"
    modCompileOnly(placeholderApi) { isTransitive = false }
    modLocalRuntime(placeholderApi) { isTransitive = false }

    // Cobblemon Battle Plus for testing the Trainer Card integration (see gradle.properties).
    val battlePlusJar = newestBattlePlusJar("fabric")
    if (battlePlusJar != null) {
        logger.lifecycle("Development runs use Cobblemon Battle Plus: ${battlePlusJar.name}")
        modLocalRuntime(files(battlePlusJar))
        listOf(
            "dev.architectury:architectury-fabric:${rootProject.properties["battle_plus_architectury_version"]}",
            "curse.maven:radical-cobblemon-trainers-api-1152792:${rootProject.properties["battle_plus_rctapi_fabric_file_id"]}",
            "me.lucko:fabric-permissions-api:${rootProject.properties["battle_plus_fabric_permissions_api_version"]}",
        ).forEach { modLocalRuntime(it) { isTransitive = false } }
    }
}

/** The newest Cobblemon Battle Plus jar built in battle_plus_dir, or null when disabled or not built. */
fun newestBattlePlusJar(loader: String): File? {
    if (!rootProject.properties["dev_battle_plus"].toString().toBoolean()) {
        return null
    }
    val dir = rootProject.file(rootProject.properties["battle_plus_dir"].toString()).resolve("build/libs_$loader")
    val jar = dir.listFiles { file -> Regex("cobblemonbattleplus-$loader-[0-9.]+-[0-9.]+\\.jar").matches(file.name) }
        ?.maxByOrNull { it.lastModified() }
    if (jar == null) {
        logger.warn("No Cobblemon Battle Plus jar in $dir; development runs start without it")
    }
    return jar
}

loom {
    mixin.useLegacyMixinAp = false

    runs {
        val vmArgs = arrayOf(
            "-XX:+UseZGC",
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-XX:+AllowEnhancedClassRedefinition",
            "-Xms$gameJvmXms",
            "-Xmx$gameJvmXmx"
        )
        named("client") {
            client()
            runDir("../run/client/${mcVersion}")
            configName = "Fabric/Client"
            ideConfigGenerated(true)
            // Keep the development player's offline UUID stable between launches.
            programArgs("--username", "Player000")
            vmArgs(*vmArgs)
        }
        // A second development client for local multiplayer tests (e.g. /pay between two players). It uses its own
        // game directory and player name, and a smaller heap so two clients and a server fit in memory together.
        create("client2") {
            client()
            runDir("../run/client2/${mcVersion}")
            configName = "Fabric/Client 2"
            ideConfigGenerated(true)
            programArgs("--username", "Player001")
            vmArgs(
                "-XX:+UseZGC",
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+AllowEnhancedClassRedefinition",
                "-Xms$gameJvmXms",
                "-Xmx3G"
            )
        }
        named("server") {
            server()
            serverWithGui()
            runDir("../run/server/${mcVersion}")
            configName = "Fabric/Server"
            ideConfigGenerated(true)
            vmArgs(*vmArgs)
        }
    }

    // include access wideners from common
    accessWidenerPath = project(":common").loom.accessWidenerPath
}

tasks {
    withType<JavaCompile> {
        // include common code in compiled jar
        source(project(":common").sourceSets.main.get().allSource)
    }

    // put all artifacts in the right directory
    withType<Jar> {
        destinationDirectory = rootDir.resolve("build").resolve("libs_fabric")
    }
    withType<RemapJarTask> {
        destinationDirectory = rootDir.resolve("build").resolve("libs_fabric")
    }

    // add common javadoc to jar
    javadoc { source(project(":common").sourceSets.main.get().allJava) }

    processResources {
        // add common resources to jar
        from(project(":common").sourceSets.main.get().resources)

        // the properties listed here can be used in the fabric.mod.json
        val properties = listOf(
            "mc_versions_fabric", "mod_version", "mod_id", "mod_name", "mod_description", "mod_authors", "mod_license",
            "cobblemon_version"
        )

        val map = mutableMapOf<String, String>()
        properties.forEach { map[it] = rootProject.properties[it].toString() }
        inputs.property("property_map", map)

        filesMatching("fabric.mod.json") {
            @Suppress("UNCHECKED_CAST") expand(inputs.properties["property_map"] as Map<String, String>)
        }
    }

    named("compileTestJava").configure {
        enabled = false
    }

    named("test").configure {
        enabled = false
    }
}
