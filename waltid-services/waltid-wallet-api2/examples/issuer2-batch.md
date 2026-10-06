# Issuer2 offers through the public Wallet2 API

These shell examples support all four Issuer2 management contracts. Run the setup,
**one** offer-creation block, then the shared wallet steps. Use a fresh offer for each
run: pre-authorized codes and authorization codes are single-use.

| Offer producer | POST path | Management body |
|---|---|---|
| OSS, single profile | `/issuer2/credential-offers` | `profileId` |
| OSS, multiple selections | `/issuer2/credential-offers` | `credentials[]` |
| Enterprise, single profile | `/v2/{issuerTarget}.{profileId}/issuer-service-api/credentials/offers` | Single-offer fields; profile is in the target |
| Enterprise, multiple selections | `/v2/{issuerTarget}/issuer-service-api/credentials/offers` | `credentials[]` |

Issuer-management `credentials[]` selects profiles and datasets. Wallet `credentials[]`
selects **protocol configuration IDs** and holder bindings. Profile IDs are not necessarily
configuration IDs. Both issuer receipts contain `credentialOffer`; that URL, or the resolved
protocol offer JSON, is the wallet input. Never pass the management request to Wallet2.

## Setup

Requires Bash, curl and jq, running Issuer2/Wallet2 services, a configured issuer profile,
and an existing wallet with a signing key and writable credential store. The multiple-selection
examples assume an SD-JWT profile allowing the `given_name` and `family_name` overrides shown
below; adapt the credential data to the profile you configured. Issuer signing keys and wallet
holder keys are separate.

```bash
set -eo pipefail
umask 077
EXAMPLE_DIR=$(mktemp -d)
# Keep this directory private: it will contain offer and PKCE material.

ISSUER_BASE='http://localhost:7002' # Service origin, without a trailing slash.
PROFILE_ID='identityCredentialSdJwt' # An existing issuer profile.
AUTH_METHOD='PRE_AUTHORIZED'       # Or AUTHORIZED for the browser flow below.
VALUE_MODE='BY_REFERENCE'          # BY_VALUE also returns a usable credentialOffer URL.

# OSS Wallet2:
WALLET_BASE='http://localhost:7005/wallet/REPLACE_WITH_WALLET_ID'
WALLET_KIND='oss'

# For Enterprise, instead set both service origins and resource paths:
# ISSUER_BASE='https://your-enterprise-host'
# ISSUER_TARGET='your-org.your-tenant.issuer'
# WALLET_BASE='https://your-enterprise-host/v2/your-org.your-tenant.wallet/wallet-service-api'
# WALLET_KIND='enterprise'

# Supply deployment-specific bearer tokens through the environment when required.
ISSUER_AUTH=()
WALLET_AUTH=()
if [[ -n ${ISSUER_TOKEN:-} ]]; then ISSUER_AUTH=(-H "Authorization: Bearer $ISSUER_TOKEN"); fi
if [[ -n ${WALLET_TOKEN:-} ]]; then WALLET_AUTH=(-H "Authorization: Bearer $WALLET_TOKEN"); fi

jq -n --arg auth "$AUTH_METHOD" --arg value "$VALUE_MODE" \
  '{authMethod:$auth, valueMode:$value} +
   (if $auth == "AUTHORIZED" then {issuerStateMode:"INCLUDE"} else {} end)' \
  > "$EXAMPLE_DIR/offer-options.json"
```

`issuerStateMode: INCLUDE` preserves runtime overrides during authorization. Enterprise
defaults to `OMIT` when it is not supplied; authorized overrides cannot use that default.

## 1. Create an offer using one management contract

### OSS single profile

The original `profileId` body remains valid. This uses the profile's configured dataset.

```bash
jq --arg profile "$PROFILE_ID" '. + {profileId:$profile}' \
  "$EXAMPLE_DIR/offer-options.json" > "$EXAMPLE_DIR/issuer-request.json"
curl --silent --show-error --fail-with-body "${ISSUER_AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary @"$EXAMPLE_DIR/issuer-request.json" \
  "$ISSUER_BASE/issuer2/credential-offers" -o "$EXAMPLE_DIR/receipt.json"
```

