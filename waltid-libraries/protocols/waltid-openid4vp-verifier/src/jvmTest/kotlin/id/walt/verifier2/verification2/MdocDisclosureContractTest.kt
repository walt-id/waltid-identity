@file:OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)

package id.walt.verifier2.verification2

import id.walt.cose.Cose
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseKey
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.credentials.formats.MdocsCredential
import id.walt.dcql.models.ClaimsQuery
import id.walt.dcql.models.CredentialFormat
import id.walt.dcql.models.CredentialQuery
import id.walt.dcql.models.CredentialSetQuery
import id.walt.dcql.models.DcqlQuery
import id.walt.dcql.models.meta.MsoMdocMeta
import id.walt.mdoc.objects.DeviceSigned
import id.walt.mdoc.objects.elements.DeviceSignedItem
import id.walt.mdoc.objects.document.Document
import id.walt.mdoc.objects.document.IssuerSigned
import id.walt.mdoc.objects.elements.IssuerSignedItem
import id.walt.mdoc.objects.mso.DeviceKeyInfo
import id.walt.mdoc.objects.mso.MobileSecurityObject
import id.walt.mdoc.objects.mso.ValidityInfo
import id.walt.mdoc.objects.wrapInCborTag
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ByteArraySerializer
import kotlinx.serialization.cbor.CborString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Instant

/** Exercises query matching only; cryptographic presentation checks run separately before this step. */
class MdocDisclosureContractTest {
    private val namespace = "example.namespace"
    private val docType = "example.document"
    private val time = Instant.parse("2026-01-01T00:00:00Z")
    private val mso = MobileSecurityObject("1.0", "SHA-256", emptyMap(),
        DeviceKeyInfo(CoseKey(kty = Cose.KeyTypes.OKP)), docType, ValidityInfo(time, time, time))

    private fun credential(vararg names: String, signature: Byte = 1, deviceFields: Map<String, String> = emptyMap()): MdocsCredential {
        val issuerAuth = CoseSign1(byteArrayOf(), CoseHeaders(),
            coseCompliantCbor.encodeToByteArray(ByteArraySerializer(), coseCompliantCbor.encodeToByteArray(mso)).wrapInCborTag(24),
            byteArrayOf(signature))
        val document = Document(docType, IssuerSigned.fromIssuerSignedItems(
            mapOf(namespace to names.mapIndexed { index, name ->
                IssuerSignedItem(index.toUInt(), ByteArray(16), name, CborString(name))
            }), issuerAuth), deviceSigned = deviceFields.takeIf { it.isNotEmpty() }?.let { fields ->
                DeviceSigned.fromDeviceSignedItems(
                    mapOf(namespace to fields.map { DeviceSignedItem(it.key, it.value) }),
                    CoseSign1(byteArrayOf(), CoseHeaders(), null, byteArrayOf(1)),
                )
            })
        return MdocsCredential(buildJsonObject {
            put("docType", JsonPrimitive(docType))
            putJsonObject(namespace) { names.forEach { put(it, JsonPrimitive(it)) } }
        }, Base64.UrlSafe.encode(coseCompliantCbor.encodeToByteArray(document)), docType)
    }

    private fun query(id: String, vararg names: String) = CredentialQuery(
        id = id, format = CredentialFormat.MSO_MDOC, meta = MsoMdocMeta(doctypeValue = docType),
        claims = names.map { ClaimsQuery(pathStrings = listOf(namespace, it)) }.takeIf { it.isNotEmpty() },
    )

