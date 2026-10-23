export const WALLET_IDS = [
  "walt-id",
  "eudi-reference-wallet",
  "france-identite",
  "d-you",
] as const;

export type WalletId = (typeof WALLET_IDS)[number];

export type IssuerKind = "community" | "enterprise";

export interface WalletDefinition {
  id: WalletId;
  label: string;
  issuer: IssuerKind;
  /** Server env var holding the Enterprise issuer service target. */
  issuerTargetEnv?: string;
  /** Verifier material injected by portal2. Absent for unsigned wallets. */
  materialId?: "german-eudi-wallet" | "france-identite" | "eudi-reference-wallet";
  dcApi: boolean;
  verification: {
    signed: boolean;
    encrypted: boolean;
  };
}

export const WALLETS: readonly WalletDefinition[] = [
  {
    id: "walt-id",
    label: "walt.id",
    issuer: "community",
    dcApi: true,
    verification: { signed: false, encrypted: false },
  },
  {
    id: "eudi-reference-wallet",
    label: "EUDI Reference Wallet",
    issuer: "enterprise",
    issuerTargetEnv: "NUXT_ES_EUDI_REF_ISSUER_TARGET",
    materialId: "eudi-reference-wallet",
    dcApi: true,
    verification: { signed: true, encrypted: true },
  },
  {
    id: "france-identite",
    label: "France Identité",
    issuer: "enterprise",
    issuerTargetEnv: "NUXT_ES_FRANCE_IDENTITE_ISSUER_TARGET",
    materialId: "france-identite",
    dcApi: false,
    verification: { signed: true, encrypted: true },
  },
  {
    id: "d-you",
    label: "d-you",
    issuer: "enterprise",
    issuerTargetEnv: "NUXT_ES_DYOU_ISSUER_TARGET",
    materialId: "german-eudi-wallet",
    dcApi: false,
    verification: { signed: true, encrypted: true },
  },
];

export function isWalletId(value: unknown): value is WalletId {
  return typeof value === "string" && (WALLET_IDS as readonly string[]).includes(value);
}

export function getWallet(id: string): WalletDefinition {
  const wallet = WALLETS.find((entry) => entry.id === id);
  if (!wallet) {
    throw new Error(
      "walletId must be walt-id, eudi-reference-wallet, france-identite, or d-you",
    );
  }
  return wallet;
}