### OSS two datasets under one profile

Two entries select two datasets. They do **not** request two holder copies.

```bash
jq --arg profile "$PROFILE_ID" '. + {credentials:[
  {profileId:$profile,runtimeOverrides:{credentialData:{given_name:"Alice",family_name:"Example"}}},
  {profileId:$profile,runtimeOverrides:{credentialData:{given_name:"Bob",family_name:"Example"}}}
]}' "$EXAMPLE_DIR/offer-options.json" > "$EXAMPLE_DIR/issuer-request.json"
curl --silent --show-error --fail-with-body "${ISSUER_AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary @"$EXAMPLE_DIR/issuer-request.json" \
  "$ISSUER_BASE/issuer2/credential-offers" -o "$EXAMPLE_DIR/receipt.json"
```

For different configurations/formats, use different existing `profileId` values in the
array and data appropriate to each profile. Top-level `profileId` or `runtimeOverrides`
must not be combined with the array contract, even as null fields.

### Enterprise single profile

The profile is part of the resource target. The body has **no** `profileId` or `credentials`.
The caller needs issuance permission on that profile target.

```bash
: "${ISSUER_TARGET:?Set the Enterprise issuer resource target}"
PROFILE_TARGET=$(jq -rn --arg value "$ISSUER_TARGET.$PROFILE_ID" '$value|@uri')
cp "$EXAMPLE_DIR/offer-options.json" "$EXAMPLE_DIR/issuer-request.json"
curl --silent --show-error --fail-with-body "${ISSUER_AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary @"$EXAMPLE_DIR/issuer-request.json" \
  "$ISSUER_BASE/v2/$PROFILE_TARGET/issuer-service-api/credentials/offers" \
  -o "$EXAMPLE_DIR/receipt.json"
```

### Enterprise two datasets under one profile

Use the issuer target, not a profile target. Each array entry names an existing profile.
The caller needs issuance permission on the issuer target.

```bash
: "${ISSUER_TARGET:?Set the Enterprise issuer resource target}"
ENCODED_ISSUER_TARGET=$(jq -rn --arg value "$ISSUER_TARGET" '$value|@uri')
jq --arg profile "$PROFILE_ID" '. + {credentials:[
  {profileId:$profile,runtimeOverrides:{credentialData:{given_name:"Alice",family_name:"Example"}}},
  {profileId:$profile,runtimeOverrides:{credentialData:{given_name:"Bob",family_name:"Example"}}}
]}' "$EXAMPLE_DIR/offer-options.json" > "$EXAMPLE_DIR/issuer-request.json"
curl --silent --show-error --fail-with-body "${ISSUER_AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary @"$EXAMPLE_DIR/issuer-request.json" \
  "$ISSUER_BASE/v2/$ENCODED_ISSUER_TARGET/issuer-service-api/credentials/offers" \
  -o "$EXAMPLE_DIR/receipt.json"
```

## 2. Resolve the receipt through Wallet2

All four receipts enter the same public wallet flow. The following request has no explicit
selections: receiving it defaults to one holder binding per granted target and generates no keys.

```bash
jq -e '.credentialOffer | type == "string" and length > 0' "$EXAMPLE_DIR/receipt.json" >/dev/null
jq '{offerUrl:.credentialOffer}' "$EXAMPLE_DIR/receipt.json" > "$EXAMPLE_DIR/receive-request.json"
curl --silent --show-error --fail-with-body "${WALLET_AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary @"$EXAMPLE_DIR/receive-request.json" \
  "$WALLET_BASE/credentials/receive/resolve-offer/batch" -o "$EXAMPLE_DIR/preview.json"
jq '{batchSize,offer:(.offer|{credentialConfigurationIds,grantType,txCodeRequired})}' "$EXAMPLE_DIR/preview.json"
```

If the caller already resolved the protocol offer, `{ "offerJson": { "credential_issuer":
"...", "credential_configuration_ids": ["..."], "grants": { ... } } }` is the alternative
input. Supply exactly one of `offerUrl` and `offerJson`.

### Optional: request two copies per granted dataset

