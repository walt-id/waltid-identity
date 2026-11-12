import { LosslessNumber, parse, stringify } from "lossless-json";

/** Keep payment numbers as decimal tokens throughout editor and transport boundaries. */
export const parsePaymentJson = (text: string): any => parse(text, null, {
  parseNumber: (value) => {
    // Preserve application counters as numbers only when their token is an exact safe integer.
    const number = Number(value);
    return /^-?(0|[1-9]\d*)$/.test(value) && value != "-0" && Number.isSafeInteger(number)
      ? number : new LosslessNumber(value);
  },
});
export const stringifyPaymentJson = (value: unknown, space?: number): string =>
  stringify(value, null, space) ?? "";

/** Use the same number contract for JSON editor formatting and mode conversions. */
export const paymentJsonParser = { parse: parsePaymentJson, stringify };

export const PAYMENT_TYPE = "urn:eudi:sca:payment:1";
/** Reject unsupported entry fields and payload shapes before projecting a request into form controls. */
export function assertPaymentTransactionFields(entry: Record<string, unknown>): void {
  if (entry.type !== PAYMENT_TYPE) return;
  const supported = ["type", "credential_ids", "require_cryptographic_holder_binding", "transaction_data_hashes_alg", "payload"];
  const unknown = Object.keys(entry).find(name => !supported.includes(name));
  if (unknown !== undefined) throw new Error(`Unsupported TS12 transaction-data field "${unknown}". Correct the JSON before using the form.`);
  const payload = entry.payload;
  if (!payload || typeof payload !== "object" || Array.isArray(payload) || payload instanceof LosslessNumber) {
    throw new Error("TS12 payment payload must be a JSON object. Correct the JSON before using the form.");
  }
}
export type PaymentMode = "unscheduled" | "scheduled" | "recurring";
type FieldKind = "text" | "timestamp" | "money" | "number" | "integer" | "boolean" | "frequency";
export type PaymentField = { path: string; label: string; kind: FieldKind; required?: boolean };
export const PAYMENT_FIELDS: PaymentField[] = [
  { path: "transaction_id", label: "Transaction ID", kind: "text", required: true },
  { path: "payee.name", label: "Payee name", kind: "text", required: true },
  { path: "payee.id", label: "Payee ID", kind: "text", required: true },
  { path: "currency", label: "Currency (ISO 4217)", kind: "text", required: true },
  { path: "amount", label: "Payment amount", kind: "money", required: true },
  { path: "date_time", label: "Request started", kind: "timestamp" },
  { path: "payee.logo", label: "Payee logo URI", kind: "text" },
  { path: "payee.website", label: "Payee website URI", kind: "text" },
  { path: "pisp.legal_name", label: "PISP legal name", kind: "text", required: true },
  { path: "pisp.brand_name", label: "PISP brand", kind: "text", required: true },
  { path: "pisp.domain_name", label: "PISP domain", kind: "text", required: true },
  { path: "amount_estimated", label: "Estimated amount", kind: "boolean" },
  { path: "amount_earmarked", label: "Amount earmarked", kind: "boolean" },
  { path: "sct_inst", label: "Instant credit transfer requested", kind: "boolean" },
  { path: "execution_date", label: "Execution date and time", kind: "timestamp", required: true },
  { path: "recurrence.start_date", label: "First payment", kind: "timestamp" },
  { path: "recurrence.end_date", label: "Last payment", kind: "timestamp" },
  { path: "recurrence.number", label: "Number of payments", kind: "integer" },
  { path: "recurrence.frequency", label: "Payment frequency", kind: "frequency", required: true },
  { path: "recurrence.mit_options.amount_variable", label: "Variable amount", kind: "boolean" },
  { path: "recurrence.mit_options.min_amount", label: "Minimum payment amount", kind: "money" },
  { path: "recurrence.mit_options.max_amount", label: "Maximum payment amount", kind: "money" },
  { path: "recurrence.mit_options.total_amount", label: "Total payment amount", kind: "money" },
  { path: "recurrence.mit_options.initial_amount", label: "Initial payment amount", kind: "money" },
  { path: "recurrence.mit_options.initial_amount_number", label: "Number of initial payments", kind: "integer" },
  { path: "recurrence.mit_options.apr", label: "Annual percentage rate", kind: "number" },
];
export const PAYMENT_FREQUENCIES = {
  INDA: "Several times a day", DAIL: "Daily", WEEK: "Weekly", TOWK: "Every two weeks",
  TWMN: "Twice a month", MNTH: "Monthly", TOMN: "Every two months", QUTR: "Quarterly",
  FOMN: "Every four months", SEMI: "Twice a year", YEAR: "Yearly", TYEA: "Every two years",
};
export type PaymentDraft = {
  mode: PaymentMode; pisp: boolean; mit: boolean;
  enabled: Record<string, boolean>; values: Record<string, string>;
};
export function paymentDraft(text: string): PaymentDraft {
  const payload = parsePaymentJson(text);
  if (!payload || typeof payload !== "object" || Array.isArray(payload)) throw new Error("Payment payload must be an object.");
  const paths = new Map(PAYMENT_FIELDS.map(field => [field.path, field]));
  const result: PaymentDraft = { mode: payload.recurrence ? "recurring" : payload.execution_date !== undefined ? "scheduled" : "unscheduled",
    pisp: payload.pisp !== undefined, mit: payload.recurrence?.mit_options !== undefined, enabled: {}, values: {} };
  function visit(value: any, path: string) {
    const field = paths.get(path);
    if (field) {
      const numeric = ["money", "number", "integer"].includes(field.kind);
      if ((numeric && !(typeof value === "number" || value instanceof LosslessNumber)) ||
          (field.kind === "boolean" && typeof value !== "boolean") ||
          (!numeric && field.kind !== "boolean" && typeof value !== "string")) {
        throw new Error(path + " has the wrong JSON type. Correct it in JSON before using the form.");
      }
      result.enabled[path] = true; result.values[path] = String(value); return;
    }
    if (!value || typeof value !== "object" || Array.isArray(value) || value instanceof LosslessNumber ||
        !["", "payee", "pisp", "recurrence", "recurrence.mit_options"].includes(path)) {
      throw new Error("Unsupported field " + (path || "payload") + ". Correct it in JSON before using the form.");
    }
    for (const [key, child] of Object.entries(value)) {
      // Supported member names never contain dots; a literal dot must not alias a nested path.
      if (key.includes(".")) throw new Error("Unsupported field " + key + ". Correct it in JSON before using the form.");
      visit(child, path ? path + "." + key : key);
    }
  }
  visit(payload, "");
  if (payload.recurrence !== undefined && payload.execution_date !== undefined) {
    throw new Error("execution_date and recurrence cannot be combined. Correct the JSON before using the form.");
  }
  return result;
}
export function paymentPayload(draft: PaymentDraft, now = Date.now()): string {
  const result: Record<string, any> = {};
  if (draft.pisp) result.pisp = {};
  if (draft.mode === "recurring") result.recurrence = draft.mit ? { mit_options: {} } : {};
  for (const field of PAYMENT_FIELDS) {
    if (field.path.startsWith("pisp.") && !draft.pisp) continue;
    if (field.path === "execution_date" && draft.mode !== "scheduled") continue;
    if (field.path.startsWith("recurrence.") && draft.mode !== "recurring") continue;
    if (field.path.startsWith("recurrence.mit_options.") && !draft.mit) continue;
    if (!field.required && !draft.enabled[field.path]) continue;
    const raw = draft.values[field.path] ?? "";
    let value: unknown = raw;
    if (["money", "number", "integer"].includes(field.kind)) {
      if (!/^-?(0|[1-9]\d*)(\.\d+)?([eE][+-]?\d+)?$/.test(raw)) throw new Error(field.label + " must be a JSON number.");
      value = new LosslessNumber(raw);
      if (field.kind === "integer" && !isMathematicalInteger(raw)) throw new Error(field.label + " must be an integer.");
    } else if (field.kind === "boolean") {
      if (raw !== "true" && raw !== "false") throw new Error(field.label + " must be true or false.");
      value = raw === "true";
    } else if (!raw.trim()) throw new Error(field.label + " must not be blank.");
    if (field.kind === "frequency" && !Object.hasOwn(PAYMENT_FREQUENCIES, raw)) throw new Error("Select a supported payment frequency.");
    if (field.path === "transaction_id" && [...raw].length > 36) throw new Error("Transaction ID must have at most 36 characters.");
    if (field.path === "currency" && !/^[A-Z]{3}$/.test(raw)) throw new Error("Currency must be an ISO 4217 alpha-3 code.");
    if (field.kind === "timestamp") {
      const timestamp = paymentTimestamp(raw);
      if (timestamp === undefined) throw new Error(field.label + " must include a valid date, time and UTC offset.");
      if (field.path === "execution_date" && timestamp < now) throw new Error("Execution date must not lie in the past.");
    }
    const parts = field.path.split("."); let object = result;
    for (const part of parts.slice(0, -1)) object = object[part] ??= {};
    object[parts.at(-1)!] = value;
  }
  return stringifyPaymentJson(result, 2);
}
// Check calendar bounds before Date.parse can normalize impossible dates.
function paymentTimestamp(raw: string): number | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})[Tt](\d{2}):(\d{2}):(\d{2})(?:\.(\d+))?([Zz]|[+-](\d{2}):(\d{2}))$/.exec(raw);
  if (!match) return;
  const [, year, month, day, hour, minute, second, fraction = "", zone, offsetHour = "0", offsetMinute = "0"] = match;
  const y = Number(year), m = Number(month), d = Number(day);
  const days = [31, y % 4 === 0 && (y % 100 !== 0 || y % 400 === 0) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31];
  if (m < 1 || m > 12 || d < 1 || d > days[m - 1] || Number(hour) > 23 || Number(minute) > 59 ||
      Number(second) > 60 || Number(offsetHour) > 23 || Number(offsetMinute) > 59) return;
  // The clock has millisecond resolution; retain the full original text in the payload.
  const millis = fraction.slice(0, 3).padEnd(3, "0");
  const instant = Date.parse(`${year}-${month}-${day}T${hour}:${minute}:${second === "60" ? "59" : second}.${millis}${zone.toUpperCase()}`);
  if (!Number.isFinite(instant)) return;
  if (second !== "60") return instant;
  const utc = new Date(instant);
  const next = Math.floor(instant / 1000) * 1000 + 1000;
  if (utc.getUTCHours() !== 23 || utc.getUTCMinutes() !== 59 || new Date(next).getUTCDate() !== 1) return;
  // Match the server: an inserted second cannot extend a deadline beyond the following midnight.
  return next;
}
function isMathematicalInteger(raw: string): boolean {
  const match = /^-?(\d+)(?:\.(\d+))?(?:[eE]([+-]?\d+))?$/.exec(raw)!;
  const digits = match[1] + (match[2] ?? "");
  const scale = (match[2]?.length ?? 0) - Number(match[3] ?? 0);
  return scale <= 0 || /^0*$/.test(digits.slice(-scale));
}
export function paymentExample(mode: PaymentMode, now = new Date()): string {
  const future = new Date(now.getTime() + 86400000).toISOString();
  const base = { transaction_id: "8D8AC610-566D-4EF0-9C22-186B2A5ED793",
    payee: { name: "Super Store", id: "merchant-001" }, currency: "EUR", amount: 11.56 };
  if (mode === "unscheduled") return stringifyPaymentJson(base, 2);
  const common = { ...base, date_time: now.toISOString(), payee: { ...base.payee, logo: "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAIAAACQkWg2AAAAFUlEQVR4nGNgKPhAGhrVMKph+GoAADKWYBBGgv6DAAAAAElFTkSuQmCC", website: "https://shop.example" },
    pisp: { legal_name: "Example Payments Ltd", brand_name: "Example Payments", domain_name: "payments.example" },
    amount_estimated: false, amount_earmarked: false, sct_inst: true };
  if (mode === "scheduled") return stringifyPaymentJson({ ...common, execution_date: future }, 2);
  return stringifyPaymentJson({ ...common, recurrence: { start_date: future,
    end_date: new Date(now.getTime() + 365 * 86400000).toISOString(), number: 12, frequency: "MNTH",
    mit_options: { amount_variable: true, min_amount: 10, max_amount: 20, total_amount: 200,
      initial_amount: 5, initial_amount_number: 2, apr: 0.12345 } } }, 2);
}
