#!/usr/bin/env bash

ensure_non_attested_issuer2() {
  local token="${1:?token is required}"
  local host_header="${2:-${ORG}.enterprise.localhost}"
  local work_dir

  log "SETUP" "Ensuring non-attested issuer exists (issuer2-noattest)"

  curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.issuer2-noattest/resource-api/services/create" \
    -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -H "Host: $host_header" \
    -d "{\"type\":\"issuer2\",\"_id\":\"$TENANT_PATH.issuer2-noattest\",\"tokenKeyId\":\"$TENANT_PATH.kms.issuer-signing-key\",\"kms\":\"$TENANT_PATH.kms\",\"credentialConfigurations\":{\"org.iso.18013.5.1.mDL\":{\"format\":\"mso_mdoc\",\"doctype\":\"org.iso.18013.5.1.mDL\",\"scope\":\"org.iso.18013.5.1.mDL\",\"credential_signing_alg_values_supported\":[-7,-9],\"cryptographic_binding_methods_supported\":[\"cose_key\"],\"proof_types_supported\":{\"jwt\":{\"proof_signing_alg_values_supported\":[\"ES256\"]}}}}}" \
    > /dev/null 2>&1 || true

  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/waltid-e2e-fixtures.XXXXXX")"

  curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.x509-store.vical-doc-signer-cert/x509-store-api/certificates" \
    -H "Authorization: Bearer $token" -H "Host: $host_header" > "$work_dir/doc_cert.json"
  curl -sf "http://localhost:$PORT/v1/$TENANT_PATH.x509-store.vical-iaca-cert/x509-store-api/certificates" \
    -H "Authorization: Bearer $token" -H "Host: $host_header" > "$work_dir/iaca_cert.json"

  DOC_CERT_JSON="$work_dir/doc_cert.json" \
  IACA_CERT_JSON="$work_dir/iaca_cert.json" \
  PROFILE_PAYLOAD_JSON="$work_dir/mdl-profile-payload.json" \
  TENANT_PATH="$TENANT_PATH" \
    python3 - <<'PY'
import json
import os

with open(os.environ["DOC_CERT_JSON"]) as f:
    doc_pem = json.load(f)["data"]["pem"]
with open(os.environ["IACA_CERT_JSON"]) as f:
    iaca_pem = json.load(f)["data"]["pem"]

tenant_path = os.environ["TENANT_PATH"]
payload = {
    "name": "mdl-profile",
    "credentialConfigurationId": "org.iso.18013.5.1.mDL",
    "issuerKeyId": f"{tenant_path}.kms.issuer-signing-key",
    "x5Chain": [
        {"type": "pem-encoded-x509-certificate-descriptor", "pemEncodedCertificate": doc_pem},
        {"type": "pem-encoded-x509-certificate-descriptor", "pemEncodedCertificate": iaca_pem},
    ],
    "credentialData": {
        "org.iso.18013.5.1": {
            "family_name": "Doe",
            "given_name": "John",
            "birth_date": "1990-01-01",
            "issue_date": "2024-01-01",
            "expiry_date": "2029-01-01",
            "issuing_country": "US",
            "issuing_authority": "Test DMV",
            "document_number": "DL123456789",
            "un_distinguishing_sign": "USA",
        }
    }
}

with open(os.environ["PROFILE_PAYLOAD_JSON"], "w") as f:
    json.dump(payload, f)
PY

  curl -sf "http://localhost:$PORT/v2/$TENANT_PATH.issuer2-noattest.mdl-profile/issuer-service-api/credentials/profiles" \
    -X POST -H "Authorization: Bearer $token" -H "Content-Type: application/json" -H "Host: $host_header" \
    -d @"$work_dir/mdl-profile-payload.json" > /dev/null 2>&1 || true

  rm -rf "$work_dir"
  log "SETUP" "issuer2-noattest ready"
}
