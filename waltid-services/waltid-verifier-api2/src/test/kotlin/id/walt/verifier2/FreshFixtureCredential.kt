package id.walt.verifier2

import id.walt.crypto.keys.jwk.JWKKey
import id.walt.credentials.CredentialParser
import id.walt.credentials.formats.DigitalCredential
import id.walt.credentials.formats.MdocsCredential
import id.walt.credentials.formats.SdJwtCredential
import id.walt.verifier2.mdocs.mdlFixtureCredential
import id.walt.verifier2.mdocs.releaseTestIssuer
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

// Keep the fixture claims and disclosures, but issue fresh signatures and validity periods.
internal fun freshFixtureCredential(credential: DigitalCredential): DigitalCredential = runBlocking {
    when (credential) {
        is MdocsCredential -> mdlFixtureCredential(
            JsonObject(credential.credentialData + ("docType" to JsonPrimitive(credential.docType))).toString(),
            credential.getHolderKey() as JWKKey,
        )
        is SdJwtCredential -> {
            val now = Clock.System.now()
            val payload = JsonObject(requireNotNull(credential.originalCredentialData) + mapOf(
                "iat" to JsonPrimitive(now.epochSeconds),
                "nbf" to JsonPrimitive((now - 1.days).epochSeconds),
                "exp" to JsonPrimitive((now + 30.days).epochSeconds),
            ))
            val (signer, chain) = releaseTestIssuer
            val jwt = signer.signJws(payload.toString().encodeToByteArray(), mapOf(
                "typ" to JsonPrimitive("dc+sd-jwt"),
                "x5c" to JsonArray(chain.map { JsonPrimitive(Base64.Default.encode(it.rawBytes)) }),
            ))
            val disclosures = credential.disclosures.orEmpty().joinToString("~") { it.encoded }
            CredentialParser.parseOnly("$jwt~$disclosures~")
        }
        else -> credential
    }
}
