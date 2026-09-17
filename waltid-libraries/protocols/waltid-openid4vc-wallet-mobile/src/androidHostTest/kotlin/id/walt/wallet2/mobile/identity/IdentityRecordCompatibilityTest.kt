package id.walt.wallet2.mobile.identity

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.PlatformKeyFacts
import id.walt.wallet2.persistence.keys.WalletKeyRequirements
import kotlin.test.Test
import kotlin.test.assertEquals

/** Synthetic pre-cleanup records pin persisted bytes independently of API names. */
class IdentityRecordCompatibilityTest {
    @Test fun `existing journal and recovery bytes remain stable`() {
        val reference = IdentityBackupReference("fixture-provider", "fixture-identity")
        val recovery = RecoveryRecord(identityId = reference.recordId, keyId = "fixture-key", did = "did:jwk:fixture",
            publicJwk = "{}", secret = RecoverySecret.Derived("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "fixture-identity"),
            constraints = RecoveryConstraints(SigningIdentityKeyStorage.HardwareBacked, KeyUseAuthorizationPolicy.None, RecoveryConfirmation.LocalAcceptance))
        val identity = SigningIdentity(reference.recordId, recovery.keyId, recovery.did, recovery.publicJwk,
            SigningIdentityKeyStorage.HardwareBacked, KeyUseAuthorizationPolicy.None, PlatformKeyFacts(),
            SigningIdentityRecoveryState.Submitted(reference, RecoveryReceipt.AcceptedLocally))
        val preparing = IdentityRecord(id = reference.recordId, keyId = recovery.keyId, phase = IdentityPhase.Preparing,
            storage = SigningIdentityKeyStorage.HardwareBacked, requirements = WalletKeyRequirements(identitySpec, identityUsages),
            policy = SigningIdentityKeyPolicy.BackupAndCustodyDisabled)
        val records = mapOf(
            "preparing" to recordJson.encodeToString(preparing),
            "awaiting-backup" to recordJson.encodeToString(preparing.copy(phase = IdentityPhase.AwaitingBackup,
                policy = SigningIdentityKeyPolicy.GeneralPurpose, identity = identity, recovery = recovery, backup = reference,
                pendingReason = SigningIdentityFailure.ProviderInteractionRequired)),
            "active-submitted" to recordJson.encodeToString(preparing.copy(phase = IdentityPhase.Active,
                policy = SigningIdentityKeyPolicy.GeneralPurpose, identity = identity, recovery = recovery, backup = reference)),
            "derived-recovery" to recordJson.encodeToString(recovery),
            "exported-recovery" to recordJson.encodeToString(recovery.copy(secret = RecoverySecret.Exported("{}"))),
        )
        records.forEach { (name, actual) ->
            val expected = checkNotNull(javaClass.getResource("/identity-records/$name.json")).readText().trimEnd()
            assertEquals(expected, actual, name)
            if (name.endsWith("recovery")) assertEquals(expected, recordJson.encodeToString(recordJson.decodeFromString<RecoveryRecord>(expected)))
            else assertEquals(expected, recordJson.encodeToString(recordJson.decodeFromString<IdentityRecord>(expected)))
        }
    }
}
