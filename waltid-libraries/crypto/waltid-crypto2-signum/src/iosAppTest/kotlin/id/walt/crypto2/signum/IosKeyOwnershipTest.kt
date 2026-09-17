package id.walt.crypto2.signum

import id.walt.crypto2.keys.PlatformKeyConfiguration
import id.walt.crypto2.keys.HardwarePreference
import id.walt.crypto2.keys.KeychainAccessibility
import id.walt.crypto2.keys.KeyOrigin
import id.walt.crypto2.algorithms.DigestAlgorithm
import id.walt.crypto2.algorithms.SignatureAlgorithm
import id.walt.crypto2.keys.EcCurve
import id.walt.crypto2.keys.KeySpec
import id.walt.crypto2.keys.KeyUsage
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSUUID
import kotlin.test.*

/** Requires an application host with Keychain access, including on the simulator. */
class IosKeyOwnershipTest {
    private val spec = KeySpec.Ec(EcCurve.P256)
    private val usages = setOf(KeyUsage.SIGN, KeyUsage.VERIFY)
    private val policy = SignumKeyPolicy(hardware = HardwarePreference.DISCOURAGED)
    private val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_256)

    @Test
    fun ownedKeyReopensSignsAndRemainsExportable() = runTest {
        val alias = "ownership-${NSUUID().UUIDString}"
        val backend = IosSignumKeyBackend()
        try {
            val created = backend.create(alias, spec, usages, policy)
            val reopened = assertNotNull(IosSignumKeyBackend().load(alias, spec, usages, policy))
            assertEquals(created.publicKey, reopened.publicKey)
            assertEquals(KeyOrigin.GENERATED, reopened.origin)
            val data = "owned key".encodeToByteArray()
            assertTrue(reopened.verify(data, reopened.sign(data, algorithm), algorithm))
            assertNotNull(reopened.privateKeyExporter).exportPrivateKey()
        } finally { backend.delete(alias, policy) }
    }

    @Test
    fun stableSignumKeyReopensWithRecordedPolicy() = runTest {
        val alias = "signum-owned-${NSUUID().UUIDString}"
        val backend = IosSignumKeyBackend()
        val spec = KeySpec.Ec(EcCurve.P384)
        val algorithm = SignatureAlgorithm.Ecdsa(DigestAlgorithm.SHA_384)
        try {
            val created = backend.create(alias, spec, usages, policy)
            assertEquals(IosKeyEngine.SIGNUM, assertNotNull(IosKeyOwnershipStore.read(alias, policy)).engine)
            val reopened = assertNotNull(IosSignumKeyBackend().load(alias, spec, usages, policy))
            assertEquals(created.publicKey, reopened.publicKey)
            val data = "stable Signum".encodeToByteArray()
            assertTrue(reopened.verify(data, reopened.sign(data, algorithm), algorithm))
            assertFailsWith<SignumKeyPolicyMismatchException> {
                backend.load(alias, spec, usages, policy.copy(authentication = SignumAuthenticationPolicy.UserPresence()))
            }
        } finally { backend.delete(alias, policy) }
    }

    @Test
    fun recoveredKeyKeepsItsOriginalPublicKeyAcrossReimport() = runTest {
        exerciseNativePrivateImport(IosSignumKeyBackend(), policy)
    }

    @Test
    fun unprotectedKeyCannotBeReopenedWithProtectedPolicy() = runTest {
        val alias = "policy-${NSUUID().UUIDString}"
        val backend = IosSignumKeyBackend()
        try {
            backend.create(alias, spec, usages, policy)
            val changed = listOf(
                policy.copy(authentication = SignumAuthenticationPolicy.UserPresence(deviceCredential = false)),
                policy.copy(platform = PlatformKeyConfiguration.IosKeychain(KeychainAccessibility.AFTER_FIRST_UNLOCK_DEVICE_ONLY)),
            )
            for (requested in changed) assertFailsWith<SignumKeyPolicyMismatchException> {
                backend.load(alias, spec, usages, requested)
            }
            assertNotNull(backend.load(alias, spec, usages, policy))
        } finally { backend.delete(alias, policy) }
    }

    @Test
    fun missingReceiptPreventsUseWithoutDeletingOrAdoptingTheNativeKey() = runTest {
        val alias = "unowned-${NSUUID().UUIDString}"
        val recoveredAlias = "$alias-recovered"
        val backend = IosSignumKeyBackend()
        try {
            val opened = backend.create(alias, spec, usages, policy)
            val material = assertNotNull(opened.privateKeyExporter).exportPrivateKey()
            val native = assertNotNull(iosKeyIdentity(alias, policy, IosKeyEngine.APPLE_KEYCHAIN))
            IosKeyOwnershipStore.delete(alias, policy)
            assertNull(backend.load(alias, spec, usages, policy))
            assertFailsWith<SignumKeyPolicyMismatchException> { opened.sign(byteArrayOf(1), algorithm) }
            assertFailsWith<SignumKeyPolicyMismatchException> { assertNotNull(opened.privateKeyExporter).exportPrivateKey() }
            backend.delete(alias, policy)
            assertFailsWith<IllegalArgumentException> { backend.create(alias, spec, usages, policy) }
            assertEquals(native, iosKeyIdentity(alias, policy, IosKeyEngine.APPLE_KEYCHAIN))
            val recovered = backend.importPrivateKey(recoveredAlias,
                assertIs<id.walt.crypto2.keys.EncodedKey.Jwk>(material), spec, usages, policy)
            assertEquals(opened.publicKey, recovered.publicKey)
            assertTrue(opened.verify(byteArrayOf(1), recovered.sign(byteArrayOf(1), algorithm), algorithm))
        } finally {
            AppleKeychainKeys.delete(alias, policy)
            backend.delete(recoveredAlias, policy)
        }
    }

    @Test
    fun replacementIsRejectedEvenByAnAlreadyOpenedHandle() = runTest {
        val alias = "replacement-${NSUUID().UUIDString}"
        val backend = IosSignumKeyBackend()
        try {
            val key = backend.create(alias, spec, usages, policy)
            AppleKeychainKeys.delete(alias, policy)
            AppleKeychainKeys.create(alias, policy, null)
            assertFailsWith<SignumKeyPolicyMismatchException> { backend.load(alias, spec, usages, policy) }
            assertFailsWith<SignumKeyPolicyMismatchException> { key.sign(byteArrayOf(1), algorithm) }
        } finally {
            AppleKeychainKeys.delete(alias, policy)
            IosKeyOwnershipStore.delete(alias, policy)
        }
    }


    @Test
    fun malformedOriginAndReferenceCannotReopenAnOwnedKey() = runTest {
        val alias = "metadata-${NSUUID().UUIDString}"
        val backend = IosSignumKeyBackend()
        try {
            backend.create(alias, spec, usages, policy)
            val record = assertNotNull(IosKeyOwnershipStore.read(alias, policy))
            val malformed = listOf(
                record.copy(version = 2),
                record.copy(origin = KeyOrigin.UNKNOWN),
                record.copy(native = record.native.copy(persistentReference = id.walt.crypto2.serialization.BinaryData(byteArrayOf()))),
            )
            for (replacement in malformed) {
                IosKeyOwnershipStore.delete(alias, policy)
                IosKeyOwnershipStore.write(alias, policy, replacement)
                assertFailsWith<SignumKeyPolicyMismatchException> { backend.load(alias, spec, usages, policy) }
            }
            IosKeyOwnershipStore.delete(alias, policy)
            IosKeyOwnershipStore.write(alias, policy, record)
            assertNotNull(backend.load(alias, spec, usages, policy))
        } finally {
            AppleKeychainKeys.delete(alias, policy)
            IosKeyOwnershipStore.delete(alias, policy)
        }
    }

    @Test
    fun importingTheSameKeyUnderTheSameAliasDoesNotReuseItsOwnership() = runTest {
        val alias = "same-material-${NSUUID().UUIDString}"
        val backend = IosSignumKeyBackend()
        try {
            val created = backend.create(alias, spec, usages, policy)
            val material = assertNotNull(created.privateKeyExporter).exportPrivateKey() as id.walt.crypto2.keys.EncodedKey.Jwk
            AppleKeychainKeys.delete(alias, policy)
            AppleKeychainKeys.create(alias, policy, material)
            assertFailsWith<SignumKeyPolicyMismatchException> { backend.load(alias, spec, usages, policy) }
            assertFailsWith<SignumKeyPolicyMismatchException> { created.sign(byteArrayOf(1), algorithm) }
        } finally {
            AppleKeychainKeys.delete(alias, policy)
            IosKeyOwnershipStore.delete(alias, policy)
        }
    }
}
