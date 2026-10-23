import assert from "node:assert/strict";
import { test } from "node:test";
import {
  communityOfferBody,
  enterpriseOfferBody,
  enterpriseOfferUrl,
  resolveOfferForward,
} from "./offerRouting.ts";
import { applyVerificationPolicy } from "./verificationRouting.ts";
import { getWallet } from "./walletCatalog.ts";

test("France Identité stays verification-only", () => {
  assert.equal(getWallet("france-identite").issuance, false);
  assert.equal(getWallet("eudi-reference-wallet").issuance, true);
  assert.equal(getWallet("d-you").issuance, true);
});

test("community offers drop wallet selection and issuer configuration secrets", () => {
  const body = communityOfferBody({
    walletId: "walt-id",
    profileId: "openBadge",
    authMethod: "PRE_AUTHORIZED",
    signedMetadata: { signingMethod: { type: "x509-chain" } },
    runtimeOverrides: { credentialData: { name: "Ada" }, keyAttestationConfig: {} },
  });

  assert.equal(body.walletId, undefined);
  assert.equal(body.signedMetadata, undefined);
  assert.deepEqual(body.runtimeOverrides, { credentialData: { name: "Ada" } });
  assert.equal(body.profileId, "openBadge");
});

test("enterprise offers keep the offer contract and select the wallet issuer profile", () => {
  process.env.NUXT_ES_BASE_URL = "https://issuer.example";
  process.env.NUXT_ES_API_KEY = "test-token";
  process.env.NUXT_ES_DYOU_ISSUER_TARGET = "waltid.portal-wallets.d-you";

  const forward = resolveOfferForward({
    walletId: "d-you",
    profileId: "pid-mdoc",
    authMethod: "PRE_AUTHORIZED",
    clientAuthenticationConfig: { supportedMethods: [] },
    runtimeOverrides: { credentialData: { given_name: "Ada" } },
  });

  assert.equal(
    forward.url,
    enterpriseOfferUrl("waltid.portal-wallets.d-you", "pid-mdoc"),
  );
  assert.equal(forward.authorization, "Bearer test-token");
  assert.deepEqual(forward.body, enterpriseOfferBody({
    authMethod: "PRE_AUTHORIZED",
    runtimeOverrides: { credentialData: { given_name: "Ada" } },
  }));
  assert.equal("clientAuthenticationConfig" in forward.body, false);
});

test("missing enterprise issuer target fails before the open issuer is used", () => {
  process.env.NUXT_ES_BASE_URL = "https://issuer.example";
  process.env.NUXT_ES_API_KEY = "test-token";
  delete process.env.NUXT_ES_FRANCE_IDENTITE_ISSUER_TARGET;

  assert.throws(
    () =>
      resolveOfferForward({
        walletId: "france-identite",
        profileId: "pid-mdoc",
        authMethod: "PRE_AUTHORIZED",
      }),
    /NUXT_ES_FRANCE_IDENTITE_ISSUER_TARGET is not configured/,
  );
});

test("verification policy strips client crypto and follows the wallet", () => {
  const signed = applyVerificationPolicy(
    {
      walletId: "d-you",
      materialId: "german-eudi-wallet",
      flow_type: "cross_device",
      core_flow: {
        key: { d: "secret" },
        x5c: ["cert"],
        clientId: "from-browser",
        dcql_query: { credentials: [] },
      },
    },
    getWallet("d-you"),
  );
  const core = signed.core_flow as Record<string, unknown>;
  assert.equal(core.key, undefined);
  assert.equal(core.x5c, undefined);
  assert.equal(core.signed_request, true);
  assert.equal(core.encrypted_response, true);

  const unsigned = applyVerificationPolicy(
    {
      walletId: "walt-id",
      flow_type: "cross_device",
      core_flow: { signed_request: true, encrypted_response: true, key: { d: "x" } },
    },
    getWallet("walt-id"),
  );
  const openCore = unsigned.core_flow as Record<string, unknown>;
  assert.equal(openCore.signed_request, undefined);
  assert.equal(openCore.key, undefined);
});
