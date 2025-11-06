package id.walt.credentials

import id.walt.credentials.examples.SdJwtExamples
import id.walt.credentials.signatures.sdjwt.SdJwtSelectiveDisclosure
import id.walt.credentials.signatures.sdjwt.SelectivelyDisclosableVerifiableCredential
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test

class SdJwtCredentialsTest {

    @Test
    fun checkSdDisclosureParsing() = runTest {
        val example = SdJwtExamples.sdJwtVcSignedExample2

        val (detection, vc) = CredentialParser.detectAndParse(example)
        println("Detected: $detection")
        println("VC: $vc")
        check(vc is SelectivelyDisclosableVerifiableCredential)

        val disclosures = """
        ["IdBnglHp_TDdryL2pbKeSA","sub","1004"]
        ["CwIX5MurPLgUEFp6Sba3Gg","given_name","Inga"]
        ["oyF-Gt9-upkQdSFL_JSzCg","family_name","Silverstone"]
        ["LdohJ49wh0q0nmcIPovIXg","birthdate","1991-11-06"]
        """.trimIndent().lines().map { Json.decodeFromString<JsonArray>(it) }
        val vcDisclosures = vc.disclosures!!.map { it.asJsonArray() }

        fun List<JsonArray>.sortedByName() = sortedBy { it[1].jsonPrimitive.content }
        check(disclosures.sortedByName() == vcDisclosures.sortedByName()) { "Expected disclosures != parsed disclosures" }


        val selectedDisclosures = disclosures.map { SdJwtSelectiveDisclosure(it) }
        val disclosedVc = vc.disclose(vc, selectedDisclosures)
        check(example.removeSuffix("~") == disclosedVc.removeSuffix("~")) { "disclosed $disclosedVc != example $example" }
    }
}
