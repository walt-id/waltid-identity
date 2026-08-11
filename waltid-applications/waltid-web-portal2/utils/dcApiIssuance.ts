export const OPENID4VCI_DC_API_PROTOCOL = "openid4vci-v1";

/** Chrome origin-trial docs for Digital Credentials API issuance. */
export const DC_API_ISSUANCE_DOCS_URL =
  "https://developer.chrome.com/blog/digital-credentials-api-143-issuance-ot";

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
  docsUrl: string;
};

function unsupported(reason: string): DcApiIssuanceSupport {
  return {
    supported: false,
    reason,
    docsUrl: DC_API_ISSUANCE_DOCS_URL,
  };
}

export function getDcApiIssuanceSupport(): DcApiIssuanceSupport {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return unsupported(
      "Digital Credentials API issuance requires a browser context.",
    );
  }

  if (!window.isSecureContext) {
    return unsupported(
      "Digital Credentials API issuance requires a secure context (HTTPS or localhost).",
    );
  }

  const digitalCredential = (window as Window & {
    DigitalCredential?: DigitalCredentialGlobal;
  }).DigitalCredential;

  // Match Chrome's documented check: DigitalCredential + protocol allow-list.
  // navigator.credentials.create alone is not enough (WebAuthn always provides it).
  if (!digitalCredential) {
    return unsupported(
      "This browser does not support Digital Credentials API issuance. Use Chrome 143+ with chrome://flags/#web-identity-digital-credentials-creation enabled, then retry.",
    );
  }

  if (typeof digitalCredential.userAgentAllowsProtocol !== "function") {
    return unsupported(
      "This browser exposes DigitalCredential but cannot verify protocol support for issuance.",
    );
  }

  if (!digitalCredential.userAgentAllowsProtocol(OPENID4VCI_DC_API_PROTOCOL)) {
    return unsupported(
      `This browser does not allow the ${OPENID4VCI_DC_API_PROTOCOL} protocol for Digital Credentials API issuance. Enable the Chrome issuance flag or use QR / deep link delivery instead.`,
    );
  }

  const nav = navigator as DigitalCredentialsNavigator;
  if (typeof nav.credentials?.create !== "function") {
    return unsupported(
      "Digital Credentials API issuance is unavailable (navigator.credentials.create missing).",
    );
  }

  return { supported: true, docsUrl: DC_API_ISSUANCE_DOCS_URL };
}

export function formatDcApiIssuanceUnsupportedMessage(
  support: DcApiIssuanceSupport = getDcApiIssuanceSupport(),
): string {
  const reason =
    support.reason ??
    "Digital Credentials API issuance is not supported in this browser.";
  return `${reason} See ${support.docsUrl} for setup requirements. QR / deep link delivery remains available.`;
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
    throw new Error(formatDcApiIssuanceUnsupportedMessage(support));
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

  try {
    return await nav.credentials!.create!(dcRequestPayload);
  } catch (e) {
    const message = e instanceof Error ? e.message : String(e);
    // Browsers without issuance support often surface cryptic CredentialsContainer errors.
    if (
      /CredentialsContainer|DigitalCredential|Required member is undefined|NotSupportedError|is not a valid/i.test(
        message,
      )
    ) {
      throw new Error(
        `Digital Credentials API issuance failed in this browser. ${formatDcApiIssuanceUnsupportedMessage(support)}`,
      );
    }
    throw e instanceof Error ? e : new Error(message);
  }
}
