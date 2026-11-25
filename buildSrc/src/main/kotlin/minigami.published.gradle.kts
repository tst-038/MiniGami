// Publishes to the local Maven repository (publishToMavenLocal) and to GitHub Packages (publish, done by the
// release workflow). GitHub Packages takes the GitHubPackagesUsername and GitHubPackagesPassword Gradle properties,
// a token with write:packages.
plugins {
    `maven-publish`
}

publishing {
    repositories {
        maven("https://maven.pkg.github.com/tst-038/MiniGami") {
            name = "GitHubPackages"
            credentials(PasswordCredentials::class)
        }
    }
}
