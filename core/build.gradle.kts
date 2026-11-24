plugins {
    id("minigami.java-conventions")
    id("minigami.library")
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testRuntimeOnly(libs.sqlite.jdbc)
}

tasks.test {
    // The SQLite driver loads its native library.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
