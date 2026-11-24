plugins {
    id("minigami.java-conventions")
    alias(libs.plugins.run.paper)
}

dependencies {
    // MiniGami ships api and core and shares them through join-classpath, so nothing of ours is bundled.
    compileOnly(project(":api"))
    // Optional at runtime: placeholders only when PlaceholderAPI is installed.
    compileOnly(libs.placeholderapi) { isTransitive = false }

    testImplementation(project(":api"))
    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.sqlite.jdbc)
}

base {
    archivesName = "MiniGamiKoth"
}

tasks.test {
    // The SQLite driver loads its native library.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.processResources {
    val version = project.version.toString()
    inputs.property("version", version)
    filesMatching("paper-plugin.yml") { expand("version" to version) }
}

tasks.runServer {
    minecraftVersion("26.3")
    pluginJars(project(":paper").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    downloadPlugins { modrinth("placeholderapi", libs.versions.placeholderapi.get()) }
}
