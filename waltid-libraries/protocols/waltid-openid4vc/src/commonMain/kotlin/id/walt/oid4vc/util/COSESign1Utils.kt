package id.walt.oid4vc.util

import cbor.Cbor
import id.walt.cose.CoseKey
import id.walt.cose.CoseSign1
import id.walt.cose.coseCompliantCbor
import id.walt.cose.toCoseVerifier
import id.walt.crypto.keys.jwk.JWKKey
import id.walt.crypto.utils.Base64Utils.base64UrlDecode
import id.walt.mdoc.cose.COSESign1
import id.walt.mdoc.dataelement.ByteStringElement
import id.walt.mdoc.dataelement.MapElement
import id.walt.mdoc.dataelement.MapKey
import id.walt.oid4vc.data.ProofOfPossession.CWTProofBuilder.Companion.HEADER_LABEL_COSE_KEY
import kotlinx.serialization.decodeFromByteArray

object COSESign1Utils {
    suspend fun verifyCOSESign1Signature(token: String): Boolean = runCatching {
        val tokenBytes = token.base64UrlDecode()
        val coseSign1 = CoseSign1.fromTagged(tokenBytes)
        val holderKey = extractHolderKey(tokenBytes) ?: return@runCatching false

        coseSign1.verify(holderKey.toCoseVerifier())
    }.getOrDefault(false)

    private suspend fun extractHolderKey(tokenBytes: ByteArray): JWKKey? {
        val legacyCoseSign1 = Cbor.decodeFromByteArray<COSESign1>(tokenBytes)
        val rawCoseKey = legacyCoseSign1.decodeProtectedHeader().extractCoseKey()
            ?: legacyCoseSign1.decodeUnprotectedHeader()?.extractCoseKey()
            ?: return null

        val coseKey = coseCompliantCbor.decodeFromByteArray<CoseKey>(rawCoseKey)
        return JWKKey.importJWK(coseKey.toJWK().toString()).getOrThrow()
    }

    private fun COSESign1.decodeUnprotectedHeader(): MapElement? =
        data.getOrNull(1) as? MapElement

    private fun MapElement.extractCoseKey(): ByteArray? =
        (value[MapKey(HEADER_LABEL_COSE_KEY)] as? ByteStringElement)?.value
}
