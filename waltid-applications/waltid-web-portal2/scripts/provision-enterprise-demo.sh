#!/usr/bin/env bash
# Creates the portal wallet tenant, KMS, x509 store, and one issuer service per wallet.
# Reads ES_ADMIN_TOKEN from the environment and does not print it.
set -euo pipefail

: "${ES_ADMIN_TOKEN:?Set ES_ADMIN_TOKEN to an Enterprise bearer token}"

ES_BASE_URL="${ES_BASE_URL:-https://enterprise.demo.walt.id}"
ES_ORGANIZATION="${ES_ORGANIZATION:-waltid}"
ES_TENANT="${ES_TENANT:-portal-wallets}"

host="$(ES_BASE_URL="$ES_BASE_URL" python3 - <<'PY'
import os
from urllib.parse import urlparse
print(urlparse(os.environ["ES_BASE_URL"]).hostname or "")
PY
)"

if [[ "$host" == "$ES_ORGANIZATION."* ]]; then
  API_BASE="${ES_BASE_URL%/}"
else
  API_BASE="https://${ES_ORGANIZATION}.${host}"
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

request() {
  local method="$1"
  local url="$2"
  local body="${3:-}"
  local output="$tmp/body"
  local code
  if [[ -n "$body" ]]; then
    code="$(curl -sS -o "$output" -w "%{http_code}" -X "$method" \
      -H "Authorization: Bearer ${ES_ADMIN_TOKEN}" \
      -H "Accept: application/json" \
      -H "Content-Type: application/json" \
      --data-binary "$body" \
      "$url")"
  else
    code="$(curl -sS -o "$output" -w "%{http_code}" -X "$method" \
      -H "Authorization: Bearer ${ES_ADMIN_TOKEN}" \
      -H "Accept: application/json" \
      "$url")"
  fi
  python3 - "$code" "$output" "$method" "$url" <<'PY'
import json, pathlib, sys
code, path, method, url = sys.argv[1:]
text = pathlib.Path(path).read_text()
def redact(value):
    if isinstance(value, dict):
        return {key: "[redacted]" if key in {"d", "key", "jwk", "privateKeyPem"} else redact(item) for key, item in value.items()}
    if isinstance(value, list):
        return [redact(item) for item in value]
    return value
try:
    parsed = json.loads(text) if text else {}
except Exception:
    parsed = {}
message = parsed.get("message", "") if isinstance(parsed, dict) else ""
if code.startswith("2") or "already exists" in message:
    label = "already exists" if "already exists" in message else code
    print(f"{method} {url} -> {label}")
else:
    try:
        detail = json.dumps(redact(json.loads(text)))[:500]
    except Exception:
        detail = text[:500]
    print(f"{method} {url} -> {code} {detail}", file=sys.stderr)
    sys.exit(1)
PY
}

org_body="$(python3 - <<'PY'
import json
print(json.dumps({"_id": "portal-wallets", "profile": {"name": "Portal wallets"}}))
PY
)"
org_code="$(curl -sS -o "$tmp/org" -w "%{http_code}" -X POST \
  -H "Authorization: Bearer ${ES_ADMIN_TOKEN}" \
  -H "Accept: application/json" \
  -H "Content-Type: application/json" \
  --data-binary "$org_body" \
  "${ES_BASE_URL%/}/v1/organization/create" || true)"
if [[ "$org_code" =~ ^2 ]]; then
  ES_ORGANIZATION="portal-wallets"
  if [[ "$host" != portal-wallets.* ]]; then
    API_BASE="https://portal-wallets.${host}"
  fi
  echo "POST ${ES_BASE_URL%/}/v1/organization/create -> ${org_code}"
else
  echo "Organization creation returned ${org_code}; using ${ES_ORGANIZATION} at ${API_BASE}"
fi

root="${ES_ORGANIZATION}.${ES_TENANT}"
request POST "${API_BASE}/v1/${root}/resource-api/tenants/create" '{"name":"Portal wallets"}'
request POST "${API_BASE}/v1/${root}.kms/resource-api/services/create" "{\"type\":\"kms\",\"_id\":\"${root}.kms\"}"
request POST "${API_BASE}/v1/${root}.kms.issuer-key/kms-service-api/keys/generate" '{"backend":"jwk","keyType":"secp256r1","name":"portal-issuer"}'
request POST "${API_BASE}/v1/${root}.x509/resource-api/services/create" "{\"type\":\"x509-store\",\"_id\":\"${root}.x509\"}"