    @Test
    fun `requested claims are accepted`() {
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            DcqlQuery(listOf(query("q", "family_name"))), mapOf("q" to listOf(credential("family_name"))), true).isSuccess)
    }

    @Test
    fun `unrequested selectively disclosable claim is rejected`() {
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            DcqlQuery(listOf(query("q", "family_name"))), mapOf("q" to listOf(credential("family_name", "portrait"))), true).isFailure)
    }

    @Test
    fun `external readers retain required claim matching without OpenID4VP disclosure rules`() {
        val dcql = DcqlQuery(listOf(query("q", "family_name")))
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            dcql, mapOf("q" to listOf(credential("family_name", "portrait"))), true,
            enforceMdocDisclosure = false).isSuccess)
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            dcql, mapOf("q" to listOf(credential("portrait"))), true,
            enforceMdocDisclosure = false).isFailure)
    }

    @Test
    fun `no requested claims does not authorize every claim`() {
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            DcqlQuery(listOf(query("q"))), mapOf("q" to listOf(credential("portrait"))), true).isFailure)
    }

    @Test
    fun `same credential selected under another query may contain its requested claims`() {
        val presented = credential("family_name", "portrait")
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            DcqlQuery(listOf(query("name", "family_name"), query("photo", "portrait"))),
            mapOf("name" to listOf(presented), "photo" to listOf(presented)), true).isSuccess)
    }

    @Test
    fun `another credential does not authorize additional claims`() {
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            DcqlQuery(listOf(query("name", "family_name"), query("photo", "portrait"))),
            mapOf("name" to listOf(credential("family_name", "portrait")),
                "photo" to listOf(credential("portrait", signature = 2))), true).isFailure)
    }

    @Test
    fun `claim alternatives accept either requested element without requiring both`() {
        val alternatives = query("q", "family_name", "portrait").let { query ->
            query.copy(
                claims = query.claims!!.mapIndexed { index, claim -> claim.copy(id = "claim$index") },
                claimSets = listOf(listOf("claim0"), listOf("claim1")),
            )
        }
        for (name in listOf("family_name", "portrait")) {
            assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
                DcqlQuery(listOf(alternatives)), mapOf("q" to listOf(credential(name))), true).isSuccess)
        }
    }

    @Test
    fun `unselected credential alternative cannot authorize an extra disclosure`() {
        val dcql = DcqlQuery(
            credentials = listOf(query("name", "family_name"), query("photo", "portrait")),
            credentialSets = listOf(CredentialSetQuery(listOf(listOf("name"), listOf("photo")))),
        )
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            dcql, mapOf("photo" to listOf(credential("portrait"))), true).isSuccess)
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            dcql, mapOf("name" to listOf(credential("family_name", "portrait"))), true).isFailure)
    }

    @Test
    fun `multiple credentials must each respect the query disclosure boundary`() {
        val dcql = DcqlQuery(listOf(query("q", "family_name").copy(multiple = true)))
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            dcql, mapOf("q" to listOf(credential("family_name"), credential("family_name", signature = 2))), true).isSuccess)
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            dcql, mapOf("q" to listOf(credential("family_name"), credential("family_name", "portrait", signature = 2))), true).isFailure)
    }

    private val transactionData = listOf(Base64.UrlSafe.encode(
        """{"type":"$namespace","credential_ids":["q"]}""".encodeToByteArray(),
    ))

    @Test
    fun `requested transaction binding is permitted only in device signed data`() {
        val query = DcqlQuery(listOf(query("q", "family_name")))
        val proof = credential("family_name", deviceFields = mapOf("transaction_data_hash" to "hash", "transaction_data_hash_alg" to "sha-256"))
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            query, mapOf("q" to listOf(proof)), true, transactionData = transactionData).isSuccess)
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            query, mapOf("q" to listOf(proof)), true).isFailure)
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            query, mapOf("q" to listOf(credential("family_name", "transaction_data_hash"))), true, transactionData = transactionData).isFailure)
    }

    @Test
    fun `transaction metadata does not authorize other fields in its namespace`() {
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            DcqlQuery(listOf(query("q", "family_name"))),
            mapOf("q" to listOf(credential("family_name", deviceFields = mapOf("portrait" to "PII")))), true, transactionData = transactionData).isFailure)
    }

    @Test
    fun `Annex C requested elements retain their separate matching semantics`() {
        assertTrue(PresentationVerificationEngine.matchCredentialsToQueries(
            DcqlQuery(listOf(query("q", "family_name"))), mapOf("q" to listOf(credential("portrait"))), false).isSuccess)
    }
}
