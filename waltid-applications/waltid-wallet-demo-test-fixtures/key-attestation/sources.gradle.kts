import groovy.json.JsonSlurper
import org.gradle.api.file.Directory

// Private source sharing for demos and integration fixtures; nothing is added to SDK main sources.
val fixtureRoot = extra["demoKeyAttestationFixture"] as Directory
val profileFile = fixtureRoot.file(
    "../../waltid-wallet-demo-shared-ios/Sources/WalletDemoSharingUI/Resources/KeyAttestationProfiles.json",
)
val generatedDirectory = layout.buildDirectory.dir("generated/demoKeyAttestation/kotlin")
val generateProfiles = tasks.register("generateDemoKeyAttestationProfiles") {
    inputs.file(profileFile)
    outputs.dir(generatedDirectory)
    doLast {
        val json = inputs.files.singleFile.readText()
        val profiles = JsonSlurper().parseText(json) as Map<*, *>
        require(profiles.keys == setOf("itb", "eudi")) { "Only ITB and EUDI demo attesters are supported" }
        val itb = profiles["itb"] as Map<*, *>
        val eudi = profiles["eudi"] as Map<*, *>
        require(itb["algorithm"] == "ES256" && eudi["signing_algorithms"] == listOf("ES256")) {
            "The demo signing adapters require ES256"
        }
        require(itb["jwt_type"] == "key-attestation+jwt")
        require((itb["lifetime_seconds"] as Number).toLong() > 0)
        require((itb["status_lifetime_seconds"] as Number).toLong() > 0)
        val escaped = json.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n").replace("$", "\\$")
        val output = outputs.files.singleFile.resolve("id/walt/walletdemo/attestation/DemoKeyAttestationProfileData.kt")
        output.parentFile.mkdirs()
        output.writeText("package id.walt.walletdemo.attestation\n\ninternal const val DEMO_KEY_ATTESTATION_PROFILES_JSON = \"$escaped\"\n")
    }
}
extra["demoKeyAttestationSources"] = files(fixtureRoot.dir("kotlin"), generateProfiles)
