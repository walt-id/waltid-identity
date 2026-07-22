# waltid-crypto2-pkcs11

JVM PKCS#11 managed-key provider for `waltid-crypto2`. PINs are resolved at operation time and are never serialized. The provider supports EC/RSA signing, explicit RSA PKCS#1 encryption and wrapping, persistent aliases, and deletion.

The generic provider is intended for SoftHSM and hardware tokens including Thales Luna. The Luna smoke test is enabled with `WALTID_LUNA_PKCS11_LIBRARY`, `WALTID_LUNA_PKCS11_SLOT`, and `WALTID_LUNA_PKCS11_PIN`; no Luna-specific API is used unless a concrete vendor-only requirement is identified.
