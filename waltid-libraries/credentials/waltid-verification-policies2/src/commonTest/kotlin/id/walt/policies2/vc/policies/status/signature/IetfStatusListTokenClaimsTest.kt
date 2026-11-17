package id.walt.policies2.vc.policies.status.signature

import id.walt.cose.CoseContentType
import id.walt.cose.CoseHeaders
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import kotlinx.serialization.cbor.CborElement
import kotlinx.serialization.cbor.CborFloat
import kotlinx.serialization.cbor.CborInteger
import kotlinx.serialization.cbor.CborMap
import kotlinx.serialization.cbor.CborString
import id.walt.policies2.vc.policies.status.model.StatusVerificationError
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.CborLabel
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IetfStatusListTokenClaimsTest {
    private val uri = "https://example.com/statuslists/1"
    private val now = 1_800_000_000L
    private val iat = now - 60

    @Test
    fun `jwt subject must be the referenced uri`() {
        val error = assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(
                buildJsonObject {
                    put("sub", "https://evil.example/list")
                    put("iat", iat)
                    put("exp", now + 60)
                },
                uri,
                now,
            )
        }
        assertEquals(
            "IETF status-list token subject does not match referenced status-list URI",
            error.message,
        )
    }

    @Test
    fun `jwt exp equal to now is expired and a missing exp is accepted`() {
        val expired = assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(
                buildJsonObject {
                    put("sub", uri)
                    put("iat", iat)
                    put("exp", now)
                },
                uri,
                now,
            )
        }
        assertEquals("IETF status-list token is expired", expired.message)

        IetfStatusListTokenClaims.validate(
            buildJsonObject {
                put("sub", uri)
                put("iat", iat)
            },
            uri,
            now,
        )
    }

    @Test
    fun `jwt nbf in the future is rejected`() {
        val error = assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(
                buildJsonObject {
                    put("sub", uri)
                    put("iat", iat)
                    put("nbf", now + 60)
                },
                uri,
                now,
            )
        }
        assertEquals("IETF status-list token is not yet valid", error.message)
    }

    @Test
    fun `cwt subject expiry and not-before follow the same rules`() {
        assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(cwt(sub = "https://evil.example/list", exp = now + 60), uri, now)
        }.also { assertEquals("IETF status-list token subject does not match referenced status-list URI", it.message) }

        assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(cwt(sub = uri, exp = now), uri, now)
        }.also { assertEquals("IETF status-list token is expired", it.message) }

        assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(cwt(sub = uri, exp = now + 60, nbf = now + 30), uri, now)
        }.also { assertEquals("IETF status-list token is not yet valid", it.message) }

        IetfStatusListTokenClaims.validate(cwt(sub = uri, exp = now + 60, nbf = now), uri, now)
    }

    @Test
    fun `jwt and cwt without iat are rejected`() {
        assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(
                buildJsonObject {
                    put("sub", uri)
                    put("exp", now + 60)
                },
                uri,
                now,
            )
        }.also { assertEquals("IETF status-list token has no iat", it.message) }

        assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(cwt(sub = uri, exp = now + 60, iat = null), uri, now)
        }.also { assertEquals("IETF status-list token has no iat", it.message) }
    }

    @Test
    fun `jwt typ is compared case-insensitively with an optional application prefix`() {
        IetfStatusListTokenClaims.requireJwtType(jwtWithTyp("application/StatusList+JWT"))

        assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.requireJwtType(jwtWithTyp("JWT"))
        }.also { assertEquals("IETF status-list JWT typ must be statuslist+jwt", it.message) }

        assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.requireJwtType(jwtWithHeader("""{"alg":"EdDSA"}"""))
        }.also { assertEquals("IETF status-list JWT typ must be statuslist+jwt", it.message) }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)
    @Test
    fun `cwt type must be protected header 16 as text or content format 279`() {
        IetfStatusListTokenClaims.requireCwtType(
            statusListCwt(CoseContentType.AsString("Application/statuslist+cwt")),
        )
        IetfStatusListTokenClaims.requireCwtType(statusListCwt(CoseContentType.AsInt(279)))

        listOf(
            "missing" to statusListCwt(type = null),
            "other media type" to statusListCwt(CoseContentType.AsString("application/cose")),
            "other integer" to statusListCwt(CoseContentType.AsInt(16)),
            "content type header" to statusListCwt(
                type = null,
                contentType = CoseContentType.AsString("application/statuslist+cwt"),
            ),
            "text typ alias" to cwtWithProtected(
                CborMap(mapOf(CborString("typ") to CborString("application/statuslist+cwt"))),
            ),
            "label 16 disagrees with typ alias" to cwtWithProtected(
                CborMap(
                    mapOf(
                        CborInteger(16) to CborString("application/cose"),
                        CborString("typ") to CborString("application/statuslist+cwt"),
                    ),
                ),
            ),
        ).forEach { (name, token) ->
            val error = assertFailsWith<StatusVerificationError>(name) {
                IetfStatusListTokenClaims.requireCwtType(token)
            }
            assertEquals("IETF status-list CWT type must be application/statuslist+cwt", error.message, name)
        }
    }

    @Test
    fun `jwt iat must be a finite json number`() {
        IetfStatusListTokenClaims.validate(
            buildJsonObject {
                put("sub", uri)
                put("iat", 1_700_000_000.5)
            },
            uri,
            now,
        )

        listOf("NaN", "1700000000").forEach { value ->
            val error = assertFailsWith<StatusVerificationError>(value) {
                IetfStatusListTokenClaims.validate(
                    buildJsonObject {
                        put("sub", uri)
                        put("iat", value)
                    },
                    uri,
                    now,
                )
            }
            assertEquals("IETF status-list token iat is not a number", error.message, value)
        }
    }

    @OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)
    @Test
    fun `cwt iat may be a fractional numeric date`() {
        IetfStatusListTokenClaims.validate(cwtIssuedAt(CborFloat(1_700_000_000.5)), uri, now)

        val error = assertFailsWith<StatusVerificationError> {
            IetfStatusListTokenClaims.validate(cwtIssuedAt(CborFloat(Double.NaN)), uri, now)
        }
        assertEquals("IETF status-list token iat is not a number", error.message)
    }

    private fun jwtWithTyp(typ: String): String = jwtWithHeader("""{"alg":"EdDSA","typ":"$typ"}""")

    private fun jwtWithHeader(header: String): String {
        val encode = { json: String -> base64Url.encode(json.encodeToByteArray()) }
        return "${encode(header)}.${encode("{}")}.${encode("signature")}"
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun statusListCwt(type: CoseContentType?, contentType: CoseContentType? = null): ByteArray =
        cwtWithProtectedBytes(
            coseCompliantCbor.encodeToByteArray(CoseHeaders(contentType = contentType, type = type)),
        )

    @OptIn(ExperimentalSerializationApi::class)
    private fun cwtWithProtected(header: CborMap): ByteArray =
        cwtWithProtectedBytes(coseCompliantCbor.encodeToByteArray(CborElement.serializer(), header))

    private fun cwtWithProtectedBytes(protected: ByteArray): ByteArray =
        CoseSign1(
            protected = protected,
            unprotected = CoseHeaders(),
            payload = byteArrayOf(),
            signature = byteArrayOf(0),
        ).toTagged()

    @OptIn(ExperimentalSerializationApi::class, ExperimentalUnsignedTypes::class)
    private fun cwtIssuedAt(iat: CborFloat): ByteArray =
        coseCompliantCbor.encodeToByteArray(
            CborElement.serializer(),
            CborMap(
                mapOf(
                    CborInteger(2) to CborString(uri),
                    CborInteger(6) to iat,
                ),
            ),
        )

    @OptIn(ExperimentalSerializationApi::class)
    private fun cwt(sub: String, exp: Long, nbf: Long? = null, iat: Long? = this.iat): ByteArray =
        coseCompliantCbor.encodeToByteArray(CwtClaims(sub = sub, exp = exp, nbf = nbf, iat = iat))

    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    private data class CwtClaims(
        @CborLabel(2) val sub: String,
        @CborLabel(4) val exp: Long,
        @CborLabel(5) val nbf: Long? = null,
        @CborLabel(6) val iat: Long? = null,
    )

    private companion object {
        val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    }
}
