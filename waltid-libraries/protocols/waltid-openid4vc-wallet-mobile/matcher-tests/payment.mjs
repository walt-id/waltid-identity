import fs from "node:fs";
import crypto from "node:crypto";
import assert from "node:assert/strict";

// Execute the actual vendored WASM and assert its Credential Manager ABI output.
// These are ABI observations, not an emulation of Credential Manager rendering.
const wasmPath =
  process.argv[2] ??
  new URL(
    "../src/androidMain/assets/id/walt/wallet2/mobile/openid4vpmatcher.wasm",
    import.meta.url,
  );
const bytes = fs.readFileSync(wasmPath);
const module = new WebAssembly.Module(bytes);
const b64 = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
const field = (value, label) => ({
  value,
  display: { verification: { display: label, display_value: String(value) } },
});
const registry = {
  supported_protocols: ["openid4vp-v1-signed", "openid4vp-v1-unsigned"],
  credentials: {
    "dc+sd-jwt": {
      "https://issuer.example/payment": [
        {
          id: "payment-credential",
          display: {
            verification: {
              title: "Example Bank card",
              subtitle: "Card ending 1234",
              explainer: "AUTHENTICATED EXPLAINER",
              warning: "REGISTERED WARNING",
              metadata_display_text: "REGISTERED METADATA",
            },
          },
          paths: {
            cardholder: field("Ada Example", "Cardholder"),
            account: field("1234", "Account"),
          },
        },
      ],
      "https://issuer.example/age": [
        {
          id: "age-credential",
          display: {
            verification: { title: "Age attestation", subtitle: "Age check" },
          },
          paths: { age_over_18: field(true, "Over 18") },
        },
      ],
    },
  },
};
const query = {
  credentials: [
    {
      id: "pay",
      format: "dc+sd-jwt",
      meta: { vct_values: ["https://issuer.example/payment"] },
      claims: [{ path: ["cardholder"] }, { path: ["account"] }],
    },
    {
      id: "age",
      format: "dc+sd-jwt",
      meta: { vct_values: ["https://issuer.example/age"] },
      claims: [{ path: ["age_over_18"] }],
    },
  ],
};
const transaction = (payload) => ({
  type: "urn:eudi:sca:payment:1",
  credential_ids: ["pay"],
  require_cryptographic_holder_binding: true,
  payload,
});
const payload = {
  transaction_id: "local-research-payment-1",
  payee: { name: "ACME Corp", id: "merchant-42" },
  amount: 42,
  currency: "EUR",
};
function execute(name, data, protocol = "openid4vp-v1-unsigned") {
  const request = Buffer.from(
    JSON.stringify({
      requests: [
        {
          protocol,
          data: protocol.endsWith("-signed")
            ? {
                request:
                  b64({ alg: "ES256" }) +
                  "." +
                  b64(data) +
                  ".deliberately-invalid-signature",
              }
            : data,
        },
      ],
    }),
  );
  const json = Buffer.from(JSON.stringify(registry));
  const header = Buffer.alloc(4);
  header.writeUInt32LE(4);
  const credentials = Buffer.concat([header, json]);
  const calls = [];
  let instance;
  const mem = () => new Uint8Array(instance.exports.memory.buffer);
  const cstr = (p) =>
    p === 0
      ? ""
      : Buffer.from(mem().subarray(p, mem().indexOf(0, p))).toString();
  const put = (p, n) =>
    new DataView(instance.exports.memory.buffer).setUint32(p, n, true);
  const host = {
    GetCredentialsSize: (p) => put(p, credentials.length),
    ReadCredentialsBuffer: (p, offset, length) => {
      mem().set(credentials.subarray(offset, offset + length), p);
      return Math.min(length, credentials.length - offset);
    },
    GetRequestSize: (p) => put(p, request.length),
    GetRequestBuffer: (p) => mem().set(request, p),
    GetWasmVersion: (p) => put(p, 9),
    AddEntrySet: (id, length) =>
      calls.push({ call: "set", id: cstr(id), length }),
    AddPaymentEntryToSetV2: (
      id,
      merchant,
      title,
      subtitle,
      icon,
      iconLength,
      amount,
      bank,
      bankLength,
      provider,
      providerLength,
      info,
      metadata,
      set,
      index,
    ) =>
      calls.push({
        call: "payment",
        id: cstr(id),
        merchant: cstr(merchant),
        title: cstr(title),
        subtitle: cstr(subtitle),
        amount: cstr(amount),
        additionalInfo: cstr(info),
        metadata: JSON.parse(cstr(metadata)),
        set: cstr(set),
        index,
      }),
    AddEntryToSet: (
      id,
      icon,
      iconLength,
      title,
      subtitle,
      disclaimer,
      warning,
      metadata,
      set,
      index,
    ) =>
      calls.push({
        call: "verification",
        id: cstr(id),
        title: cstr(title),
        disclaimer: cstr(disclaimer),
        warning: cstr(warning),
        metadata: JSON.parse(cstr(metadata)),
        set: cstr(set),
        index,
      }),
    AddFieldToEntrySet: (id, label, value, set, index) =>
      calls.push({
        call: "field",
        id: cstr(id),
        label: cstr(label),
        value: cstr(value),
        set: cstr(set),
        index,
      }),
    AddMetadataDisplayTextToEntrySet: (id, value, set, index) =>
      calls.push({
        call: "metadataText",
        id: cstr(id),
        value: cstr(value),
        set: cstr(set),
        index,
      }),
    AddInlineIssuanceEntry: () => calls.push({ call: "inlineIssuance" }),
    SetDelegationTypeForEntryInSet: () => {},
  };
  const imports = {
    credman: host,
    credman_v2: host,
    credman_v5: host,
    wasi_snapshot_preview1: {
      fd_close: () => 0,
      fd_fdstat_get: () => 0,
      fd_seek: () => 0,
      fd_write: (fd, iovs, count, nwritten) => {
        let length = 0;
        const v = new DataView(instance.exports.memory.buffer);
        for (let i = 0; i < count; i++)
          length += v.getUint32(iovs + i * 8 + 4, true);
        put(nwritten, length);
        return 0;
      },
      proc_exit: (code) => {
        if (code !== 0) throw new Error("WASI exit " + code);
      },
    },
  };
  for (const imp of WebAssembly.Module.imports(module)) {
    if (imp.kind === "function" && !imports[imp.module]?.[imp.name]) {
      imports[imp.module] ??= {};
      imports[imp.module][imp.name] = () => {
        throw new Error(
          "Unimplemented host call: " + imp.module + "." + imp.name,
        );
      };
    }
  }
  let error = null;
  try {
    instance = new WebAssembly.Instance(module, imports);
    instance.exports._start();
  } catch (e) {
    error = String(e);
  }
  return { name, protocol, input: data, calls, error };
}
const rule = (path, en, de, visualisation = 3) => ({
  path,
  mandatory: false,
  visualisation,
  labels: { en: { label: en }, de: { label: de } },
});
const document = {
  claims: [
    rule(["transaction_id"], "Reference", "Referenz"),
    rule(["payee", "name"], "Payee", "Empfänger", 1),
    rule(["payee", "id"], "Payee ID", "Empfänger-ID", 2),
    rule(["amount"], "Amount", "Betrag", 1),
    rule(["currency"], "Currency", "Währung", 4),
    rule(["sct_inst"], "Instant payment", "Sofortzahlung"),
    rule(["pisp", "legal_name"], "Payment service", "Zahlungsdienst"),
    rule(["pisp", "brand_name"], "Brand", "Marke"),
    rule(["pisp", "domain_name"], "Domain", "Domain"),
  ],
  credential_claims: [
    rule(["cardholder"], "Cardholder", "Karteninhaber"),
    rule(["account"], "Account", "Konto"),
  ],
  ui_labels: {
    affirmative_action_label: {
      en: "Authorize payment",
      de: "Zahlung freigeben",
    },
    transaction_title: { en: "Confirm this payment", de: "Zahlung bestätigen" },
    security_hint: {
      en: "Check the payee and amount.",
      de: "Empfänger und Betrag prüfen.",
    },
  },
};
registry.walt_payment_number_formats_v1 = {
  en: { decimal_separator: ".", zero_digit: "0" },
  "en-us": { decimal_separator: ".", zero_digit: "0" },
  de: { decimal_separator: ",", zero_digit: "0" },
  "de-at": { decimal_separator: ",", zero_digit: "0" },
};
registry.walt_payment_instructions_v1 = {
  "payment-credential": { document, locales: ["en-US"] },
};
const make = (td) => ({ dcql_query: query, transaction_data: td.map(b64) });
const results = [];
function run(name, data, protocol) {
  const r = execute(name, data, protocol);
  results.push(r);
  return r.calls;
}
const payment = (calls) => calls.find((c) => c.call === "payment");
for (const binding of [false, "true", null]) {
  assert.deepEqual(
    run("invalid-holder-binding-" + binding, make([
      { ...transaction(payload), require_cryptographic_holder_binding: binding },
    ])),
    [],
  );
}
const implicitBinding = transaction(payload);
delete implicitBinding.require_cryptographic_holder_binding;
assert.equal(payment(run("implicit-holder-binding", make([implicitBinding]))).amount, "EUR 42.00");
const unsigned = run(
  "authenticated-payment-plus-ordinary",
  make([transaction(payload)]),
);
assert.equal(unsigned.find((c) => c.call === "set").length, 2);
assert.equal(unsigned.filter((c) => c.call === "verification").length, 1);
assert.equal(payment(unsigned).amount, "EUR 42.00");
assert.equal(
  payment(unsigned).subtitle,
  [
    "Unsigned request. Check the payment details before confirming.",
    "Card ending 1234",
    "Confirm this payment",
    "Check the payee and amount.",
    "Reference: \u2068local-research-payment-1\u2069",
    "Payee: \u2068ACME Corp\u2069",
    "Payee ID: \u2068merchant-42\u2069",
    "Amount: \u2068EUR 42.00\u2069",
    "Cardholder: \u2068Ada Example\u2069",
    "Account: \u20681234\u2069",
  ].join("\n"),
);
assert.equal(payment(unsigned).metadata.dcql_cred_id, "pay");
assert.deepEqual(payment(unsigned).metadata.claims, [
  ["cardholder"],
  ["account"],
]);
const signed = run(
  "signed-wrapper-matcher-does-not-authenticate",
  make([transaction(payload)]),
  "openid4vp-v1-signed",
);
assert(!payment(signed).subtitle.includes("Unsigned request"));
for (const [token, currency, expected] of [
  ["1.234", "KWD", "KWD 1.234"],
  ["1.2345", "CLF", "CLF 1.2345"],
  ["9007199254740993.01", "EUR", "EUR 9007199254740993.01"],
  ["1.234e1", "EUR", "EUR 12.34"],
  ["42.000", "JPY", "JPY 42"],
]) {
  const raw = JSON.stringify(
    transaction({ ...payload, amount: "TOKEN", currency }),
  ).replace('"TOKEN"', token);
  const calls = run("exact-" + currency + "-" + token, {
    dcql_query: query,
    transaction_data: [Buffer.from(raw).toString("base64url")],
  });
  assert.equal(payment(calls)?.amount, expected);
}
registry.walt_payment_instructions_v1["payment-credential"].locales = ["de-AT"];
const optional = run(
  "german-optional",
  make([
    transaction({
      ...payload,
      sct_inst: false,
      pisp: {
        legal_name: "Service GmbH",
        brand_name: "Pay",
        domain_name: "pay.example",
      },
    }),
  ]),
);
assert.equal(payment(optional).amount, "EUR 42,00");
assert(payment(optional).subtitle.includes("Sofortzahlung: \u2068Nein\u2069"));
assert(
  payment(optional).subtitle.includes("Karteninhaber: \u2068Ada Example\u2069"),
);
assert(!payment(optional).subtitle.includes("Währung:"));
for (const locale of ["fr", "ar"]) {
  registry.walt_payment_instructions_v1["payment-credential"].locales = [
    locale,
  ];
  assert.equal(
    run("missing-language-" + locale, make([transaction(payload)])).length,
    0,
  );
}
registry.walt_payment_instructions_v1["payment-credential"].locales = ["en"];
const invalid = [
  ["multiple-transactions", [transaction(payload), transaction(payload)]],
  ["missing-amount", [transaction({ ...payload, amount: undefined })]],
  [
    "untrusted-display-override",
    [transaction({ ...payload, amount_display: "EUR 0.01" })],
  ],
  ["excess-precision", [transaction({ ...payload, amount: 1.234 })]],
  [
    "recurring-unsupported",
    [transaction({ ...payload, recurrence: { frequency: "MNTH" } })],
  ],
  [
    "scheduled-unsupported",
    [transaction({ ...payload, execution_date: "2099-01-01T00:00:00Z" })],
  ],
  [
    "unlabelled-payload-field",
    [transaction({ ...payload, amount_earmarked: false })],
  ],
];
for (const [name, transactions] of invalid)
  assert.equal(run(name, make(transactions)).length, 0, name);
