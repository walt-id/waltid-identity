package id.walt.cli.util

import id.walt.credentials.CredentialHolderBinding as SharedCredentialHolderBinding
import id.walt.credentials.formats.DigitalCredential
import id.walt.crypto2.keys.Key

object CredentialHolderBinding {
    suspend fun requireBoundToPresenter(
        credential: DigitalCredential,
        presenterDid: String,
        presenterKey: Key,
    ) {
        SharedCredentialHolderBinding.requireBoundToPresenter(credential, presenterDid, KeyUtil.thumbprint(presenterKey))
    }
}
