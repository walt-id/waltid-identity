plugins {
    id("waltid.multiplatform.library.common")
}

kotlin {
    jvmToolchain(project.javaLibraryVersion)
    jvm()
}
