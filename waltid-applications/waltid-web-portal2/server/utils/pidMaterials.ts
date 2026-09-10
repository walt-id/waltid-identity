import { createError } from "h3";
import {
  isPidMaterialId,
  type PidMaterialId,
} from "../../data/simplePidVerificationRequests";

export type PidMaterial = {
  clientId: string;
  key: Record<string, unknown>;
  x5c: string[];
  verifierInfo?: unknown;
};

const MATERIAL_ENV: Record<
  PidMaterialId,
  {
    key: string;
    x5c: string;
    clientId: string;
    verifierInfo?: string;
  }
> = {
  "german-eudi-wallet": {
    key: "NUXT_PID_GERMAN_KEY_JWK",
    x5c: "NUXT_PID_GERMAN_X5C",
    clientId: "NUXT_PID_GERMAN_CLIENT_ID",
    verifierInfo: "NUXT_PID_GERMAN_VERIFIER_INFO",
  },
  "france-identite": {
    key: "NUXT_PID_FRANCE_KEY_JWK",
    x5c: "NUXT_PID_FRANCE_X5C",
    clientId: "NUXT_PID_FRANCE_CLIENT_ID",
  },
  "eudi-reference-wallet": {
    key: "NUXT_PID_EUDI_REF_KEY_JWK",
    x5c: "NUXT_PID_EUDI_REF_X5C",
    clientId: "NUXT_PID_EUDI_REF_CLIENT_ID",
  },
};

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

export function parsePidMaterialId(value: unknown): PidMaterialId {
  if (!isPidMaterialId(value)) {
    throw createError({
      statusCode: 400,
      statusMessage:
        "materialId must be german-eudi-wallet, france-identite, or eudi-reference-wallet",
    });
  }
  return value;
}

export function loadPidMaterial(id: PidMaterialId): PidMaterial {
  const env = MATERIAL_ENV[id];
  const key = parseKey(requiredEnv(env.key));
  const x5c = parseX5c(requiredEnv(env.x5c));
  const clientId = requiredEnv(env.clientId);
  const verifierInfo = env.verifierInfo
    ? parseJsonEnv(requiredEnv(env.verifierInfo), env.verifierInfo)
    : undefined;

  return { key, x5c, clientId, verifierInfo };
}

export function resolveVerifierForwardTarget(): string {
  const proxyTarget = process.env.NUXT_VERIFIER_PROXY_TARGET?.replace(/\/+$/, "");
  if (proxyTarget) return proxyTarget;

  const publicBase = process.env.NUXT_PUBLIC_VERIFIER_BASE?.replace(/\/+$/, "");
  if (publicBase && /^https?:\/\//.test(publicBase)) return publicBase;

  return "http://localhost:7004";
}

export function stripClientSecrets(
  payload: Record<string, unknown>,
): Record<string, unknown> {
  const coreFlow = isRecord(payload.core_flow) ? { ...payload.core_flow } : {};
  delete coreFlow.key;
  delete coreFlow.x5c;
  delete coreFlow.clientId;
  delete coreFlow.verifier_info;
  delete coreFlow.verifierInfo;
  return { ...payload, core_flow: coreFlow };
}

export function injectPidMaterial(
  payload: Record<string, unknown>,
  material: PidMaterial,
): Record<string, unknown> {
  const next = stripClientSecrets(payload);
  const coreFlow = isRecord(next.core_flow) ? { ...next.core_flow } : {};

  coreFlow.key = material.key;
  coreFlow.x5c = material.x5c;
  coreFlow.clientId = material.clientId;
  if (material.verifierInfo !== undefined) {
    coreFlow.verifier_info = material.verifierInfo;
  }

  return { ...next, core_flow: coreFlow };
}

export function redactVerifierSecrets(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(redactVerifierSecrets);
  if (!isRecord(value)) return value;

  const next: Record<string, unknown> = {};
  for (const [key, nested] of Object.entries(value)) {
    if (
      key === "key" ||
      key === "x5c" ||
      key === "verifier_info" ||
      key === "verifierInfo"
    ) {
      continue;
    }
    if (key === "d" && typeof nested === "string") continue;
    next[key] = redactVerifierSecrets(nested);
  }
  return next;
}

function requiredEnv(name: string): string {
  const value = process.env[name]?.trim();
  if (!value) {
    throw createError({
      statusCode: 502,
      statusMessage: `${name} is not configured`,
    });
  }
  return value;
}

function parseJsonEnv(raw: string, name: string): unknown {
  try {
    return JSON.parse(raw);
  } catch {
    throw createError({
      statusCode: 502,
      statusMessage: `${name} must be valid JSON`,
    });
  }
}

function parseKey(raw: string): Record<string, unknown> {
  const parsed = parseJsonEnv(raw, "PID key JWK");
  if (!isRecord(parsed)) {
    throw createError({
      statusCode: 502,
      statusMessage: "PID key JWK must be a JSON object",
    });
  }
  if (parsed.type === "jwk" && isRecord(parsed.jwk)) return parsed;
  if (typeof parsed.kty === "string") return { type: "jwk", jwk: parsed };
  throw createError({
    statusCode: 502,
    statusMessage: "PID key JWK must be a JWK or { type: \"jwk\", jwk: ... }",
  });
}

function parseX5c(raw: string): string[] {
  try {
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      const certificates = parsed.filter(
        (entry): entry is string =>
          typeof entry === "string" && entry.trim().length > 0,
      );
      if (certificates.length > 0) return certificates;
    }
  } catch {
    // Single certificate string.
  }

  if (raw.trim()) return [raw.trim()];
  throw createError({
    statusCode: 502,
    statusMessage: "PID x5c must be a certificate or JSON array of certificates",
  });
}
