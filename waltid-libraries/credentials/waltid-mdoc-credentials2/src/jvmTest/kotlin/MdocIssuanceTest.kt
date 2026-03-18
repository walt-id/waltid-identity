@file:OptIn(ExperimentalSerializationApi::class)

import id.walt.cose.*
import id.walt.cose.JWKKeyCoseTransform.getCosePublicKey
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.presentations.formats.MsoMdocPresentation
import id.walt.crypto.keys.KeyManager
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.decodeFromBase64
import id.walt.mdoc.credsdata.DrivingPrivilege
import id.walt.mdoc.credsdata.Mdl
import id.walt.mdoc.credsdata.isoshared.IsoSexEnum
import id.walt.mdoc.encoding.ByteStringWrapper
import id.walt.mdoc.issuance.MdocIssuer
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.document.DeviceAuth
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.DeviceNameSpaces
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.schema.MdocsSchemaMappingFunction.jsonToCborElement
import id.walt.policies2.vp.policies.VPPolicyRunner
import id.walt.policies2.vp.policies.VPVerificationPolicyManager
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.time.Clock

class MdocIssuanceTest {

    companion object {

        val issuerKeyInit = suspend {
            KeyManager.resolveSerializedKey(
                """
           {
            "type": "jwk",
            "jwk": {
              "kty": "EC",
              "d": "-wSIL_tMH7-mO2NAfHn03I8ZWUHNXVzckTTb96Wsc1s",
              "crv": "P-256",
              "kid": "sW5yv0UmZ3S0dQuUrwlR9I3foREBHHFwXhGJGqGEVf0",
              "x": "Pzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5U",
              "y": "6dwhUAzKzKUf0kNI7f40zqhMZNT0c40O_WiqSLCTNZo"
            }
          }""".trimIndent()
            ) as JWKKey
        }

        val holderKeyInit = suspend {
            KeyManager.resolveSerializedKey(
                """
           {
            "type": "jwk",
            "jwk": {
              "kty": "EC",
              "d": "2STd0J5vD68K5FdxvK4SvgkumTr7shP0abiAmbRdgNk",
              "crv": "P-256",
              "kid": "holder",
              "x": "lcaMxDbsqZsDc-REGbONOCz7ghxVuk38wZ__8BNuF4c",
              "y": "rWK-j7daO07d1AwyhD2It6a1evaTwmoSs1p70PGu99M"
            }
          }""".trimIndent()
            ) as JWKKey
        }

        val issuerCert =
            listOf("MIICCTCCAbCgAwIBAgIUfqyiArJZoX7M61/473UAVi2/UpgwCgYIKoZIzj0EAwIwKDELMAkGA1UEBhMCQVQxGTAXBgNVBAMMEFdhbHRpZCBUZXN0IElBQ0EwHhcNMjUwNjAyMDY0MTEzWhcNMjYwOTAyMDY0MTEzWjAzMQswCQYDVQQGEwJBVDEkMCIGA1UEAwwbV2FsdGlkIFRlc3QgRG9jdW1lbnQgU2lnbmVyMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEPzp6eVSAdXERqAp8q8OuDEhl2ILGAaoaQXTJ2sD2g5Xp3CFQDMrMpR/SQ0jt/jTOqExk1PRzjQ79aKpIsJM1mqOBrDCBqTAfBgNVHSMEGDAWgBTxCn2nWMrE70qXb614U14BweY2azAdBgNVHQ4EFgQUx5qkOLC4lpl1xpYZGmF9HLxtp0gwDgYDVR0PAQH/BAQDAgeAMBoGA1UdEgQTMBGGD2h0dHBzOi8vd2FsdC5pZDAVBgNVHSUBAf8ECzAJBgcogYxdBQECMCQGA1UdHwQdMBswGaAXoBWGE2h0dHBzOi8vd2FsdC5pZC9jcmwwCgYIKoZIzj0EAwIDRwAwRAIgHTap3c6yCUNhDVfZWBPMKj9dCWZbrME03kh9NJTbw1ECIAvVvuGll9O21eR16SkJHHAA1pPcovhcTvF9fz9cc66M")
        val issuerCertCose = issuerCert.map { CoseCertificate(it.decodeFromBase64()) }


        /** Verification process: Simulate what a verifier would do */
        suspend fun verifyIssued(
            document: Document,
            docType: String,
            namespacesToCheck: List<String>,
            holderKey: CoseKey,
            issuerPublicCoseVerifier: CoseVerifier,
            namespaces: Map<String, JsonObject>
        ) {
            println("Verify issued...")

            // A verifier receives the `document`. First, they parse the MSO.
            val receivedIssuerAuth = document.issuerSigned.issuerAuth

            // A. Verify the COSE signature
            val isSignatureValid = receivedIssuerAuth.verify(issuerPublicCoseVerifier)
            check(isSignatureValid, { "COSE signature verification failed!" })
            println("COSE signature on MSO is valid.")

            val decodedMso = receivedIssuerAuth.decodeIsoPayload<MobileSecurityObject>()

            // Assert that the MSO content is as expected
            check("1.0" == decodedMso.version) { "Invalid version" }
            check(docType == decodedMso.docType) { "Doctype does not match" }
            check(holderKey == decodedMso.deviceKeyInfo.deviceKey) { "Holder key / device key does not match" }

            require(namespacesToCheck.isNotEmpty()) { "Error in code: Did not specify any namespaces to check" }

            namespacesToCheck.forEach { namespaceToCheck ->
                println("Checking namespace $namespaceToCheck...")
                // The core of the verification: check if the digests in the MSO match the received data.
                val receivedDigests = decodedMso.valueDigests[namespaceToCheck]!!.entries
                val receivedIssuerItems = document.issuerSigned.namespaces!![namespaceToCheck]!!.entries

                // check(issuerSignedItems.size == receivedDigests.size)

                // For each received item, calculate its digest and verify it matches the one in the MSO
                for (signedItemWrapper in receivedIssuerItems) {
                    val signedItem = signedItemWrapper.value
                    val expectedDigest = ValueDigest.fromIssuerSignedItem(signedItem, namespaceToCheck, decodedMso.digestAlgorithm)
                    val receivedDigest = receivedDigests.find { it.key == signedItem.digestId }

                    requireNotNull(receivedDigest, { "Digest with ID ${signedItem.digestId} not found in MSO" })
                    check(
                        expectedDigest.value.toHexString() == receivedDigest.value.toHexString(),
                        { "Digest for '${signedItem.elementIdentifier}' does not match!" }
                    )
                    println("Iterating ${signedItem.digestId} - ${signedItem.elementIdentifier}: Digest matches: ${receivedDigest.value.toHexString()} (${signedItem.elementValue} (${signedItem.elementValue::class.simpleName}))")
                }


                println("Successfully verified ${receivedIssuerItems.size} issuer-signed item digests against the MSO.")
                println("(MSO namespace $namespaceToCheck has ${receivedDigests.size} item digests)")
            }

            println("--- Running Verifier")

            val hexCred = coseCompliantCbor.encodeToHexString(document)

            val verificationResults = VPPolicyRunner.verifySpecificPresentation(
                presentation = MsoMdocPresentation(CredentialParser.parseOnly(hexCred) as MdocsCredential),
                policies = VPVerificationPolicyManager.defaultMsoMdocPolicies.filterNot { it.id == "mso_mdoc/device-auth" },
                verificationContext = null
            )

            verificationResults.forEach { (id, result) ->
                println("$id: ${result.success} - $result")
            }

            verificationResults.forEach { (id, result) -> require(result.success) { "Verification result for '${id}' failed!" } }

            val prettyJson = Json { prettyPrint = true }

            println("Original:")
            println(prettyJson.encodeToString(namespaces))

            println("Decoded:")
            val issuerSignedNamespacesJson = document.issuerSigned.namespacesToJson()
            println(prettyJson.encodeToString(issuerSignedNamespacesJson))

            val clearedNamespaces = namespaces.mapValues { (namespace, namespaceData) ->
                JsonObject(namespaceData.filterValues { it !is JsonNull })
            }

            require(JsonObject(clearedNamespaces) == issuerSignedNamespacesJson)
        }

        fun makeDocument(docType: String, issuerSigned: IssuerSigned): Document {
            val document = Document(
                docType = docType,
                issuerSigned = issuerSigned,
                deviceSigned = DeviceSigned(
                    ByteStringWrapper(DeviceNameSpaces(mapOf())),
                    DeviceAuth(deviceMac = CoseMac0(ByteArray(0), CoseHeaders(), ByteArray(0), ByteArray(0)))
                )
            )
            println("Document: $document")

            return document
        }
    }

