plugins {
    id("minigami.java-conventions")
    alias(libs.plugins.run.paper)
}

dependencies {
    implementation(project(":api"))
    implementation(project(":features"))
    implementation(project(":games:last-standing"))
    implementation(project(":games:build-battle"))
    implementation(project(":games:team-deathmatch"))
    implementation(project(":games:hide-and-seek"))
    // Optional at runtime: arenas are reset from schematics only when WorldEdit or FAWE is installed.
    compileOnly(libs.worldedit.bukkit) { isTransitive = false }
    compileOnly(libs.worldedit.core) { isTransitive = false }

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

base {
    archivesName = "MiniGami"
}

tasks.jar {
    // One plugin jar: bundle our own modules. Paper is compileOnly, so runtimeClasspath holds only them.
    // shortcut: plain jar merge, no relocation; switch to the Shadow plugin if we ever bundle third-party libraries.
    inputs.files(configurations.runtimeClasspath)
    from(configurations.runtimeClasspath.map { files -> files.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("paper-plugin.yml") { expand("version" to version) }
}

tasks.runServer {
    minecraftVersion("26.3")
    downloadPlugins {
        modrinth("fastasyncworldedit", libs.versions.fawe.get())
    }
}