KEY_ID="${root}.kms.issuer-key"
KMS_ID="${root}.kms"

python3 - "$tmp/issuers.json" "$KEY_ID" "$KMS_ID" <<'PY'
import json, sys
path, key_id, kms_id = sys.argv[1:]
proof = {
    "jwt": {"proof_signing_alg_values_supported": ["ES256"]},
}
card_base = "https://issuer2.demo.walt.id/static/credential-cards"
def display(name, description, color, image):
    return {
        "name": name,
        "locale": "en",
        "description": description,
        "background_color": color,
        "text_color": "#FFFFFF",
        "background_image": {"uri": f"{card_base}/{image}"},
        "logo": {"uri": f"{card_base}/waltid-mark.png", "alt_text": "walt.id"},
    }
def claim(path, name):
    return {"path": path, "display": [{"name": name, "locale": "en"}]}
configurations = {
    "pid-mdoc": {
        "format": "mso_mdoc",
        "scope": "pid-mdoc",
        "doctype": "eu.europa.ec.eudi.pid.1",
        "credential_signing_alg_values_supported": [-7],
        "cryptographic_binding_methods_supported": ["cose_key"],
        "proof_types_supported": proof,
        "credential_metadata": {
            "display": [display("PID", "Person Identification Data", "#0573F0", "pid-mdoc.png")],
            "claims": [
                claim(["eu.europa.ec.eudi.pid.1", "family_name"], "Family name"),
                claim(["eu.europa.ec.eudi.pid.1", "given_name"], "Given name"),
                claim(["eu.europa.ec.eudi.pid.1", "birth_date"], "Birth date"),
            ],
        },
    },
    "pid-sd-jwt": {
        "format": "dc+sd-jwt",
        "scope": "pid-sd-jwt",
        "vct": "urn:eudi:pid:1",
        "credential_signing_alg_values_supported": ["ES256"],
        "cryptographic_binding_methods_supported": ["jwk"],
        "proof_types_supported": proof,
        "credential_metadata": {
            "display": [display("PID", "Person Identification Data (SD-JWT)", "#1D4ED8", "pid-sd-jwt.png")],
            "claims": [
                claim(["family_name"], "Family name"),
                claim(["given_name"], "Given name"),
                claim(["birth_date"], "Birth date"),
            ],
        },
    },
    "mdl": {
        "format": "mso_mdoc",
        "scope": "mdl",
        "doctype": "org.iso.18013.5.1.mDL",
        "credential_signing_alg_values_supported": [-7],
        "cryptographic_binding_methods_supported": ["cose_key"],
        "proof_types_supported": proof,
        "credential_metadata": {
            "display": [display("Mobile Driving Licence", "ISO 18013-5 mobile driving licence", "#0F766E", "mdl.png")],
            "claims": [
                claim(["org.iso.18013.5.1", "family_name"], "Family name"),
                claim(["org.iso.18013.5.1", "given_name"], "Given name"),
                claim(["org.iso.18013.5.1", "birth_date"], "Birth date"),
                claim(["org.iso.18013.5.1", "driving_privileges"], "Driving privileges"),
            ],
        },
    },
    "sca-payment": {
        "format": "dc+sd-jwt",
        "scope": "sca-payment",
        "vct": "urn:eudi:sca:payment:1",
        "credential_signing_alg_values_supported": ["ES256"],
        "cryptographic_binding_methods_supported": ["jwk"],
        "proof_types_supported": proof,
        "credential_metadata": {
            "display": [display("SCA Payment", "SD-JWT payment credential", "#0F172A", "payment-card.png")],
            "claims": [
                claim(["card_scheme"], "Card scheme"),
                claim(["card_last4"], "Last four digits"),
                claim(["card_holder_name"], "Card holder"),
            ],
        },
    },
}
profiles = {
    "pid-mdoc": {
        "name": "PID mDOC",
        "credentialConfigurationId": "pid-mdoc",
        "issuerKeyId": key_id,
        "credentialData": {
            "eu.europa.ec.eudi.pid.1": {
                "family_name": "Musterfrau",
                "given_name": "Anna",
                "birth_date": "1990-01-01",
            }
        },
        "mDocNameSpacesDataMappingConfig": {
            "eu.europa.ec.eudi.pid.1": {
                "entriesConfigMap": {
                    "birth_date": {"type": "string", "conversionType": "stringToFullDate"}
                }
            }
        },
    },
    "pid-sd-jwt": {
        "name": "PID SD-JWT",
        "credentialConfigurationId": "pid-sd-jwt",
        "issuerKeyId": key_id,
        "credentialData": {
            "family_name": "Musterfrau",
            "given_name": "Anna",
            "birth_date": "1990-01-01",
        },
        "selectiveDisclosure": {
            "fields": {
                "family_name": {"sd": True},
                "given_name": {"sd": True},
                "birth_date": {"sd": True},
            },
            "decoyMode": "NONE",
            "decoys": 0,
        },
    },
    "mdl": {
        "name": "mDL",
        "credentialConfigurationId": "mdl",
        "issuerKeyId": key_id,
        "credentialData": {
            "org.iso.18013.5.1": {
                "family_name": "Musterfrau",
                "given_name": "Anna",
                "birth_date": "1990-01-01",
                "issuing_country": "AT",
                "driving_privileges": [{"vehicle_category_code": "B"}],
            }
        },
        "mDocNameSpacesDataMappingConfig": {
            "org.iso.18013.5.1": {
                "entriesConfigMap": {
                    "birth_date": {"type": "string", "conversionType": "stringToFullDate"}
                }
            }
        },
    },
    "sca-payment": {
        "name": "SCA Payment",
        "credentialConfigurationId": "sca-payment",
        "issuerKeyId": key_id,
        "credentialData": {
            "card_scheme": "demo",
            "card_last4": "4242",
            "card_holder_name": "Anna Musterfrau",
        },
        "selectiveDisclosure": {
            "fields": {
                "card_scheme": {"sd": True},
                "card_last4": {"sd": True},
                "card_holder_name": {"sd": True},
            },
            "decoyMode": "NONE",
            "decoys": 0,
        },
    },
}
json.dump({
    "configurations": configurations,
    "profiles": profiles,
    "kms": kms_id,
    "tokenKeyId": key_id,
}, open(path, "w"))
PY