const claimRules = document.credential_claims;
document.credential_claims = [];
assert.equal(
  run("missing-requested-attribute-label", make([transaction(payload)])).length,
  0,
);
document.credential_claims = claimRules;
const config = registry.walt_payment_instructions_v1;
delete registry.walt_payment_instructions_v1;
assert.equal(
  run("missing-authenticated-instructions", make([transaction(payload)]))
    .length,
  0,
);
assert.equal(
  run("ordinary-sharing-without-payment-metadata", make([])).filter(
    (c) => c.call === "field",
  ).length,
  3,
);
registry.walt_payment_instructions_v1 = config;
const paymentRecords = registry.credentials["dc+sd-jwt"]["https://issuer.example/payment"];
paymentRecords.unshift({ ...paymentRecords[0], id: "payment-without-instructions" });
const alternative = run("unlabelled-alternative-does-not-hide-valid-card", make([transaction(payload)]));
assert.deepEqual(alternative.filter((c) => c.call === "payment").map((c) => c.id), ["payment-credential"]);
assert.equal(alternative.filter((c) => c.call === "verification").length, 1);
paymentRecords.shift();
paymentRecords[0].paths.amount = field("99.00", "Account balance");
document.credential_claims.push(rule(["amount"], "Account balance", "Kontostand"));
query.credentials[0].claims.push({ path: ["amount"] });
const amountAttribute = run("credential-amount-is-not-transaction-amount", make([transaction(payload)]));
assert(payment(amountAttribute).subtitle.includes("Account balance: \u206899.00\u2069"));
assert(payment(amountAttribute).subtitle.includes("Amount: \u2068EUR 42.00\u2069"));
delete paymentRecords[0].paths.amount;
document.credential_claims.pop();
query.credentials[0].claims.pop();
const injection = run(
  "request-value-line-injection",
  make([
    transaction({
      ...payload,
      payee: { ...payload.payee, name: "Shop\nAmount: 0.01" },
    }),
  ]),
);
assert(payment(injection).subtitle.includes("Shop\\u000aAmount: 0.01"));
for (const result of results)
  assert.equal(
    result.error,
    null,
    result.name + " must return normally, never trap",
  );
console.log(
  JSON.stringify(
    {
      wasmSha256: crypto.createHash("sha256").update(bytes).digest("hex"),
      cases: results.length,
      results,
    },
    null,
    2,
  ),
);
