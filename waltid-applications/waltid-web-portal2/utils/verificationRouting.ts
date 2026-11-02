import { getWallet, isWalletId, type WalletDefinition } from "./walletCatalog.ts";
import { isRecord } from "./offerRouting.ts";

export function walletForVerification(
  payload: Record<string, unknown>,
): WalletDefinition {
  if (isWalletId(payload.walletId)) return getWallet(payload.walletId);
  if (payload.materialId === "german-eudi-wallet") return getWallet("d-you");
  if (payload.materialId === "france-identite") return getWallet("france-identite");
  if (payload.materialId === "eudi-reference-wallet") {
    return getWallet("eudi-reference-wallet");
  }
  throw new Error(
    "walletId must be walt-id, eudi-reference-wallet, france-identite, or d-you",
  );
}

export function applyVerificationPolicy(
  payload: Record<string, unknown>,
  wallet: WalletDefinition,
): Record<string, unknown> {
  const next: Record<string, unknown> = { ...payload };
  delete next.walletId;
  delete next.materialId;
  const coreFlow = isRecord(next.core_flow) ? { ...next.core_flow } : {};
  delete coreFlow.key;
  delete coreFlow.x5c;
  delete coreFlow.clientId;
  delete coreFlow.verifier_info;
  delete coreFlow.verifierInfo;
  if (wallet.verification.signed) {
    coreFlow.signed_request = true;
    coreFlow.encrypted_response = wallet.verification.encrypted;
  } else {
    delete coreFlow.signed_request;
    delete coreFlow.encrypted_response;
  }
  next.core_flow = coreFlow;
  return next;
}
