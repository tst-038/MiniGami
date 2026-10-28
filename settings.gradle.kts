plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "MiniGami"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.enginehub.org/repo/")
    }
}

include("core", "api", "features", "games:last-standing", "games:build-battle", "games:team-deathmatch", "games:hide-and-seek", "games:king-of-the-hill", "paper")
