import assert from "node:assert/strict";
import { test } from "node:test";
import { paymentDraft, paymentPayload, paymentExample, parsePaymentJson, stringifyPaymentJson,
  PAYMENT_FIELDS, PAYMENT_FREQUENCIES, assertPaymentTransactionFields } from "./ts12Payment.ts";

test("payment editor and request serialization preserve independent numeric tokens", () => {
  const input = '{"transaction_id":"test","payee":{"name":"Shop","id":"merchant"},"currency":"EUR","amount":9007199254740993.01,"recurrence":{"frequency":"MNTH","number":2e0,"mit_options":{"apr":0.1234567890123456789,"max_amount":9007199254740993.01}}}';
  const edited = paymentPayload(paymentDraft(input));
  const request = stringifyPaymentJson({ openid: { transactionData: [{ type: "urn:eudi:sca:payment:1", payload: parsePaymentJson(edited) }] } });
  const proxyRequest = stringifyPaymentJson(parsePaymentJson(request));
  assert.match(proxyRequest, /"amount":9007199254740993\.01/);
  assert.match(proxyRequest, /"apr":0\.1234567890123456789/);
  assert.match(proxyRequest, /"number":2e0/);
  assert.equal(stringifyPaymentJson(parsePaymentJson('{"amount":-0}')), '{"amount":-0}');
  assert.equal(PAYMENT_FIELDS.length, 26);
  assert.equal(Object.keys(PAYMENT_FREQUENCIES).length, 12);
});

test("optional presence distinguishes absent, false and zero, preserving inactive drafts", () => {
  const draft = paymentDraft(paymentExample("recurring", new Date("2026-11-04T10:00:00Z")));
  draft.values["amount_earmarked"] = "false";
  draft.values["recurrence.mit_options.total_amount"] = "0";
  draft.enabled["date_time"] = false;
  draft.mode = "unscheduled";
  const ordinary = parsePaymentJson(paymentPayload(draft));
  assert.equal(ordinary.amount_earmarked, false);
  assert.equal(ordinary.recurrence, undefined);
  assert.equal(ordinary.execution_date, undefined);
  assert.equal(ordinary.date_time, undefined);
  draft.mode = "recurring";
  assert.equal(parsePaymentJson(paymentPayload(draft)).recurrence.mit_options.total_amount, 0);
});

test("scheduled and recurring examples have mutually exclusive branches and fresh timestamps", () => {
  const now = new Date();
  const scheduled = parsePaymentJson(paymentExample("scheduled", now));
  const recurring = parsePaymentJson(paymentExample("recurring", now));
  assert.ok(Date.parse(scheduled.execution_date) > now.getTime());
  assert.equal(scheduled.recurrence, undefined);
  assert.equal(recurring.execution_date, undefined);
  assert.equal(recurring.recurrence.frequency, "MNTH");
  assert.equal(Object.keys(recurring.recurrence.mit_options).length, 7);
});

test("unknown fields, malformed JSON and invalid imported types never disappear through form import", () => {
  for (const raw of ["{", '{"unknown":1}', '{"amount":"1"}', '{"amount":null}', '{"payee":[]}',
    '{"recurrence":{"frequency":"MNTH","unknown":1}}',
    '{"execution_date":"2099-01-01T00:00:00Z","recurrence":{"frequency":"MNTH"}}']) {
    assert.throws(() => paymentDraft(raw), undefined, raw);
  }
});

test("invalid numeric drafts are rejected without coercion", () => {
  const draft = paymentDraft(paymentExample("recurring"));
  for (const count of ["2.1", "1.00e-1", "1e-2", "true", ""]) {
    draft.values["recurrence.number"] = count;
    assert.throws(() => paymentPayload(draft), undefined, count);
  }
  for (const count of ["2", "2.0", "2e0", "10e-1", "0e-100"]) {
    draft.values["recurrence.number"] = count;
    assert.doesNotThrow(() => paymentPayload(draft));
  }
});

test("frequency selection rejects names inherited from JavaScript objects", () => {
  const draft = paymentDraft(paymentExample("recurring"));
  for (const code of ["toString", "constructor", "__proto__"]) {
    draft.values["recurrence.frequency"] = code;
    assert.throws(() => paymentPayload(draft), /supported payment frequency/);
  }
});