for issuer in eudi-reference france-identite d-you; do
  issuer_target="${root}.${issuer}"
  existing="$tmp/existing-${issuer}.json"
  existing_code="$(curl -sS -o "$existing" -w "%{http_code}" \
    -H "Authorization: Bearer ${ES_ADMIN_TOKEN}" \
    -H "Accept: application/json" \
    "${API_BASE}/v2/${issuer_target}/issuer-service-api/configuration/view" || true)"
  if [[ "$existing_code" != "200" ]]; then
    printf '{}' > "$existing"
  fi
  issuer_body="$(python3 - "$tmp/issuers.json" "$issuer_target" "$issuer" "$existing" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
target, issuer, existing_path = sys.argv[2:]
existing = json.load(open(existing_path))
configurations = data["configurations"]
if issuer == "france-identite":
    configurations = {"pid-mdoc": configurations["pid-mdoc"]}
body = {
    "type": "issuer2",
    "_id": target,
    "kms": existing.get("kms") or data["kms"],
    "tokenKeyId": existing.get("tokenKeyId") or data["tokenKeyId"],
    "credentialConfigurations": configurations,
}
if existing.get("signedMetadata"):
    body["signedMetadata"] = existing["signedMetadata"]
print(json.dumps(body))
PY
)"
  request POST "${API_BASE}/v1/${issuer_target}/resource-api/services/create" "$issuer_body"
  request PUT "${API_BASE}/v2/${issuer_target}/issuer-service-api/configuration/update" "$issuer_body"
  if [[ "$issuer" == "france-identite" ]]; then
    profile_ids=(pid-mdoc)
  else
    profile_ids=(pid-mdoc pid-sd-jwt mdl sca-payment)
  fi
  for profile in "${profile_ids[@]}"; do
    profile_body="$(python3 - "$tmp/issuers.json" "$profile" <<'PY'
import json, sys
data = json.load(open(sys.argv[1]))
print(json.dumps(data["profiles"][sys.argv[2]]))
PY
)"
    request POST "${API_BASE}/v2/${issuer_target}.${profile}/issuer-service-api/credentials/profiles" "$profile_body"
  done
  case "$issuer" in
    eudi-reference) eudi_target="$issuer_target" ;;
    france-identite) france_target="$issuer_target" ;;
    d-you) dyou_target="$issuer_target" ;;
  esac
done

cat <<EOF
NUXT_ES_BASE_URL=${API_BASE}
NUXT_ES_EUDI_REF_ISSUER_TARGET=${eudi_target}
NUXT_ES_FRANCE_IDENTITE_ISSUER_TARGET=${france_target}
NUXT_ES_DYOU_ISSUER_TARGET=${dyou_target}
EOF
