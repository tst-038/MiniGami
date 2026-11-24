plugins {
    id("minigami.java-conventions")
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