    @Test
    fun testUniversalIssuance() = runTest {
        val issuerKey = issuerKeyInit()
        val holderKey = holderKeyInit().getPublicKey().getCosePublicKey()
        val issuerPublicKey = issuerKey.getPublicKey()
        val issuerPublicCoseVerifier = issuerPublicKey.toCoseVerifier()

        val docType = "org.waltid.123456.custom"
        val ns = "org.waltid.123456.custom.1"

        val exampleValueMappingFunction: (docType: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement) -> CborElement? =
            { docType: String, namespace: String, elementIdentifier: String, elementValueJson: JsonElement ->
                if (elementValueJson is JsonPrimitive) {
                    /*if (elementIdentifier.endsWith("date")) {
                        LocalDate.parse(elementValueJson.content)
                    } else when {
                        elementValueJson.isString -> elementValueJson.content
                        elementValueJson.intOrNull != null -> elementValueJson.int
                        elementValueJson.longOrNull != null -> elementValueJson.long
                        elementValueJson.doubleOrNull != null -> elementValueJson.double
                        elementValueJson.floatOrNull != null -> elementValueJson.float
                        elementValueJson.booleanOrNull != null -> elementValueJson.boolean
                        elementValueJson is JsonNull -> null
                        else -> TODO("1 Got: $elementValueJson (${elementValueJson::class.simpleName})")
                    }*/
                    elementValueJson.jsonToCborElement()
                } else {
                    TODO("2 Got: $elementValueJson (${elementValueJson::class})")
                }
            }

        val data = MdocIssuer.MdocUniversalIssuanceData(
            namespaces = mapOf(
                ns to buildJsonObject {
                    put("family_name", "Doe")
                    put("given_name", "John")
                    put("birth_date", "1986-03-22")
                    put("issue_date", "2019-10-20")
                    put("expiry_date", "2024-10-20")
                    put("issuing_country", "AT")
                    put("issuing_authority", "AT DMV")
                    put("document_number", "123456789")
                    put("height_cm", JsonPrimitive(180u))
                    put("explicit_null", null)
                },

                "org.waltid.ns2" to buildJsonObject {
                    put("a", false)
                    put("b", 1)
                    put("c", JsonPrimitive(2547483647u))
                    put("d", 30000000000L)
                })
        )

        val issuerSigned = MdocIssuer.issueUniversal(
            issuerKey = issuerKey,
            issuerCertificate = issuerCertCose,
            holderKey = holderKey,
            docType = docType,
            data = data,
            valueMappingFunction = exampleValueMappingFunction
        )

        val document = makeDocument(docType, issuerSigned)

        verifyIssued(
            document = document,
            docType = docType,
            issuerPublicCoseVerifier = issuerPublicCoseVerifier,
            namespacesToCheck = listOf(ns),
            holderKey = holderKey,
            namespaces = data.namespaces
        )

    }