test("incomplete PISP, timestamps and Unicode ID boundaries are checked in the form", () => {
  const draft = paymentDraft(paymentExample("unscheduled"));
  draft.pisp = true;
  assert.throws(() => paymentPayload(draft), /PISP legal name/);
  draft.pisp = false;
  draft.values.transaction_id = "😀".repeat(36);
  assert.doesNotThrow(() => paymentPayload(draft));
  draft.values.transaction_id += "😀";
  assert.throws(() => paymentPayload(draft), /36 characters/);
});


test("literal dotted members cannot alias supported nested payment fields", () => {
  for (const extra of [
    { "payee.name": "Replacement" },
    { recurrence: { frequency: "MNTH", "mit_options.min_amount": 1 } },
    { "recurrence.mit_options": {} },
  ]) {
    const original = stringifyPaymentJson({ ...parsePaymentJson(paymentExample("unscheduled")), ...extra });
    assert.throws(() => paymentDraft(original), /Unsupported field/);
  }
});

test("full-request payment imports reject unsupported entry fields without dropping the draft", () => {
  const entry = { type: "urn:eudi:sca:payment:1", credential_ids: ["payment"],
    payload: parsePaymentJson(paymentExample("unscheduled")), future_extension: "keep-this-draft" };
  const original = stringifyPaymentJson(entry);
  assert.throws(() => assertPaymentTransactionFields(entry), /future_extension/);
  assert.equal(stringifyPaymentJson(entry), original);
  assert.doesNotThrow(() => assertPaymentTransactionFields({ ...entry, type: "org.example.custom" }));
  const { future_extension, ...supported } = entry;
  assert.doesNotThrow(() => assertPaymentTransactionFields(supported));
});


test("all timestamp fields preserve RFC 3339 offsets, fractions and known leap seconds", () => {
  const now = Date.parse("2016-01-01T00:00:00Z");
  for (const path of ["date_time", "execution_date", "recurrence.start_date", "recurrence.end_date"]) {
    for (const value of ["2016-12-31T23:59:60Z", "2017-01-01T00:59:60+01:00",
      "2016-12-31t23:59:60.1234567891z", "2099-01-01T12:00:00+23:00", "2099-01-01T00:00:00.1234567891Z"]) {
      const draft = paymentDraft(paymentExample(path.startsWith("recurrence.") ? "recurring" : "unscheduled", new Date(now)));
      if (path === "execution_date") draft.mode = "scheduled";
      draft.enabled[path] = true;
      draft.values[path] = value;
      const payload = parsePaymentJson(paymentPayload(draft, now));
      const actual = path.split(".").reduce((object, key) => object[key], payload);
      assert.equal(actual, value, path);
    }
  }
});

test("timestamp fields reject calendar rollover and invalid RFC 3339 bounds", () => {
  for (const path of ["date_time", "execution_date", "recurrence.start_date", "recurrence.end_date"]) {
    const draft = paymentDraft(paymentExample(path.startsWith("recurrence.") ? "recurring" : "unscheduled"));
    if (path === "execution_date") draft.mode = "scheduled";
    draft.enabled[path] = true;
    for (const value of ["2099-02-30T00:00:00Z", "2100-02-29T00:00:00Z", "2099-13-01T00:00:00Z",
      "2099-01-00T00:00:00Z", "2099-01-01T24:00:00Z", "2099-01-01T00:60:00Z",
      "2099-01-01T00:00:00+24:00", "2099-01-01T00:00:00+01:60",
      "2016-12-30T23:59:60Z", "2016-12-31T12:34:60Z"]) {
      draft.values[path] = value;
      assert.throws(() => paymentPayload(draft), /date, time and UTC offset/, path + ": " + value);
    }
    if (path !== "execution_date") {
      for (const value of ["2000-02-29T00:00:00Z", "0000-02-29T00:00:00Z"]) {
        draft.values[path] = value;
        assert.doesNotThrow(() => paymentPayload(draft));
      }
    }
  }
});

test("scheduled leap seconds use the server's conservative deadline boundary", () => {
  const draft = paymentDraft(paymentExample("unscheduled"));
  draft.mode = "scheduled";
  const midnight = Date.parse("2017-01-01T00:00:00Z");
  for (const value of ["2016-12-31T23:59:60.1234567891Z", "2017-01-01T00:59:60.9+01:00"]) {
    draft.values.execution_date = value;
    assert.doesNotThrow(() => paymentPayload(draft, midnight - 1));
    assert.doesNotThrow(() => paymentPayload(draft, midnight));
    assert.throws(() => paymentPayload(draft, midnight + 1), /must not lie in the past/);
  }
});
