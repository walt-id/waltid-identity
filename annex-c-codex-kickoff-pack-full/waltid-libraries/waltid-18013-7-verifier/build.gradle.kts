plugins {
    kotlin("jvm")
}

dependencies {
    testImplementation(kotlin("test"))
    // Used by the test harness to load ANNEXC-REAL-001.json
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
}
