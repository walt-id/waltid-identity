package id.walt.policies2.vc.policies.status.signature

import id.walt.cose.coseCompliantCbor
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
    }

    private fun jwtWithTyp(typ: String): String {
        val encode = { json: String -> base64Url.encode(json.encodeToByteArray()) }
        return "${encode("""{"alg":"EdDSA","typ":"$typ"}""")}.${encode("{}")}.${encode("signature")}"
    }

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
