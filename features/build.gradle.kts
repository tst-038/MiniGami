plugins {
    id("minigami.java-conventions")
}

dependencies {
    implementation(project(":api"))

    testImplementation(testFixtures(project(":api")))
    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
