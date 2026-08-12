/** Field name used by EUDI TS-12 SCA payment (`urn:eudi:sca:payment:1`). */
export const NESTED_TRANSACTION_DATA_FIELDS = new Set(["payload"]);

export const DEFAULT_SCA_PAYMENT_PAYLOAD = {
  transaction_id: "8D8AC610-566D-4EF0-9C22-186B2A5ED793",
  payee: {
    name: "Super Store",
    id: "merchant-001",
  },
  currency: "EUR",
  amount: 11.56,
} as const;

export function isNestedTransactionDataField(field: string): boolean {
  return NESTED_TRANSACTION_DATA_FIELDS.has(field);
}

export function defaultTransactionFieldValue(
  field: string,
  profileType?: string,
): string {
  if (field === "payload") {
    return JSON.stringify(DEFAULT_SCA_PAYMENT_PAYLOAD, null, 2);
  }
  return "";
}

/**
 * Converts a UI string into the value to embed in OpenID4VP transaction_data.
 * Nested fields (e.g. `payload`) must be JSON objects, not quoted strings.
 */
export function parseTransactionFieldValue(
  field: string,
  raw: string,
): unknown {
  const trimmed = raw.trim();
  if (!isNestedTransactionDataField(field)) {
    return raw;
  }

  if (!trimmed) {
    throw new Error(`Transaction data field "${field}" must be a JSON object.`);
  }

  let parsed: unknown;
  try {
    parsed = JSON.parse(trimmed);
  } catch {
    throw new Error(
      `Transaction data field "${field}" must be valid JSON (got a flat string editor value that does not parse).`,
    );
  }

  if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
    throw new Error(
      `Transaction data field "${field}" must be a JSON object (nested payload).`,
    );
  }

  return parsed;
}
