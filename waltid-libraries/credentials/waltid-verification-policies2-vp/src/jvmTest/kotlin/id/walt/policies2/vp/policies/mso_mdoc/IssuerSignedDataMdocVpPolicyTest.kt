@file:Suppress("PackageDirectoryMismatch")

package id.walt.policies2.vp.policies

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseKey
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.credentials.examples.MdocsExamples
import id.walt.mdoc.objects.digest.ValueDigest
import id.walt.mdoc.objects.digest.ValueDigestList
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.verifier.openid.models.openid.OpenID4VPResponseMode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

class IssuerSignedDataMdocVpPolicyTest {

    @Test
    fun shouldSucceedsWhenDigestMatches() = runTest {
        val namespace = "eu.europa.ec.eudi.pid.1"
        val elementId = "given_name"

        val item = IssuerSignedItem(
            digestId = 1u,
            random = ByteArray(16) { 0x01 },
            elementIdentifier = elementId,
            elementValue = "Inga",
        )

        val policy = IssuerSignedDataMdocVpPolicy()

        val mso = dummyMsoWithDigests(
            docType = "eu.europa.ec.eudi.pid.1",
            digestAlgorithm = "SHA-256",
            digests = mapOf(namespace to listOf(ValueDigest.fromIssuerSignedItem(item, namespace, "SHA-256"))),
        )

        val document = Document(
            docType = "eu.europa.ec.eudi.pid.1",
            issuerSigned = IssuerSigned.fromIssuerSignedItems(
                namespacedItems = mapOf(namespace to listOf(item)),
                issuerAuth = dummyIssuerAuth(),
            ),
            deviceSigned = null,
        )

        val result = policy.runPolicy(document, mso, dummyVerificationContext())

        assertTrue(result.success)
        val matchingDigest = result.results["matching_digest"]?.jsonObject
        assertNotNull(matchingDigest)
        val ids = matchingDigest[namespace]?.jsonArray
        assertNotNull(ids)
        assertTrue(JsonPrimitive(elementId) in ids)
    }

    @Test
    fun shouldFailWhenDigestMismatchesForPrimitive() = runTest {
        val namespace = "eu.europa.ec.eudi.pid.1"
        val elementId = "given_name"

        val item = IssuerSignedItem(
            digestId = 1u,
            random = ByteArray(16) { 0x01 },
            elementIdentifier = elementId,
            elementValue = "Inga",
        )
        val correctDigest = ValueDigest.fromIssuerSignedItem(item, namespace, "SHA-256")
        val wrongDigestBytes = correctDigest.value.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        val policy = IssuerSignedDataMdocVpPolicy()
        val mso = dummyMsoWithDigests(
            docType = "eu.europa.ec.eudi.pid.1",
            digestAlgorithm = "SHA-256",
            digests = mapOf(namespace to listOf(ValueDigest(correctDigest.key, wrongDigestBytes))),
        )
        val document = Document(
            docType = "eu.europa.ec.eudi.pid.1",
            issuerSigned = IssuerSigned.fromIssuerSignedItems(
                namespacedItems = mapOf(namespace to listOf(item)),
                issuerAuth = dummyIssuerAuth(),
            ),
            deviceSigned = null,
        )

        val result = policy.runPolicy(document, mso, dummyVerificationContext())

        assertFalse(result.success)
        assertTrue(result.errors.isNotEmpty())
        assertEquals("IllegalArgumentException", result.errors.first().error)
    }

    @OptIn(ExperimentalEncodingApi::class, ExperimentalSerializationApi::class)
    @Test
    fun shouldNotFailWhenNonPrimitivesMismatches() = runTest {
        val namespace = "org.iso.18013.5.1"
        val targetElementId = "driving_privileges"

        val documentBytes = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
            .decode(MdocsExamples.mdocsExampleBase64Url.replace("\\s+".toRegex(), ""))

        val exampleDocument = coseCompliantCbor.decodeFromByteArray<Document>(documentBytes)

        val targetItem = exampleDocument.issuerSigned.namespaces
            ?.get(namespace)
            ?.entries
            ?.firstOrNull { it.value.elementIdentifier == targetElementId }
            ?: error("mdoc example did not contain $targetElementId in $namespace")

        val item = targetItem.value
        val correctDigest = ValueDigest.fromIssuerSignedItem(item, namespace, "SHA-256")
        val tamperedDigest = correctDigest.value.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }

        val document = Document(
            docType = exampleDocument.docType,
            issuerSigned = IssuerSigned.fromIssuerSignedItems(
                namespacedItems = mapOf(namespace to listOf(item)),
                issuerAuth = dummyIssuerAuth(),
            ),
            deviceSigned = null,
        )

        val tamperedMso = dummyMsoWithDigests(
            docType = exampleDocument.docType,
            digestAlgorithm = "SHA-256",
            digests = mapOf(namespace to listOf(ValueDigest(correctDigest.key, tamperedDigest))),
        )

        val policy = IssuerSignedDataMdocVpPolicy()
        val result = policy.runPolicy(document, tamperedMso, dummyVerificationContext())

        assertTrue(result.success)

        val unmatchedNonPrimitive = result.results["unmatched_non_primitive"]?.jsonObject
        assertNotNull(unmatchedNonPrimitive)

        val ids = unmatchedNonPrimitive[namespace]?.jsonArray
        assertNotNull(ids)
        assertTrue(JsonPrimitive(targetElementId) in ids)
    }

    @OptIn(ExperimentalTime::class)
    private fun dummyMsoWithDigests(
        docType: String,
        digestAlgorithm: String,
        digests: Map<String, List<ValueDigest>>,
    ): MobileSecurityObject {
        val now = Clock.System.now()

        return MobileSecurityObject(
            version = "1.0",
            digestAlgorithm = digestAlgorithm,
            valueDigests = digests.mapValues { (_, entries) -> ValueDigestList(entries) },
            deviceKeyInfo = DeviceKeyInfo(
                deviceKey = CoseKey(
                    kty = Cose.KeyTypes.OKP,
                    crv = Cose.EllipticCurves.Ed25519,
                    x = ByteArray(32) { 0x02 },
                )
            ),
            docType = docType,
            validityInfo = ValidityInfo(
                signed = now,
                validFrom = now,
                validUntil = now + 1.days,
            )
        )
    }

    private fun dummyIssuerAuth(): CoseSign1 =
        CoseSign1(
            protected = byteArrayOf(),
            unprotected = CoseHeaders(),
            payload = byteArrayOf(0x00),
            signature = byteArrayOf(),
        )

    private fun dummyVerificationContext() = VerificationSessionContext(
        vpToken = "vp_token",
        expectedNonce = "nonce",
        expectedAudience = null,
        expectedOrigins = null,
        responseUri = null,
        responseMode = OpenID4VPResponseMode.DIRECT_POST,
        isSigned = true,
        isEncrypted = false,
        jwkThumbprint = null,
        isAnnexC = false,
        customData = null
    )
}
