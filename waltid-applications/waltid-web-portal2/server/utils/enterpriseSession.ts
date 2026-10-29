import { enterpriseBaseUrl } from "../../utils/offerRouting";

type CachedSession = {
  token: string;
  expiresAtMs: number;
};

let cached: CachedSession | null = null;
let pending: Promise<string> | null = null;

function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw new Error(`${name} is not configured`);
  }
  return value;
}

function sessionExpiry(value: unknown): number {
  const fallback = Date.now() + 10 * 60 * 1000;
  if (typeof value === "number" && Number.isFinite(value)) {
    return value > 10_000_000_000 ? value : value * 1000;
  }
  if (typeof value === "string" && value.trim()) {
    const parsed = Date.parse(value);
    if (Number.isFinite(parsed)) return parsed;
  }
  return fallback;
}

async function login(): Promise<string> {
  const response = await fetch(`${enterpriseBaseUrl()}/auth/account/emailpass`, {
    method: "POST",
    headers: {
      accept: "application/json",
      "content-type": "application/json",
    },
    body: JSON.stringify({
      email: requiredEnv("NUXT_ES_EMAIL"),
      password: requiredEnv("NUXT_ES_PASSWORD"),
    }),
  });
  const text = await response.text();
  if (!response.ok) {
    throw new Error(`Enterprise login failed (${response.status})`);
  }
  let parsed: { token?: unknown; expiration?: unknown };
  try {
    parsed = JSON.parse(text) as { token?: unknown; expiration?: unknown };
  } catch {
    throw new Error("Enterprise login returned an unreadable session");
  }
  if (typeof parsed.token !== "string" || parsed.token.length === 0) {
    throw new Error("Enterprise login did not return a session token");
  }
  cached = { token: parsed.token, expiresAtMs: sessionExpiry(parsed.expiration) };
  return parsed.token;
}

export async function enterpriseAuthorization(): Promise<string> {
  if (cached && cached.expiresAtMs - Date.now() > 30_000) {
    return `Bearer ${cached.token}`;
  }
  if (!pending) {
    pending = login().finally(() => {
      pending = null;
    });
  }
  return `Bearer ${await pending}`;
}
