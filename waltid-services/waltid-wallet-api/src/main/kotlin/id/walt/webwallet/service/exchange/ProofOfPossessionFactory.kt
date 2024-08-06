package id.walt.webwallet.service.exchange

import COSE.OneKey
import com.nimbusds.jose.jwk.ECKey
import id.walt.did.dids.DidService
import id.walt.oid4vc.data.CredentialOffer
import id.walt.oid4vc.data.OfferedCredential
import id.walt.oid4vc.data.ProofOfPossession
import id.walt.oid4vc.data.ProofType
import id.walt.webwallet.service.oidc4vc.TestCredentialWallet

object ProofOfPossessionFactory {
  suspend fun new(
    useKeyProof: Boolean,
    credentialWallet: TestCredentialWallet,
    offeredCredential: OfferedCredential,
    credentialOffer: CredentialOffer,
    nonce: String?
  ): ProofOfPossession = when (useKeyProof) {
    true -> keyProofOfPossession(credentialWallet, offeredCredential, credentialOffer, nonce)
    false -> didProofOfPossession(credentialWallet, offeredCredential, credentialOffer, nonce)
  }

  private fun didProofOfPossession(
    credentialWallet: TestCredentialWallet,
    offeredCredential: OfferedCredential,
    credentialOffer: CredentialOffer,
    nonce: String?
  ) = credentialWallet.generateDidProof(
    did = credentialWallet.did,
    issuerUrl = credentialOffer.credentialIssuer,
    nonce = nonce,
    proofType = offeredCredential.proofTypesSupported?.keys?.first() ?: ProofType.jwt
  )

  private suspend fun keyProofOfPossession(
    credentialWallet: TestCredentialWallet,
    offeredCredential: OfferedCredential,
    credentialOffer: CredentialOffer,
    nonce: String?
  ): ProofOfPossession {
    val key = DidService.resolveToKey(credentialWallet.did).getOrThrow()
    val proofType = offeredCredential.proofTypesSupported?.keys?.first() ?: ProofType.jwt
    return credentialWallet.generateKeyProof(
      key = key,
      cosePubKey = if (proofType == ProofType.cwt) OneKey(
        ECKey.parse(key.getPublicKey().exportJWK()).toECPublicKey(),
        null
      ).AsCBOR().EncodeToBytes() else null,
      issuerUrl = credentialOffer.credentialIssuer,
      nonce = nonce,
      proofType = proofType
    )
  }
}