    @Test
    fun testTypesafeIssuance() = runTest {
        val issuerKey = issuerKeyInit()
        val holderKey = holderKeyInit().getPublicKey().getCosePublicKey()
        val issuerPublicKey = issuerKey.getPublicKey()
        val issuerPublicCoseVerifier = issuerPublicKey.toCoseVerifier()

        val now = Clock.System.now()
        val today = now.toLocalDateTime(TimeZone.UTC).date

        val data = Mdl(
            familyName = "Mustermann",
            givenName = "Max",
            issueDate = today,
            expiryDate = today.plus(DatePeriod(years = 10)),
            portrait = byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10),
            sex = IsoSexEnum.MALE,
            height = 100u,
            weight = 200u,
            ageBirthYear = 2000u,
            ageOver18 = true,
            documentNumber = "AAABBBCCC123",
            drivingPrivileges = listOf(
                DrivingPrivilege("B", today)
            )
        )

        val issuerSigned = MdocIssuer.issueTypesafe(
            issuerKey = issuerKey,
            issuerCertificate = issuerCertCose,
            holderKey = holderKey,
            typesafeData = data
        )

        val document = makeDocument(data.docType, issuerSigned)

        val namespacesForVerification = data.toNamespaces()

        verifyIssued(
            document = document,
            docType = data.docType,
            issuerPublicCoseVerifier = issuerPublicCoseVerifier,
            namespacesToCheck = namespacesForVerification.keys.toList(),
            holderKey = holderKey,
            namespaces = data.toNamespacesJson()
        )
    }
}


