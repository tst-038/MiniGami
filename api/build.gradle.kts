plugins {
    id("minigami.java-conventions")
}

dependencies {
    api(project(":core"))
    compileOnlyApi(libs.paper.api)
}
