import assert from "node:assert/strict";
import { test } from "node:test";
import {
  annexCRequestedElements,
  claimsFromIssuerMetadata,
  dcqlCredentialQueryId,
  dcqlFromIssuerMetadata,
  defaultSelectedClaimIds,
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

test("PID defaults to family name, given name, and birth date", () => {
  const configuration = {
    format: "mso_mdoc",
    doctype: "eu.europa.ec.eudi.pid.1",
    credential_metadata: {
      claims: [
        { path: ["eu.europa.ec.eudi.pid.1", "family_name"] },
        { path: ["eu.europa.ec.eudi.pid.1", "given_name"] },
        { path: ["eu.europa.ec.eudi.pid.1", "birth_date"] },
        { path: ["eu.europa.ec.eudi.pid.1", "age_over_18"] },
        { path: ["eu.europa.ec.eudi.pid.1", "nationality"] },
      ],
    },
  };

  assert.deepEqual(
    defaultSelectedClaimIds(configuration, "eu.europa.ec.eudi.pid.1"),
    [
      "eu.europa.ec.eudi.pid.1.family_name",
      "eu.europa.ec.eudi.pid.1.given_name",
      "eu.europa.ec.eudi.pid.1.birth_date",
    ],
  );
});

test("mDL exact age can be selected but is omitted from default requests", () => {
  const configuration = {
    format: "mso_mdoc",
    doctype: "org.iso.18013.5.1.mDL",
    credential_metadata: {
      claims: [
        { path: ["org.iso.18013.5.1", "family_name"] },
        { path: ["org.iso.18013.5.1", "portrait"] },
        { path: ["org.iso.18013.5.1", "age_in_years"] },
      ],
    },
  };

  assert.deepEqual(defaultSelectedClaimIds(configuration, "isoMdl"), [
    "org.iso.18013.5.1.family_name",
    "org.iso.18013.5.1.portrait",
  ]);
  assert.ok(claimsFromIssuerMetadata(configuration).some(
    (claim) => claim.id === "org.iso.18013.5.1.age_in_years",
  ));
});

const pidPaths = [
  ["family_name"], ["given_name"], ["birthdate"], ["address", "locality"], ["place_of_birth", "country"],
];
const legacyPidPaths = [
  ["family_name"], ["given_name"], ["birth_date"], ["resident_city"], ["birth_place", "country"],
];

for (const { label, vct, paths, birthId } of [
  { label: "current metadata", vct: "urn:eudi:pid:1", paths: pidPaths, birthId: "birthdate" },
  {
    label: "legacy claim names",
    vct: "urn:eudi:pid:1",
    paths: legacyPidPaths,
    birthId: "birth_date",
  },
  {
    label: "the legacy demo VCT",
    vct: "https://issuer2.demo.walt.id/openid4vci/urn:eudi:pid:1",
    paths: legacyPidPaths,
    birthId: "birth_date",
  },
]) {
  test(`SD-JWT PID uses rulebook paths with ${label}`, () => {
    const configuration = {
      format: "dc+sd-jwt",
      vct,
      credential_metadata: { claims: paths.map((path) => ({ path })) },
    };
    assert.deepEqual(defaultSelectedClaimIds(configuration, "pid-sd-jwt"), ["family_name", "given_name", birthId]);
    const query = dcqlFromIssuerMetadata("pid-sd-jwt", configuration, claimsFromIssuerMetadata(configuration));
    assert.deepEqual(query.meta, { vct_values: [vct] });
    assert.deepEqual(query.claims.map((claim) => claim.path), pidPaths);
  });
}

test("custom SD-JWT VCTs retain their advertised paths and defaults", () => {
  const paths = [["birth_date"], ["resident_city"]];
  for (const [id, vct] of [
    ["custom-pid", "urn:eudi:pid:10"],
    ["urn:eudi:pid:1", "urn:eudi:pid:10"],
    ["custom-pid", "https://issuer.example/openid4vci/urn:eudi:pid:1"],
  ] as const) {
    const configuration = {
      format: "dc+sd-jwt",
      vct,
      credential_metadata: { claims: paths.map((path) => ({ path })) },
    };
    const query = dcqlFromIssuerMetadata(id, configuration, claimsFromIssuerMetadata(configuration));
    assert.deepEqual(query.claims.map((claim) => claim.path), paths, vct);
    assert.deepEqual(defaultSelectedClaimIds(configuration, id), ["birth_date", "resident_city"], vct);
  }
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
