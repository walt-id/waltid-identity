package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.KeyUseAuthorizationException
import id.walt.crypto2.keys.KeyUseAuthorizationFailure

internal fun recoveryProviderFailure(cause: Exception): SigningIdentityFailure = when ((cause as? IdentityProviderException)?.failure) {
    IdentityProviderFailure.InteractionRequired -> SigningIdentityFailure.ProviderInteractionRequired
    IdentityProviderFailure.Rejected -> SigningIdentityFailure.ProviderRejected
    IdentityProviderFailure.Conflict -> SigningIdentityFailure.ProviderConflict
    IdentityProviderFailure.ConfirmationPending -> SigningIdentityFailure.ProviderConfirmationPending
    IdentityProviderFailure.TemporarilyUnavailable, null -> SigningIdentityFailure.ProviderUnavailable
}
internal fun signingIdentityFailure(cause: Throwable): SigningIdentityFailure = when ((cause as? KeyUseAuthorizationException)?.failure) {
    KeyUseAuthorizationFailure.UnsupportedCombination -> SigningIdentityFailure.UnsupportedPolicy
    KeyUseAuthorizationFailure.ProtectedKeyUnavailable -> SigningIdentityFailure.KeyUnavailable
    KeyUseAuthorizationFailure.InvalidStoredKeyMetadata -> SigningIdentityFailure.InvalidRecoveryRecord
    KeyUseAuthorizationFailure.BiometricUnavailable, KeyUseAuthorizationFailure.BiometricNotEnrolled,
    KeyUseAuthorizationFailure.DeviceCredentialNotSet,
    KeyUseAuthorizationFailure.InteractionContextUnavailable, KeyUseAuthorizationFailure.AuthorizationNotCompleted ->
        SigningIdentityFailure.AuthorizationNotCompleted
    null -> SigningIdentityFailure.NativeOperationFailed
}
