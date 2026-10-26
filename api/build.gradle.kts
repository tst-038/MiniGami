plugins {
    id("minigami.java-conventions")
}

dependencies {
    api(project(":core"))
    compileOnlyApi(libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