Skip this block for the default single-copy behavior. Both holder keys must already exist
and be usable by the wallet; advertised batch support does not create keys or increase
copy count automatically. Enterprise keys must be authorized references in an attached KMS.

```bash
HOLDER_1='REPLACE_WITH_EXISTING_KEY_ID_OR_KMS_RESOURCE_PATH'
HOLDER_2='REPLACE_WITH_ANOTHER_EXISTING_KEY_ID_OR_KMS_RESOURCE_PATH'
jq -e '(.batchSize // 1) >= 2' "$EXAMPLE_DIR/preview.json" >/dev/null
case "$WALLET_KIND" in
  oss) KEY_FIELD='keyId' ;;
  enterprise) KEY_FIELD='keyReference' ;;
  *) printf 'WALLET_KIND must be oss or enterprise\n' >&2; exit 1 ;;
esac
jq --slurpfile preview "$EXAMPLE_DIR/preview.json" \
  --arg field "$KEY_FIELD" --arg first "$HOLDER_1" --arg second "$HOLDER_2" \
  '. + {credentials:[$preview[0].offer.credentialConfigurationIds[] | {
    credentialConfigurationId:., holderBindings:[{($field):$first},{($field):$second}]
  }]}' "$EXAMPLE_DIR/receive-request.json" > "$EXAMPLE_DIR/batch-request.json"
mv "$EXAMPLE_DIR/batch-request.json" "$EXAMPLE_DIR/receive-request.json"
```

This selects all offered configurations. To select a subset, keep only the desired
configuration entries. For two datasets under one configuration, use one configuration
selection: the wallet expands the issuer's token `credential_identifiers` into two targets.
With two holder bindings, it sends two Credential Endpoint requests containing two proofs
each. Never invent dataset identifiers or derive them from profile IDs.

## 3A. Receive a pre-authorized offer

Use this branch when `AUTH_METHOD=PRE_AUTHORIZED`. If the profile requires a transaction
code, obtain it through the issuer's separate delivery channel and add `txCode` to the
request. Do not send one when the offer does not request it.

```bash
curl --silent --show-error --fail-with-body "${WALLET_AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary @"$EXAMPLE_DIR/receive-request.json" \
  "$WALLET_BASE/credentials/receive" -o "$EXAMPLE_DIR/result.json"
jq '{credentialIds,deferredCredentials,failure,storageOutcome}' "$EXAMPLE_DIR/result.json"
```

Enterprise also exposes `/credentials/receive/pre-authorized` with equivalent semantics.

## 3B. Receive an authorization-code offer

Use a fresh offer created with `AUTH_METHOD=AUTHORIZED`. Preserve the chosen selections,
client ID, redirect URI, returned state and PKCE verifier for the entire browser continuation.
The redirect URI must be registered with the authorization server where registration is required.

```bash
CLIENT_ID='wallet-client'
REDIRECT_URI='https://your-wallet.example/callback'
jq --slurpfile request "$EXAMPLE_DIR/receive-request.json" \
  --arg client "$CLIENT_ID" --arg redirect "$REDIRECT_URI" \
  '{offerUrl:$request[0].offerUrl,clientId:$client,redirectUri:$redirect,
    credentialConfigurationIds:($request[0].credentials //
      [.offer.credentialConfigurationIds[]|{credentialConfigurationId:.}] | map(.credentialConfigurationId))}' \
  "$EXAMPLE_DIR/preview.json" > "$EXAMPLE_DIR/authorization-request.json"
curl --silent --show-error --fail-with-body "${WALLET_AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary @"$EXAMPLE_DIR/authorization-request.json" \
  "$WALLET_BASE/credentials/receive/authorization-url/batch" -o "$EXAMPLE_DIR/authorization.json"
jq -r '.authorizationUrl' "$EXAMPLE_DIR/authorization.json"
```

Open that URL and complete issuer authentication/consent. Your callback handler must validate
its redirect binding, returned state and any required authorization-server `iss` before
accepting the code. Set `AUTHORIZATION_CODE` from that **validated** callback; do not redeem
an arbitrary query parameter or reuse the code for an isolated token request first.

