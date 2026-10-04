import assert from "node:assert/strict";
import { test } from "node:test";
import {
  DEFAULT_CLIENT_ID_TYPE,
  REDIRECT_URI_CLIENT_ID_PREFIX,
  SIGNED_REQUEST_DEFAULT_CLIENT_ID_TYPE,
  clientIdRequiresX5c,
  clientIdTypeForSignedRequest,
  isClientIdAllowedForSignedRequest,
  prefixedCoreFlowClientId,
  requiresClientIdValue,
  signedRequestClientIdError,
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

test("signed requests cannot use redirect_uri, blank or explicit", () => {
  assert.equal(
    signedRequestClientIdError("redirect_uri", true),
    "Signed requests cannot use the redirect_uri client_id prefix",
  );
  assert.throws(
    () =>
      prefixedCoreFlowClientId("redirect_uri", "", { signedRequest: true }),
    /Signed requests cannot use the redirect_uri client_id prefix/,
  );
  assert.throws(
    () =>
      prefixedCoreFlowClientId(
        "redirect_uri",
        "https://verifier.example.com/callback",
        { signedRequest: true },
      ),
    /Signed requests cannot use the redirect_uri client_id prefix/,
  );
});

test("signed requests allow x509, DID, attestation, and pre-registered client IDs", () => {
  assert.equal(isClientIdAllowedForSignedRequest("redirect_uri"), false);
  assert.equal(isClientIdAllowedForSignedRequest("x509_hash"), true);
  assert.equal(isClientIdAllowedForSignedRequest("x509_san_dns"), true);
  assert.equal(
    isClientIdAllowedForSignedRequest("decentralized_identifier"),
    true,
  );
  assert.equal(isClientIdAllowedForSignedRequest("verifier_attestation"), true);
  assert.equal(isClientIdAllowedForSignedRequest("pre_registered"), true);
  assert.equal(
    prefixedCoreFlowClientId("x509_san_dns", "verifier.example.com", {
      signedRequest: true,
    }),
    "x509_san_dns:verifier.example.com",
  );
});

test("x509 client IDs require an x5c chain", () => {
  assert.equal(clientIdRequiresX5c("x509_hash"), true);
  assert.equal(clientIdRequiresX5c("x509_san_dns"), true);
  assert.equal(clientIdRequiresX5c("redirect_uri"), false);
  assert.equal(clientIdRequiresX5c("decentralized_identifier"), false);
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

test("redirect_uri is the unsigned default and does not require a value", () => {
  assert.equal(DEFAULT_CLIENT_ID_TYPE, "redirect_uri");
  assert.equal(SIGNED_REQUEST_DEFAULT_CLIENT_ID_TYPE, "x509_hash");
  assert.equal(requiresClientIdValue("redirect_uri"), false);
  assert.equal(requiresClientIdValue("x509_hash"), false);
  assert.equal(requiresClientIdValue("x509_san_dns"), true);
});

test("unsigned sessions switch back to redirect_uri", () => {
  assert.equal(
    clientIdTypeForSignedRequest(false, "x509_hash"),
    "redirect_uri",
  );
  assert.equal(
    clientIdTypeForSignedRequest(false, "x509_san_dns"),
    "redirect_uri",
  );
  assert.equal(
    clientIdTypeForSignedRequest(false, "decentralized_identifier"),
    "redirect_uri",
  );
});

test("signed sessions keep a signable type and replace redirect_uri", () => {
  assert.equal(
    clientIdTypeForSignedRequest(true, "redirect_uri"),
    "x509_hash",
  );
  assert.equal(
    clientIdTypeForSignedRequest(true, "x509_san_dns"),
    "x509_san_dns",
  );
});
