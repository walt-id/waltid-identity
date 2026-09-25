import assert from "node:assert/strict";
import { test } from "node:test";
import {
  annexCRequestedElements,
  claimsFromIssuerMetadata,
  dcqlCredentialQueryId,
  dcqlFromIssuerMetadata,
  isMdocConfiguration,
  isPidConfiguration,
} from "./dcqlFromIssuerMetadata.ts";

test("builds mdoc PID DCQL from issuer metadata claims only", () => {
  const configuration = {
    format: "mso_mdoc",
    doctype: "eu.europa.ec.eudi.pid.1",
    credential_metadata: {
      claims: [
        {
          path: ["eu.europa.ec.eudi.pid.1", "family_name"],
          display: [{ name: "Family name", locale: "en" }],
        },
        {
          path: ["eu.europa.ec.eudi.pid.1", "given_name"],
          display: [{ name: "Given name", locale: "en" }],
        },
        {
          path: ["eu.europa.ec.eudi.pid.1", "birth_date"],
          display: [{ name: "Birth date", locale: "en" }],
        },
      ],
    },
  };
  const claims = claimsFromIssuerMetadata(configuration);
  const query = dcqlFromIssuerMetadata(
    "eu.europa.ec.eudi.pid.1",
    configuration,
    claims.slice(0, 2),
  );

  assert.equal(isPidConfiguration(configuration, "eu.europa.ec.eudi.pid.1"), true);
  assert.equal(isMdocConfiguration(configuration), true);
  assert.deepEqual(query, {
    id: "eu-europa-ec-eudi-pid-1",
    format: "mso_mdoc",
    meta: { doctype_value: "eu.europa.ec.eudi.pid.1" },
    claims: [
      { path: ["eu.europa.ec.eudi.pid.1", "family_name"] },
      { path: ["eu.europa.ec.eudi.pid.1", "given_name"] },
    ],
  });
  assert.deepEqual(annexCRequestedElements(configuration, claims.slice(0, 2)), {
    "eu.europa.ec.eudi.pid.1": {
      "eu.europa.ec.eudi.pid.1": ["family_name", "given_name"],
    },
  });
});

test("builds SD-JWT identity DCQL from the published vct", () => {
  const configuration = {
    format: "dc+sd-jwt",
    vct: "https://issuer.example/openid4vci/identity_credential",
    credential_metadata: {
      claims: [
        { path: ["given_name"], display: [{ name: "Given name" }] },
        { path: ["address", "street_address"], display: [{ name: "Street address" }] },
        { path: ["is_over_18"], display: [{ name: "Over 18" }] },
      ],
    },
  };
  const claims = claimsFromIssuerMetadata(configuration);
  const query = dcqlFromIssuerMetadata("identity_credential", configuration, claims);

  assert.deepEqual(query.meta, {
    vct_values: ["https://issuer.example/openid4vci/identity_credential"],
  });
  assert.deepEqual(query.claims, [
    { path: ["given_name"] },
    { path: ["address", "street_address"] },
    { path: ["is_over_18"] },
  ]);
});

test("builds JWT Open Badge DCQL from credential_definition.type", () => {
  const configuration = {
    format: "jwt_vc_json",
    credential_definition: {
      type: ["VerifiableCredential", "OpenBadgeCredential"],
    },
    credential_metadata: {
      claims: [
        {
          path: ["credentialSubject", "achievement", "name"],
          display: [{ name: "Achievement name" }],
        },
      ],
    },
  };
  const claims = claimsFromIssuerMetadata(configuration);
  const query = dcqlFromIssuerMetadata(
    "OpenBadgeCredential_jwt_vc_json",
    configuration,
    claims,
  );

  assert.equal(isPidConfiguration(configuration, "OpenBadgeCredential_jwt_vc_json"), false);
  assert.deepEqual(query, {
    id: "OpenBadgeCredential_jwt_vc_json",
    format: "jwt_vc_json",
    meta: {
      type_values: [["VerifiableCredential", "OpenBadgeCredential"]],
    },
    claims: [
      {
        path: ["credentialSubject", "achievement", "name"],
      },
    ],
  });
});

test("ignores claims without a path and refuses an empty selection", () => {
  const configuration = {
    format: "dc+sd-jwt",
    vct: "https://issuer.example/openid4vci/identity_credential",
    credential_metadata: {
      claims: [{ display: [{ name: "Broken" }] }, { path: ["family_name"] }],
    },
  };
  const claims = claimsFromIssuerMetadata(configuration);
  assert.deepEqual(claims.map((claim) => claim.id), ["family_name"]);
  assert.throws(
    () => dcqlFromIssuerMetadata("identity_credential", configuration, []),
    /at least one claim/,
  );
});

test("sanitizes credential query ids to OpenID4VP identifier characters", () => {
  assert.equal(dcqlCredentialQueryId("urn:eudi:pid:1"), "urn-eudi-pid-1");
  assert.equal(
    dcqlCredentialQueryId("eu.europa.ec.eudi.pid.1"),
    "eu-europa-ec-eudi-pid-1",
  );
  assert.equal(
    dcqlCredentialQueryId("OpenBadgeCredential_jwt_vc_json"),
    "OpenBadgeCredential_jwt_vc_json",
  );
});
