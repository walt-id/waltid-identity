import { getWallet, type WalletDefinition } from "./walletCatalog.ts";

const ISSUER_SECRET_KEYS = [
  "signedMetadata",
  "clientAuthenticationConfig",
  "keyAttestationConfig",
  "clientAuthentication",
  "keyAttestation",
] as const;

const ENTERPRISE_OFFER_KEYS = [
  "authMethod",
  "issuerStateMode",
  "valueMode",
  "expiresInSeconds",
  "txCode",
  "txCodeValue",
  "sessionId",
  "runtimeOverrides",
] as const;

export function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

export function joinUrl(base: string, path: string): string {
  return `${base.replace(/\/+$/, "")}/${path.replace(/^\/+/, "")}`;
}

export function wellKnownUrl(resourceUrl: string, name: string): string {
  const url = new URL(resourceUrl);
  const path = url.pathname.replace(/\/+$/, "");
  return `${url.origin}/.well-known/${name}${path}`;
}

export function communityIssuerBase(): string {
  const proxyTarget = process.env.NUXT_ISSUER_PROXY_TARGET?.replace(/\/+$/, "");
  if (proxyTarget) return proxyTarget;
  const publicBase = process.env.NUXT_PUBLIC_ISSUER_BASE?.replace(/\/+$/, "");
  if (publicBase && /^https?:\/\//.test(publicBase)) return publicBase;
  return "http://localhost:7005";
}

export function enterpriseBaseUrl(): string {
  const base = process.env.NUXT_ES_BASE_URL?.trim().replace(/\/+$/, "");
  if (!base) {
    throw new Error("NUXT_ES_BASE_URL is not configured");
  }
  return base;
}

export function enterpriseApiKey(): string {
  const key = process.env.NUXT_ES_API_KEY?.trim();
  if (!key) {
    throw new Error("NUXT_ES_API_KEY is not configured");
  }
  return key;
}

export function enterpriseIssuerTarget(wallet: WalletDefinition): string {
  if (!wallet.issuerTargetEnv) {
    throw new Error(`${wallet.id} does not use an Enterprise issuer`);
  }
  const target = process.env[wallet.issuerTargetEnv]?.trim();
  if (!target) {
    throw new Error(`${wallet.issuerTargetEnv} is not configured`);
  }
  return target;
}

export function credentialConfigurationId(
  payload: Record<string, unknown>,
): string {
  const value = payload.credentialConfigurationId ?? payload.profileId;
  if (typeof value !== "string" || value.trim().length === 0) {
    throw new Error("profileId or credentialConfigurationId is required");
  }
  return value.trim();
}

export function communityOfferUrl(): string {
  return joinUrl(communityIssuerBase(), "/issuer2/credential-offers");
}

export function communityMetadataUrl(): string {
  return joinUrl(
    communityIssuerBase(),
    "/.well-known/openid-credential-issuer/openid4vci",
  );
}

export function communityProfilesUrl(): string {
  return joinUrl(communityIssuerBase(), "/issuer2/profiles");
}

export function enterpriseCredentialIssuerUrl(issuerTarget: string): string {
  return joinUrl(
    enterpriseBaseUrl(),
    `/v2/${issuerTarget}/issuer-service-api/openid4vci`,
  );
}

export function enterpriseMetadataUrl(issuerTarget: string): string {
  return wellKnownUrl(
    enterpriseCredentialIssuerUrl(issuerTarget),
    "openid-credential-issuer",
  );
}

export function enterpriseAuthorizationServerMetadataUrl(
  issuerTarget: string,
): string {
  return wellKnownUrl(
    enterpriseCredentialIssuerUrl(issuerTarget),
    "oauth-authorization-server",
  );
}

export function enterpriseOfferUrl(
  issuerTarget: string,
  configurationId: string,
): string {
  return joinUrl(
    enterpriseBaseUrl(),
    `/v2/${issuerTarget}.${configurationId}/issuer-service-api/credentials/offers`,
  );
}

export function enterpriseEventsUrl(
  issuerTarget: string,
  sessionId: string,
): string {
  return joinUrl(
    enterpriseBaseUrl(),
    `/v2/${issuerTarget}/issuer-service-api/issuance-session/${encodeURIComponent(sessionId)}/events`,
  );
}

export function stripIssuerSecrets(
  value: Record<string, unknown>,
): Record<string, unknown> {
  const next: Record<string, unknown> = { ...value };
  for (const key of ISSUER_SECRET_KEYS) delete next[key];
  if (isRecord(next.runtimeOverrides)) {
    const overrides = { ...next.runtimeOverrides };
    for (const key of ISSUER_SECRET_KEYS) delete overrides[key];
    next.runtimeOverrides = overrides;
  }
  return next;
}

export function communityOfferBody(
  payload: Record<string, unknown>,
): Record<string, unknown> {
  const body = stripIssuerSecrets(payload);
  delete body.walletId;
  delete body.credentialConfigurationId;
  return body;
}

export function enterpriseOfferBody(
  payload: Record<string, unknown>,
): Record<string, unknown> {
  if (typeof payload.authMethod !== "string" || payload.authMethod.trim().length === 0) {
    throw new Error("authMethod is required");
  }
  const source = stripIssuerSecrets(payload);
  const body: Record<string, unknown> = {};
  for (const key of ENTERPRISE_OFFER_KEYS) {
    if (source[key] !== undefined) body[key] = source[key];
  }
  return body;
}

export function resolveOfferForward(payload: Record<string, unknown>): {
  wallet: WalletDefinition;
  url: string;
  body: Record<string, unknown>;
  authorization?: string;
  issuerTarget?: string;
} {
  const wallet = getWallet(
    typeof payload.walletId === "string" ? payload.walletId : "",
  );
  if (wallet.issuer === "community") {
    return { wallet, url: communityOfferUrl(), body: communityOfferBody(payload) };
  }
  const issuerTarget = enterpriseIssuerTarget(wallet);
  const configurationId = credentialConfigurationId(payload);
  return {
    wallet,
    url: enterpriseOfferUrl(issuerTarget, configurationId),
    body: enterpriseOfferBody(payload),
    authorization: `Bearer ${enterpriseApiKey()}`,
    issuerTarget,
  };
}

export function portalEventsUrl(walletId: string, sessionId: string): string {
  const params = new URLSearchParams({ walletId });
  return `/api/issuer-events/${encodeURIComponent(sessionId)}?${params.toString()}`;
}
