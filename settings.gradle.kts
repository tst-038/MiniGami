plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "MiniGami"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.enginehub.org/repo/")
        maven("https://repo.extendedclip.com/releases/")
    }
}

include("core", "api", "features", "stats", "gamenight", "games:last-standing", "games:build-battle", "games:team-deathmatch", "games:hide-and-seek", "games:domination", "games:the-wall", "games:shifting-maze", "paper")
