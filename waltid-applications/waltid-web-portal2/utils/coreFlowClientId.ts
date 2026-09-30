export type CoreFlowClientIdType =
  | "x509_hash"
  | "x509_san_dns"
  | "redirect_uri"
  | "decentralized_identifier"
  | "verifier_attestation"
  | "pre_registered";

/**
 * Verifier2 treats the bare prefix as omitted and completes it to
 * `redirect_uri:<session response_uri>` for unsigned cross-device sessions.
 */
export const REDIRECT_URI_CLIENT_ID_PREFIX = "redirect_uri";

export function requiresClientIdValue(type: CoreFlowClientIdType): boolean {
  return type !== "x509_hash" && type !== "redirect_uri";
}

export function prefixedCoreFlowClientId(
  type: Exclude<CoreFlowClientIdType, "x509_hash">,
  value: string,
  options?: { signedRequest?: boolean },
): string {
  const trimmed = value.trim();
  if (type === "redirect_uri" && !trimmed) {
    if (options?.signedRequest) {
      throw new Error(
        "Signed requests cannot auto-generate a redirect_uri client ID.",
      );
    }
    return REDIRECT_URI_CLIENT_ID_PREFIX;
  }

  if (!trimmed) {
    throw new Error(`${type} requires a value.`);
  }

  return type === "pre_registered" ? trimmed : `${type}:${trimmed}`;
}
