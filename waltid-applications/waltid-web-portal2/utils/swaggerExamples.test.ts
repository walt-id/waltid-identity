import assert from "node:assert/strict";
import { test } from "node:test";
import { useSwaggerExamples } from "../composables/useSwaggerExamples.ts";
import { parsePaymentJson, stringifyPaymentJson } from "./ts12Payment.ts";

test("OpenAPI object and encoded-string examples retain exact payment tokens through initial request serialization", async (t) => {
  const payload = '{"openid":{"transactionData":[{"type":"urn:eudi:sca:payment:1","payload":{"amount":9007199254740993.01}}]}}';
  const spec = JSON.stringify({ paths: { "/verification-session/create": { post: {
    requestBody: { content: { "application/json": { examples: {
      object: { value: "OBJECT_PAYLOAD" }, string: { value: payload },
    } } } },
  } } } }).replace('"OBJECT_PAYLOAD"', payload);
  t.mock.method(globalThis, "fetch", async () => new Response(spec, { headers: { "content-type": "application/json" } }));
  const swagger = useSwaggerExamples("https://verifier.example", "/verification-session/create", { parseJson: parsePaymentJson });
  await swagger.load();
  assert.equal(swagger.error.value, null);
  assert.equal(swagger.examples.value.length, 2);
  for (const example of swagger.examples.value) {
    const initialRequest = stringifyPaymentJson(example.payload, 2);
    assert.match(initialRequest, /"amount": 9007199254740993\.01/);
    assert.doesNotMatch(initialRequest, /9007199254740994/);
  }
});