```bash
: "${AUTHORIZATION_CODE:?Set the code from the validated browser callback}"
jq --slurpfile request "$EXAMPLE_DIR/receive-request.json" \
  --slurpfile authorization "$EXAMPLE_DIR/authorization.json" \
  --arg code "$AUTHORIZATION_CODE" --arg client "$CLIENT_ID" --arg redirect "$REDIRECT_URI" \
  '{code:$code,codeVerifier:$authorization[0].codeVerifier,clientId:$client,redirectUri:$redirect,
    credentialIssuer:.offer.credentialIssuer,credentialEndpoint:.offer.credentialEndpoint,nonceEndpoint:.offer.nonceEndpoint,
    credentials:($request[0].credentials // [.offer.credentialConfigurationIds[]|{credentialConfigurationId:.}])}' \
  "$EXAMPLE_DIR/preview.json" > "$EXAMPLE_DIR/authorized-receive-request.json"
curl --silent --show-error --fail-with-body "${WALLET_AUTH[@]}" \
  -H 'Content-Type: application/json' --data-binary @"$EXAMPLE_DIR/authorized-receive-request.json" \
  "$WALLET_BASE/credentials/receive/authorized/batch" -o "$EXAMPLE_DIR/result.json"
jq '{credentialIds,deferredCredentials,failure,storageOutcome}' "$EXAMPLE_DIR/result.json"
```

Authorization-code requests require advertised `authorization_details` support or configuration scopes.
Pre-authorized codes already authorize the offered credentials and work without either selector;
available selectors narrow the token request. Each copy must use a distinct stored holder key.
The batch authorization endpoint negotiates automatically; the single-configuration endpoint retains `useScope`. For deployments requiring DPoP end to end, use the
same wallet sender key throughout authorization and set `useDpop:true` on authorized receive;
copy-holder selections do not replace that sender key.

## 4. Keep partial results and resume pending targets

A successful receive may include immediately stored IDs and pending targets together.
HTTP 207 means progress was retained alongside a failure; curl treats it as success, so
inspect `failure` and `storageOutcome` instead of assuming every target completed. HTTP
4xx/5xx bodies remain in the result file because these commands use `--fail-with-body`.
Do not restart the whole offer to retry a local save or redeem a spent grant again.

```bash
curl --silent --show-error --fail-with-body "${WALLET_AUTH[@]}" \
  "$WALLET_BASE/credentials/receive/deferred" -o "$EXAMPLE_DIR/pending.json"
jq '.[] | {id,credentialConfigurationId,credentialIdentifier,intervalSeconds}' "$EXAMPLE_DIR/pending.json"
```

Choose one returned `id` as `DEFERRED_ID`. Wait its `intervalSeconds` before a remote poll.
An absent/null interval means the response is already local and storage can be retried now.
High-level resumption enforces the remote deadline across restarts; an early call returns
`type:"deferred"` with remaining seconds without contacting the issuer.

```bash
: "${DEFERRED_ID:?Choose one retained handle from pending.json}"
ENCODED_DEFERRED_ID=$(jq -rn --arg value "$DEFERRED_ID" '$value|@uri')
curl --silent --show-error --fail-with-body "${WALLET_AUTH[@]}" -X POST \
  "$WALLET_BASE/credentials/receive/deferred/$ENCODED_DEFERRED_ID" \
  -o "$EXAMPLE_DIR/resume-result.json"
jq . "$EXAMPLE_DIR/resume-result.json"
```

Merge stored IDs from every outcome into your existing results. A `deferred` outcome keeps
pending handles; `stored` finishes the selected handle; `failed` preserves the reported
progress and explains the stopped target. `REMOTE_OUTCOME_UNCERTAIN` and
`STORAGE_OUTCOME_UNCERTAIN` prohibit automatic replay/takeover; keep the handle and IDs for
reconciliation. Different pending targets are resumed separately, without resending tokens.

Reload a stored credential with `GET $WALLET_BASE/credentials/{credentialId}`. Each credential
retains its holder-key association. For presentation, select its ID through the public
presentation endpoints and omit a signing-key override so the wallet uses that association.
