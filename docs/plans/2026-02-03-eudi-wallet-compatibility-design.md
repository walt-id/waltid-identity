# EUDI Wallet Compatibility Design

## Overview

Enable full compatibility between walt.id issuer API and the EU Reference Wallet (Android) for issuing PID and mDL credentials.

## Goals

- Issue SD-JWT PID credentials to EUDI wallet
- Issue mDoc PID credentials to EUDI wallet
- Issue mDL credentials to EUDI wallet
- Support both pre-authorized code and authorization code flows

## Phased Approach

### Phase 1: SD-JWT PID

**Why first:** Uses JWT-based proofs (already supported), fewest changes needed.

**Changes:**
- Add `proof_types_supported` with JWT config
- Add `scope` for authorization code flow
- Add `display` for wallet UI
- Add `claims` with attribute definitions

**Files modified:**
- `docker-compose/issuer-api/config/credential-issuer-metadata.conf`

**Testing:**
1. Verify metadata: `curl .well-known/openid-credential-issuer`
2. Generate credential offer with `urn:eu.europa.ec.eudi:pid:1`
3. Scan with EU Reference Wallet
4. Complete pre-authorized flow

### Phase 2: mDoc PID

**Why second:** Requires COSE/CWT proof handling.

**Changes:**
- Change `cryptographic_binding_methods_supported` from `jwk` to `cose_key`
- Change `proof_types_supported` from `jwt` to `cwt`
- Add `display` metadata

**Potential code changes:**
- CWT proof validation in CIProvider
- COSE key binding in credential

### Phase 3: mDL

**Why last:** Similar to mDoc PID, can reuse Phase 2 work.

**Changes:**
- Add `display` metadata
- Verify mDL-specific claims/namespaces

## Configuration

### Updated credential-issuer-metadata.conf

```hocon
supportedCredentialTypes = {
    # Phase 1: SD-JWT PID
    "urn:eu.europa.ec.eudi:pid:1" = {
        format = "vc+sd-jwt"
        vct = "urn:eu.europa.ec.eudi:pid:1"
        scope = "urn:eu.europa.ec.eudi:pid:1"
        cryptographic_binding_methods_supported = ["jwk"]
        credential_signing_alg_values_supported = ["ES256"]
        proof_types_supported = {
            jwt = { proof_signing_alg_values_supported = ["ES256", "ES384", "ES512"] }
        }
        display = [{ name = "EU Person Identification Data", locale = "en" }]
        claims = {
            given_name = { display = [{ name = "Given Name", locale = "en" }] }
            family_name = { display = [{ name = "Family Name", locale = "en" }] }
            birth_date = { display = [{ name = "Date of Birth", locale = "en" }] }
            issuing_country = { display = [{ name = "Issuing Country", locale = "en" }] }
            issuing_authority = { display = [{ name = "Issuing Authority", locale = "en" }] }
        }
    }

    # Phase 2: mDoc PID
    "eu.europa.ec.eudi.pid_mso_mdoc" = {
        format = mso_mdoc
        scope = "eu.europa.ec.eudi.pid_mso_mdoc"
        cryptographic_binding_methods_supported = ["cose_key"]
        credential_signing_alg_values_supported = ["ES256"]
        proof_types_supported = {
            cwt = { proof_signing_alg_values_supported = ["ES256"] }
        }
        doctype = "eu.europa.ec.eudi.pid.1"
        display = [{ name = "EU PID (mDoc)", locale = "en" }]
    }

    "eu.europa.ec.eudi.pid.1" = {
        format = mso_mdoc
        scope = "eu.europa.ec.eudi.pid.1"
        cryptographic_binding_methods_supported = ["cose_key"]
        credential_signing_alg_values_supported = ["ES256"]
        proof_types_supported = { cwt = { proof_signing_alg_values_supported = ["ES256"] } }
        doctype = "eu.europa.ec.eudi.pid.1"
    }

    # Phase 3: mDL
    "org.iso.18013.5.1.mDL" = {
        format = mso_mdoc
        cryptographic_binding_methods_supported = ["cose_key"]
        credential_signing_alg_values_supported = ["ES256"]
        proof_types_supported = { cwt = { proof_signing_alg_values_supported = ["ES256"] } }
        doctype = "org.iso.18013.5.1.mDL"
        display = [{ name = "Mobile Driving License", locale = "en" }]
    }
}
```

## Implementation Already Complete

- Client Attestation support (`attest_jwt_client_auth`)
- DPoP handling
- OpenID4VCI Draft 13 format

## Success Criteria

- [ ] Phase 1: SD-JWT PID issued to EU Reference Wallet
- [ ] Phase 2: mDoc PID issued to EU Reference Wallet
- [ ] Phase 3: mDL issued to EU Reference Wallet
- [ ] Both pre-authorized and authorization code flows work
