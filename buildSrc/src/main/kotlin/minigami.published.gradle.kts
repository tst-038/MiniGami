// What other plugins compile against: published as io.github.tst-038:minigami-<module>.
plugins {
    `java-library`
    `maven-publish`
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("library") {
            artifactId = "minigami-${project.name}"
            from(components["java"])
        }
    }
}
