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

export const DEFAULT_CLIENT_ID_TYPE: CoreFlowClientIdType = "redirect_uri";

/** Signable fallback when signed_request is enabled on the unsigned-only redirect_uri type. */
export const SIGNED_REQUEST_DEFAULT_CLIENT_ID_TYPE: CoreFlowClientIdType =
  "x509_hash";

export function clientIdTypeForSignedRequest(
  signedRequest: boolean,
  current: CoreFlowClientIdType,
): CoreFlowClientIdType {
  if (!signedRequest) return DEFAULT_CLIENT_ID_TYPE;
  return isClientIdAllowedForSignedRequest(current)
    ? current
    : SIGNED_REQUEST_DEFAULT_CLIENT_ID_TYPE;
}

export function requiresClientIdValue(type: CoreFlowClientIdType): boolean {
  return type !== "x509_hash" && type !== "redirect_uri";
}

export function isClientIdAllowedForSignedRequest(
  type: CoreFlowClientIdType,
): boolean {
  return type !== "redirect_uri";
}

export function clientIdRequiresX5c(type: CoreFlowClientIdType): boolean {
  return type === "x509_hash" || type === "x509_san_dns";
}

export function signedRequestClientIdError(
  type: CoreFlowClientIdType,
  signedRequest: boolean,
): string | null {
  if (!signedRequest) return null;
  if (type === "redirect_uri") {
    return "Signed requests cannot use the redirect_uri client_id prefix";
  }
  return null;
}

export function prefixedCoreFlowClientId(
  type: Exclude<CoreFlowClientIdType, "x509_hash">,
  value: string,
  options?: { signedRequest?: boolean },
): string {
  const signedError = signedRequestClientIdError(
    type,
    options?.signedRequest === true,
  );
  if (signedError) throw new Error(signedError);

  const trimmed = value.trim();
  if (type === "redirect_uri" && !trimmed) {
    return REDIRECT_URI_CLIENT_ID_PREFIX;
  }

  if (!trimmed) {
    throw new Error(`${type} requires a value.`);
  }

  return type === "pre_registered" ? trimmed : `${type}:${trimmed}`;
}
