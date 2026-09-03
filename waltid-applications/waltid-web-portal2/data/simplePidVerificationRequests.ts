export const PID_MATERIAL_IDS = [
  "german-eudi-wallet",
  "france-identite",
  "eudi-reference-wallet",
] as const;

export type PidMaterialId = (typeof PID_MATERIAL_IDS)[number];

export interface SimplePidVerificationRequestOption {
  id: string;
  label: string;
  description: string;
  link?: string;
  materialId?: PidMaterialId;
  requestBody: Record<string, unknown>;
}

const PID_CREDENTIAL_QUERY = {
  credentials: [
    {
      id: "pid",
      format: "mso_mdoc",
      meta: {
        doctype_value: "eu.europa.ec.eudi.pid.1",
      },
      claims: [
        {
          path: ["eu.europa.ec.eudi.pid.1", "family_name"],
        },
        {
          path: ["eu.europa.ec.eudi.pid.1", "given_name"],
        },
        {
          path: ["eu.europa.ec.eudi.pid.1", "birth_date"],
        },
      ],
    },
  ],
};

function createPidRequestBody(signed = false): Record<string, unknown> {
  const coreFlow: Record<string, unknown> = {
    dcql_query: PID_CREDENTIAL_QUERY,
  };

  if (signed) {
    coreFlow.signed_request = true;
    coreFlow.encrypted_response = true;
  }

  return {
    flow_type: "cross_device",
    core_flow: coreFlow,
  };
}

export const SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS: SimplePidVerificationRequestOption[] =
  [
    {
      id: "basic-pid",
      label: "Basic PID",
      description:
        "Unsigned PID request without wallet-specific client identity, keys, certificates, or verifier metadata.",
      requestBody: createPidRequestBody(),
    },
    {
      id: "german-eudi-wallet",
      label: "German EUDI Wallet 🇩🇪",
      description: "Verify a PID from the German EUDI wallet.",
      link: "https://eudi-wallet.gov.de",
      materialId: "german-eudi-wallet",
      requestBody: createPidRequestBody(true),
    },
    {
      id: "france-identite",
      label: "France Identité 🇫🇷",
      description: "Verify a PID from the France Identité app.",
      link: "https://playground.france-identite.gouv.fr/marketplace/wallets/fin/",
      materialId: "france-identite",
      requestBody: createPidRequestBody(true),
    },
    {
      id: "eudi-reference-wallet",
      label: "EUDI Reference Wallet 🇪🇺",
      description: "Verify a PID from the EUDI Reference Wallet",
      link: "https://github.com/eu-digital-identity-wallet",
      materialId: "eudi-reference-wallet",
      requestBody: createPidRequestBody(true),
    },
  ];

export function getSimplePidVerificationRequestOption(
  id: string,
): SimplePidVerificationRequestOption {
  return (
    SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS.find(
      (option) => option.id === id,
    ) ?? SIMPLE_PID_VERIFICATION_REQUEST_OPTIONS[0]!
  );
}

export function isPidMaterialId(value: unknown): value is PidMaterialId {
  return (
    typeof value === "string" &&
    (PID_MATERIAL_IDS as readonly string[]).includes(value)
  );
}
