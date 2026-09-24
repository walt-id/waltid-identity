package id.walt.verifier2.verification2

import id.walt.crypto.utils.withoutBulkValues
import id.walt.policies2.vc.CredentialPolicyResult

/**
 * Bounds what a credential policy result contributes to a stored session.
 *
 * A credential policy returns whatever it likes and the verifier keeps it verbatim
 * (`Verifier2SessionCredentialPolicyValidation`: `result = result.getOrNull()`). For a 250 KB portrait
 * mDL that meant `policy_results.vc_policies` held **3,228,057 bytes** of a 10,046,803-byte session -
 * a third full copy of the credential, beside `presented_presentations` (3,230,756) and
 * `presented_credentials` (3,230,732), measured with server-side `$bsonSize`.
 *
 * The other two copies are deliberate: raw as received, and decoded as this version decodes it, are the
 * audit record. Policy results are explicitly allowed to reference instead of repeat -
 * `CredentialPolicyResult.credentialIndex` already says which credential a result belongs to, so the
 * value itself adds nothing that cannot be recovered.
 *
 * The bounding itself lives in `waltid-crypto` beside the conversion that creates the bulk, so the
 * verifier, the wallet credential store and anything else that persists a decoded copy share one
 * implementation and one set of limits.
 *
 * `success`, `error`, `queryId` and `credentialIndex` are untouched - only the free-form payload is
 * bounded, so nothing a caller decides on is affected.
 */
internal fun CredentialPolicyResult.boundedForStorage(): CredentialPolicyResult =
    result?.let { copy(result = it.withoutBulkValues()) } ?: this
