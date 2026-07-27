# waltid-crypto2-pkcs11

JVM PKCS#11 managed-key provider for `waltid-crypto2`. PINs are resolved at operation time and are never serialized. The provider supports EC/RSA signing (ECDSA, RSASSA-PKCS1-v1_5, RSASSA-PSS), persistent aliases, and deletion.

RSA encryption and RSA key wrapping are deliberately not supported. SunPKCS11 registers only `RSA/ECB/PKCS1Padding` and `RSA/ECB/NoPadding` (`P11RSACipher` rejects every OAEP padding and `CKM_RSA_PKCS_OAEP` is never mapped), so RSA-OAEP is unreachable through the JCA `Cipher` API for any token, and `RSAES-PKCS1-v1_5` decryption is a Bleichenbacher padding oracle against a token-held key. Key usages are therefore restricted to `SIGN` and `VERIFY`. If wrapping is ever required, it should be added as AES-KW under an OAEP-capable mechanism rather than PKCS#1 v1.5.

The generic provider is intended for SoftHSM and hardware tokens including Thales Luna. The Luna smoke test is enabled with `WALTID_LUNA_PKCS11_LIBRARY`, `WALTID_LUNA_PKCS11_SLOT`, and `WALTID_LUNA_PKCS11_PIN`; no Luna-specific API is used unless a concrete vendor-only requirement is identified.
