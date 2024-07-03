listOf("iosArm64", "iosSimulatorArm64").forEach { target ->

    tasks.register<Exec>("implementation-$target-pod-install") {
        group = "build"
        workingDir(projectDir)
        commandLine(
            "env","pod", "install"
        )
        inputs.files(
            file("$projectDir/Podfile")
        )
        outputs.files(
            fileTree("$projectDir/Pods")
        )
    }

    tasks.register<Exec>("implementation-$target") {
        group = "build"

        this.dependsOn("implementation-$target-pod-install")

        val sdk = when (target) {
            "iosArm64" -> "iphoneos"
            else -> "iphonesimulator"
        }
        val archs = "arm64"

        commandLine(
            "xcodebuild",
            "-workspace", "waltid.crypto.ios.utils.xcworkspace",
            "-scheme", "waltid.crypto.ios.utils",
            "-sdk", sdk,
            "-derivedDataPath", "DerivedData-$target-$archs",
            "-configuration", "Release",
            "archive",
            "TARGET=$target",
            "ARCHS=$archs"
        )

        workingDir(projectDir)

        inputs.files(
            fileTree("$projectDir/waltid.crypto.ios.utils.xcodeproj") { exclude("**/xcuserdata") },
            fileTree("$projectDir/waltid.crypto.ios.utils")
        )
        outputs.files(
            fileTree("$projectDir/build/$target/Release-${sdk}")
        )
    }
}

tasks.create<Delete>("clean") {
    group = "build"

    delete(
        "$projectDir/build",
        "$projectDir/DerivedData-iosArm64-arm64",
        "$projectDir/DerivedData-iosSimulatorArm64-arm64"
    )
}
