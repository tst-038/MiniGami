// What other plugins compile against: published as io.github.tst-038:minigami-<module>.
plugins {
    `java-library`
    id("minigami.published")
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
