import assert from "node:assert/strict";
import { test } from "node:test";
import {
  REDIRECT_URI_CLIENT_ID_PREFIX,
  prefixedCoreFlowClientId,
  requiresClientIdValue,
} from "./coreFlowClientId.ts";

test("blank redirect_uri sends the bare prefix so verifier2 can bind response_uri", () => {
  assert.equal(
    prefixedCoreFlowClientId("redirect_uri", "  "),
    REDIRECT_URI_CLIENT_ID_PREFIX,
  );
});

test("explicit redirect_uri is prefixed", () => {
  assert.equal(
    prefixedCoreFlowClientId(
      "redirect_uri",
      "https://verifier.example.com/callback",
    ),
    "redirect_uri:https://verifier.example.com/callback",
  );
});

test("signed requests cannot auto-generate redirect_uri", () => {
  assert.throws(
    () =>
      prefixedCoreFlowClientId("redirect_uri", "", { signedRequest: true }),
    /Signed requests cannot auto-generate/,
  );
});

test("other prefixed types still require a value", () => {
  assert.throws(
    () => prefixedCoreFlowClientId("x509_san_dns", " "),
    /x509_san_dns requires a value/,
  );
  assert.equal(
    prefixedCoreFlowClientId("pre_registered", "my-verifier"),
    "my-verifier",
  );
});

test("redirect_uri is optional in the form, other types are not", () => {
  assert.equal(requiresClientIdValue("redirect_uri"), false);
  assert.equal(requiresClientIdValue("x509_hash"), false);
  assert.equal(requiresClientIdValue("x509_san_dns"), true);
});
