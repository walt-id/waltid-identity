export const OPENID4VCI_DC_API_PROTOCOL = "openid4vci-v1";

type DigitalCredentialGlobal = {
  userAgentAllowsProtocol?: (protocol: string) => boolean;
};

type DigitalCredentialsNavigator = Navigator & {
  credentials?: {
    create?: (options: unknown) => Promise<unknown>;
  };
};

export type DcApiIssuanceSupport = {
  supported: boolean;
  reason?: string;
};

export function getDcApiIssuanceSupport(): DcApiIssuanceSupport {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return {
      supported: false,
      reason: "Digital Credentials API issuance requires a browser context.",
    };
  }

  if (!window.isSecureContext) {
    return {
      supported: false,
      reason:
        "Digital Credentials API issuance requires a secure context (HTTPS or localhost).",
    };
  }

  const nav = navigator as DigitalCredentialsNavigator;
  if (typeof nav.credentials?.create !== "function") {
    return {
      supported: false,
      reason:
        "Digital Credentials API issuance is unavailable (navigator.credentials.create missing). Use Chrome 143+ with chrome://flags/#web-identity-digital-credentials-creation enabled.",
    };
  }

  const digitalCredential = (window as Window & {
    DigitalCredential?: DigitalCredentialGlobal;
  }).DigitalCredential;

  if (
    typeof digitalCredential?.userAgentAllowsProtocol === "function" &&
    !digitalCredential.userAgentAllowsProtocol(OPENID4VCI_DC_API_PROTOCOL)
  ) {
    return {
      supported: false,
      reason: `This browser does not allow the ${OPENID4VCI_DC_API_PROTOCOL} protocol for Digital Credentials API issuance.`,
    };
  }

  return { supported: true };
}

export function parseCredentialOfferFromUrl(
  credentialOfferUrl: string,
): Record<string, unknown> {
  const queryIndex = credentialOfferUrl.indexOf("?");
  if (queryIndex < 0) {
    throw new Error("Credential offer URL is missing query parameters.");
  }

  const params = new URLSearchParams(credentialOfferUrl.slice(queryIndex + 1));
  const byValue = params.get("credential_offer");
  if (byValue) {
    const parsed = JSON.parse(byValue) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error("credential_offer must be a JSON object.");
    }
    return parsed as Record<string, unknown>;
  }

  const byReference = params.get("credential_offer_uri");
  if (byReference) {
    throw new Error(
      "Credential offer is by-reference; DC API issuance expects valueMode BY_VALUE.",
    );
  }

  throw new Error(
    "Credential offer URL contains neither credential_offer nor credential_offer_uri.",
  );
}

export function enrichCredentialOfferForDcApi(
  offer: Record<string, unknown>,
  credentialIssuerMetadata: unknown,
  authorizationServerMetadata: unknown,
): Record<string, unknown> {
  return {
    ...offer,
    credential_issuer_metadata: credentialIssuerMetadata,
    authorization_server_metadata: authorizationServerMetadata,
  };
}

export async function invokeDigitalCredentialsCreate(
  enrichedOffer: Record<string, unknown>,
  mediationRequired = false,
): Promise<unknown> {
  const support = getDcApiIssuanceSupport();
  if (!support.supported) {
    throw new Error(support.reason ?? "Digital Credentials API is unavailable.");
  }

  const nav = navigator as DigitalCredentialsNavigator;
  const dcRequestPayload: { mediation?: string; digital: unknown } = {
    digital: {
      requests: [
        {
          protocol: OPENID4VCI_DC_API_PROTOCOL,
          data: enrichedOffer,
        },
      ],
    },
  };

  if (mediationRequired) dcRequestPayload.mediation = "required";

  return nav.credentials!.create!(dcRequestPayload);
}
