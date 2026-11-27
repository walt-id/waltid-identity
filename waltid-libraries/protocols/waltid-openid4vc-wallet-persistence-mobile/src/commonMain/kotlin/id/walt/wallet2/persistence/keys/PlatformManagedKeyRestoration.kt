package id.walt.wallet2.persistence.keys

import id.walt.crypto2.keys.KeyUseAuthorizationPolicy
import id.walt.crypto2.keys.ManagedKey

/**
 * Result of restoring one persisted managed key.
 *
 * The result retains the persisted authorization policy even when native key material is missing.
 */
public sealed interface PlatformManagedKeyRestoration {
    /** Authorization policy persisted with the managed key. */
    public val authorizationPolicy: KeyUseAuthorizationPolicy

    /** Native key material was found and restored. */
    public data class Restored(
        /** Restored platform-managed key. */
        public val key: ManagedKey,
        override val authorizationPolicy: KeyUseAuthorizationPolicy,
    ) : PlatformManagedKeyRestoration

    /** Native material belongs to this identity but was permanently invalidated by the platform. */
    public data class Invalidated(
        override val authorizationPolicy: KeyUseAuthorizationPolicy,
    ) : PlatformManagedKeyRestoration

    /** Native key material is absent while its persisted policy remains known. */
    public data class Missing(
        override val authorizationPolicy: KeyUseAuthorizationPolicy,
    ) : PlatformManagedKeyRestoration
}
