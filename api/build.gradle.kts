plugins {
    id("minigami.java-conventions")
    `java-test-fixtures`
}

dependencies {
    api(project(":core"))
    compileOnlyApi(libs.paper.api)

    testFixturesImplementation(libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
