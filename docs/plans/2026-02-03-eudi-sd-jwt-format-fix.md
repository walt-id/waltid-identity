# EUDI Wallet SD-JWT Format Fix

## Problem

The EUDI wallet SDK rejects credential offers from walt.id issuer with error:
> "Credential offer contains unknown credential ids"

## Root Cause

The EUDI wallet SDK (`eu.europa.ec.eudi:eudi-lib-jvm-openid4vci-kt:0.9.1`) only recognizes `dc+sd-jwt` format for SD-JWT credentials, but walt.id issuer advertises `vc+sd-jwt`.

**SDK code in `CredentialIssuerMetadataJsonParser.kt`:**
```kotlin
private val knownFormats = setOf(
    FORMAT_MSO_MDOC,
    FORMAT_SD_JWT_VC,  // "dc+sd-jwt" - not "vc+sd-jwt"
    FORMAT_W3C_SIGNED_JWT,
    ...
)
```

When parsing issuer metadata, credentials with unknown formats are silently filtered out. When the wallet later validates the credential offer, it finds no matching credential configurations.

## Solution

Change the SD-JWT PID format in issuer metadata from `vc+sd-jwt` to `dc+sd-jwt`.

**Before:**
```hocon
"urn:eudi:pid:1" = {
    format = "vc+sd-jwt"
    ...
}
```

**After:**
```hocon
"urn:eudi:pid:1" = {
    format = "dc+sd-jwt"
    ...
}
```

## Verification

walt.id library supports both formats in `CredentialFormat.kt`:
- `sd_jwt_dc("dc+sd-jwt")`
- `sd_jwt_vc("vc+sd-jwt")`

The format string is parsed by `CredentialFormat.fromValue()` which handles `dc+sd-jwt` correctly.

## mDoc Status

The EUDI wallet SDK marks CWT proof type as "Unsupported":
```kotlin
"cwt" -> ProofTypeMeta.Unsupported(type)
```

mDoc credentials require CWT proofs (COSE key binding), so mDoc issuance won't work with the current SDK version. Focus on SD-JWT for now.

## Files Modified

- `docker-compose/issuer-api/config/credential-issuer-metadata.conf`

## Testing

1. Rebuild issuer-api Docker image
2. Restart services
3. Generate fresh credential offer for `urn:eudi:pid:1`
4. Scan with EUDI wallet
5. Complete pre-authorized flow
6. Verify SD-JWT credential appears in wallet
