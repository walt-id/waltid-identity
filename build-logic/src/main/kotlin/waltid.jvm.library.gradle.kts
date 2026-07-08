plugins {
    id("waltid.jvm.library.base")
}

kotlin {
    jvmToolchain(project.javaLibraryVersion)
}
