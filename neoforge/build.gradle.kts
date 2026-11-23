plugins {
    alias(libs.plugins.moddevgradle)
}

// Put a repositories block here for neoforge-only dependencies that do not use modrinth maven.

dependencies {
    implementation(project.project(":common").sourceSets.getByName("main").output)

    // Required at development runtime; neither dependency is bundled into this add-on's jar.
    implementation("maven.modrinth:cobblemon:${rootProject.properties["cobblemon_neoforge_version_id"]}")
    runtimeOnly("maven.modrinth:kotlin-for-forge:${rootProject.properties["kotlin_for_forge_version_id"]}")
    // Cobblemon's event API (subscribe) uses Kotlin types; Kotlin for Forge provides them at runtime.
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:${libs.versions.kotlin.get()}")

    // Cobblemon Battle Plus for testing the Trainer Card integration (see gradle.properties).
    val battlePlusJar = newestBattlePlusJar("neoforge")
    if (battlePlusJar != null) {
        logger.lifecycle("Development runs use Cobblemon Battle Plus: ${battlePlusJar.name}")
        runtimeOnly(files(battlePlusJar))
        listOf(
            "dev.architectury:architectury-neoforge:${rootProject.properties["battle_plus_architectury_version"]}",
            "curse.maven:radical-cobblemon-trainers-api-1152792:${rootProject.properties["battle_plus_rctapi_neoforge_file_id"]}",
        ).forEach { runtimeOnly(it) { isTransitive = false } }
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

val parchmentVersion = libs.versions.parchment.get()
val mcVersion = rootProject.properties["minecraft_version"].toString()
val gameJvmXms = rootProject.properties["game_jvm_xms"].toString()
val gameJvmXmx = rootProject.properties["game_jvm_xmx"].toString()

neoForge {
    version = rootProject.properties["neoforge_version"].toString()

    parchment {
        minecraftVersion = mcVersion
        mappingsVersion = parchmentVersion
    }

    runs {
        val vmArgs = arrayOf(
            "-XX:+UseZGC",
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-XX:+AllowEnhancedClassRedefinition",
            "-Xms$gameJvmXms",
            "-Xmx$gameJvmXmx",
        )
        create("Client") {
            client()
            gameDirectory = rootProject.file("run/client/${mcVersion}")
            ideName = "NeoForge/Client" // match fabric names
            jvmArguments.addAll(*vmArgs)
        }
        // A second development client for local multiplayer tests (e.g. /pay between two players). It uses its own
        // game directory and player name, and a smaller heap so two clients and a server fit in memory together.
        create("Client2") {
            client()
            gameDirectory = rootProject.file("run/client2/${mcVersion}")
            ideName = "NeoForge/Client 2"
            programArguments.addAll("--username", "Player001")
            jvmArguments.addAll(
                "-XX:+UseZGC",
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+AllowEnhancedClassRedefinition",
                "-Xms$gameJvmXms",
                "-Xmx3G",
            )
        }
        create("Server") {
            server()
            gameDirectory = rootProject.file("run/server/${mcVersion}")
            ideName = "NeoForge/Server" // match fabric names
            jvmArguments.addAll(*vmArgs)
        }
        create("Data") {
            data()
            gameDirectory = rootProject.file("run/data/${mcVersion}")
            ideName = "NeoForge/Data" // match fabric names
            jvmArguments.addAll(*vmArgs)
        }
    }

    mods {
        create(rootProject.properties["mod_id"].toString()) {
            sourceSet(sourceSets.main.get())
        }
    }
}

tasks {
    jar {
        // add common code to jar
        val main = project.project(":common").sourceSets.main.get()
        from(main.output.classesDirs)
        from(main.output.resourcesDir)
    }

    named("compileTestJava").configure {
        enabled = false
    }

    // NeoGradle compiles the game, but we don't want to add our common code to the game's code
    val notNeoTask: (Task) -> Boolean = { !it.name.startsWith("neo") && !it.name.startsWith("compileService") }

    // add common code & Javadoc to built jars (except for NeoGradle jars)
    withType<JavaCompile>().matching(notNeoTask).configureEach {
        source(project(":common").sourceSets.main.get().allSource)
    }
    withType<Javadoc>().matching(notNeoTask).configureEach {
        source(project(":common").sourceSets.main.get().allSource)
    }

    withType<ProcessResources>().matching(notNeoTask).configureEach {
        // include common resources
        from(project(":common").sourceSets.main.get().resources)

        // the properties listed here can be used in the mods.toml
        val properties = listOf(
            "mc_versions_neo",
            "neo_loader_version_range",
            "mod_version",
            "mod_id",
            "mod_name",
            "mod_description",
            "mod_authors",
            "mod_license",
            "cobblemon_version"
        )

        // store a map of the properties so the configuration cache can be used
        val map = mutableMapOf<String, String>()
        properties.forEach { map[it] = rootProject.properties[it].toString() }
        inputs.property("property_map", map)

        filesMatching("META-INF/neoforge.mods.toml") {
            @Suppress("UNCHECKED_CAST") expand(inputs.properties["property_map"] as Map<String, String>)
        }
    }
}
