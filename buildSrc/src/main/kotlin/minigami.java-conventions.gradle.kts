// Shared setup for every module: Java 25, JUnit Platform, jar naming.
plugins {
    `java-library`
}

base {
    archivesName = "MiniGami-${project.name}"
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-Xlint:all")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
