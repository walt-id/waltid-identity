package id.walt.policies2.policies

import id.walt.credentials.formats.DigitalCredential
import id.walt.vical.Vical
import id.walt.x509.CertificateDer
import id.walt.x509.validateCertificateChain
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

// Call with https://vical.dts.aamva.org/vical/vc/vc-2025-09-27-1758957681255
@SerialName("vical")
@Serializable
class VicalPolicy(val vical: String) : VerificationPolicy2() {
    private val httpClientTimeout: Long = 3_000L

    //FIX val client = getClient() // should be provided by each platform -> defaultHttpClient()
    fun getClient() = HttpClient()

    override val id = "vical"

    override suspend fun verify(credential: DigitalCredential): Result<JsonElement> {

        try {

            // Verification steps
            // Prerequisite: The VICAL got validated beforehand
            val vical = readVical()

            // 1.) Use issuer certificates from VIAL as TrustAnchors,and validate certificate chain

            val trustAnchors = mutableListOf<CertificateDer>()

            vical.vicalData.certificateInfos.iterator().forEach { trustAnchors.add(CertificateDer(it.certificate)) }

            credential.verify(credential.getIssuerKey()!!) // FIX: should this work?

            val leaf = CertificateDer(
                credential.getIssuerKey()!!.getPublicKeyRepresentation()
            ) // FIX: we need the certificate here

            val chain = listOf(
                CertificateDer(
                    credential.getIssuerKey()!!.getPublicKeyRepresentation()
                )
            ) // FIX: we need the x5c chain here

            validateCertificateChain(leaf, chain, trustAnchors, false)

            // 2.) Validate the credentials signature with the trusted key from the issuer

            credential.verify(credential.getIssuerKey()!!) // FIX: Correct?

            // 3.) IACA must be valid for credential type

            return Result.success(JsonPrimitive(true))
        } catch (e: Throwable) {
            return Result.failure(e)
        }

    }

    private suspend fun readVical(): Vical {

        val vicalBinary = fetchBinaryFile(vical)

        println("Successfully read ${vicalBinary.size} bytes")

        val vical = Vical.decode(vicalBinary)

        return vical
    }

    suspend fun fetchBinaryFile(url: String): ByteArray {
        return getClient().get(url) {
            timeout {
                requestTimeoutMillis = httpClientTimeout
                connectTimeoutMillis = httpClientTimeout
                socketTimeoutMillis = httpClientTimeout
            }
        }.body<ByteArray>()
    }
}