plugins {
    id("waltid.multiplatform.library")
    id("waltid.publish.maven")
}

group = "id.walt.library-commons"

kotlin {
    js(IR) {
        outputModuleName = "library-commons"
    }

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
            implementation(identityLibs.kotlinx.coroutines.test)
        }
        jvmMain.dependencies {

        }
        jvmTest.dependencies {

        }
        jsMain.dependencies {

        }
        jsTest.dependencies {
            implementation(kotlin("test-js"))
        }
    }
}

mavenPublishing {
    pom {
        name.set("walt.id library commons")
        description.set("walt.id library commons")
    }
}
