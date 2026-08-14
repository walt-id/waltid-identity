type DigitalCredentialGlobal = {
  userAgentAllowsProtocol?: (protocol: string) => boolean;
};

type DigitalCredentialsNavigator = Navigator & {
  credentials?: {
    get?: (options: unknown) => Promise<unknown>;
  };
};

export type DcApiPresentationSupport = {
  supported: boolean;
  reason?: string;
};

function unsupported(reason: string): DcApiPresentationSupport {
  return { supported: false, reason };
}

/**
 * Feature-detect Digital Credentials API *presentation*.
 *
 * `navigator.credentials.get` is not sufficient: WebAuthn also provides it.
 * Match the issuance path by requiring `window.DigitalCredential` in a secure
 * context, then confirm `credentials.get` exists.
 */
export function getDcApiPresentationSupport(): DcApiPresentationSupport {
  if (typeof window === "undefined" || typeof navigator === "undefined") {
    return unsupported(
      "Digital Credentials API presentation requires a browser context.",
    );
  }

  if (!window.isSecureContext) {
    return unsupported(
      "Digital Credentials API presentation requires a secure context (HTTPS or localhost).",
    );
  }

  const digitalCredential = (
    window as Window & { DigitalCredential?: DigitalCredentialGlobal }
  ).DigitalCredential;

  if (!digitalCredential) {
    return unsupported(
      "This browser does not support the Digital Credentials API (window.DigitalCredential is missing).",
    );
  }

  const nav = navigator as DigitalCredentialsNavigator;
  if (typeof nav.credentials?.get !== "function") {
    return unsupported(
      "Digital Credentials API presentation is unavailable (navigator.credentials.get missing).",
    );
  }

  return { supported: true };
}

export function formatDcApiPresentationUnsupportedMessage(
  support: DcApiPresentationSupport = getDcApiPresentationSupport(),
): string {
  return (
    support.reason ??
    "Digital Credentials API presentation is not supported in this browser."
  );
}
