package id.walt.cli.util

import id.walt.credentials.formats.AbstractW3C
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto2.keys.Key

object CredentialHolderBinding {
    suspend fun requireBoundToPresenter(
        credential: DigitalCredential,
        presenterDid: String,
        presenterKey: Key,
    ) {
        val holderKey = credential.getHolderCrypto2Key()
        val bound = when {
            holderKey != null -> KeyUtil.samePublicIdentity(holderKey, presenterKey)
            credential is AbstractW3C -> credential.subject == presenterDid
            else -> false
        }
        require(bound) {
            "Credential is not cryptographically or semantically bound to presenter $presenterDid"
        }
    }
}
