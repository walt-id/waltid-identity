plugins {
    val kotlinVersion = "1.9.0"
    kotlin("multiplatform") version kotlinVersion
    application
}

group = "id.walt.did"

repositories {
    mavenCentral()
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

application {
    mainClass.set("id.walt.did.DidMainKt")
}

kotlin {

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8" // JVM got Ed25519 at version 15
        }
        withJava()
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
    }
    sourceSets {

        val jvmMain by getting {

            dependencies {

            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        all {
            languageSettings.enableLanguageFeature("InlineClasses")
        }
    }

}
