export type SimpleCredentialFormat = "jwt_vc_json" | "dc+sd-jwt" | "mso_mdoc";

export type SimpleVerifierFlowType = "cross_device" | "dc_api";

export interface SimpleCredentialClaim {
  id: string;
  label: string;
  path: string[];
}

export interface SimpleCredentialOption {
  id: string;
  title: string;
  description: string;
  profileId: string;
  format: SimpleCredentialFormat;
  pills: Array<{ label: string; tone: "blue" | "green" | "purple" | "slate" }>;
  defaultCredentialData: Record<string, unknown>;
  /** When true, the option is only shown in the simple verify editor. */
  verifyOnly?: boolean;
  /** Verification transport. Defaults to cross_device (QR / HTTP). */
  verifierFlowType?: SimpleVerifierFlowType;
  verifier: {
    credentialId: string;
    meta: Record<string, unknown>;
    claims: SimpleCredentialClaim[];
  };
}

const MDL_DEFAULT_CREDENTIAL_DATA: Record<string, unknown> = {
  "org.iso.18013.5.1": {
    family_name: "Musterfrau",
    given_name: "Anna Maria",
    birth_date: "1988-08-25",
    age_in_years: 38, // For UTC validFrom dates 2026-08-25 through 2027-08-24.
    issue_date: "2025-01-15",
    expiry_date: "2035-01-15",
    issuing_country: "AT",
    issuing_authority: "Bundesministerium für Inneres",
    document_number: "DL-AT-2025-00018427",
    portrait:
      "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",
    driving_privileges: [
      {
        vehicle_category_code: "B",
        issue_date: "2006-09-14",
        expiry_date: "2035-01-15",
      },
      {
        vehicle_category_code: "AM",
        issue_date: "2004-08-25",
        expiry_date: "2035-01-15",
      },
    ],
    un_distinguishing_sign: "A",
    administrative_number: "AT-FS-99817234",
    sex: 2,
    height: 170,
    weight: 63,
    eye_colour: "brown",
    hair_colour: "brown",
    birth_place: "Graz",
    resident_address: "Mariahilfer Strasse 120/8",
    portrait_capture_date: "2024-12-20T12:00:00Z",
    age_birth_year: 1988,
    age_over_12: true,
    age_over_13: true,
    age_over_14: true,
    age_over_16: true,
    age_over_18: true,
    age_over_21: true,
    age_over_25: true,
    age_over_60: false,
    age_over_62: false,
    age_over_65: false,
    age_over_68: false,
    issuing_jurisdiction: "AT-9",
    nationality: "AT",
    resident_city: "Wien",
    resident_state: "Wien",
    resident_postal_code: "1070",
    resident_country: "AT",
    family_name_national_character: "Musterfrau",
    given_name_national_character: "Anna Maria",
    signature_usual_mark:
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0i8AAAAASUVORK5CYII=",
  },
};

const MDL_VERIFIER_CLAIMS: SimpleCredentialClaim[] = [
  {
    id: "family_name",
    label: "Family name",
    path: ["org.iso.18013.5.1", "family_name"],
  },
  {
    id: "given_name",
    label: "Given name",
    path: ["org.iso.18013.5.1", "given_name"],
  },
  {
    id: "birth_date",
    label: "Birth date",
    path: ["org.iso.18013.5.1", "birth_date"],
  },
  {
    id: "issue_date",
    label: "Issue date",
    path: ["org.iso.18013.5.1", "issue_date"],
  },
  {
    id: "expiry_date",
    label: "Expiry date",
    path: ["org.iso.18013.5.1", "expiry_date"],
  },
  {
    id: "issuing_country",
    label: "Issuing country",
    path: ["org.iso.18013.5.1", "issuing_country"],
  },
  {
    id: "issuing_authority",
    label: "Issuing authority",
    path: ["org.iso.18013.5.1", "issuing_authority"],
  },
  {
    id: "document_number",
    label: "Document number",
    path: ["org.iso.18013.5.1", "document_number"],
  },
  {
    id: "portrait",
    label: "Portrait",
    path: ["org.iso.18013.5.1", "portrait"],
  },
  {
    id: "driving_privileges",
    label: "Driving privileges",
    path: ["org.iso.18013.5.1", "driving_privileges"],
  },
  {
    id: "un_distinguishing_sign",
    label: "UN distinguishing sign",
    path: ["org.iso.18013.5.1", "un_distinguishing_sign"],
  },
  {
    id: "administrative_number",
    label: "Administrative number",
    path: ["org.iso.18013.5.1", "administrative_number"],
  },
  { id: "sex", label: "Sex", path: ["org.iso.18013.5.1", "sex"] },
  {
    id: "height",
    label: "Height",
    path: ["org.iso.18013.5.1", "height"],
  },
  {
    id: "weight",
    label: "Weight",
    path: ["org.iso.18013.5.1", "weight"],
  },
  {
    id: "eye_colour",
    label: "Eye colour",
    path: ["org.iso.18013.5.1", "eye_colour"],
  },
  {
    id: "hair_colour",
    label: "Hair colour",
    path: ["org.iso.18013.5.1", "hair_colour"],
  },
  {
    id: "birth_place",
    label: "Birth place",
    path: ["org.iso.18013.5.1", "birth_place"],
  },
  {
    id: "resident_address",
    label: "Resident address",
    path: ["org.iso.18013.5.1", "resident_address"],
  },
  {
    id: "portrait_capture_date",
    label: "Portrait capture date",
    path: ["org.iso.18013.5.1", "portrait_capture_date"],
  },
  {
    id: "age_in_years",
    label: "Age in years",
    path: ["org.iso.18013.5.1", "age_in_years"],
  },
  {
    id: "age_birth_year",
    label: "Birth year",
    path: ["org.iso.18013.5.1", "age_birth_year"],
  },
  {
    id: "age_over_12",
    label: "Over 12",
    path: ["org.iso.18013.5.1", "age_over_12"],
  },
  {
    id: "age_over_13",
    label: "Over 13",
    path: ["org.iso.18013.5.1", "age_over_13"],
  },
  {
    id: "age_over_14",
    label: "Over 14",
    path: ["org.iso.18013.5.1", "age_over_14"],
  },
  {
    id: "age_over_16",
    label: "Over 16",
    path: ["org.iso.18013.5.1", "age_over_16"],
  },
  {
    id: "age_over_18",
    label: "Over 18",
    path: ["org.iso.18013.5.1", "age_over_18"],
  },
  {
    id: "age_over_21",
    label: "Over 21",
    path: ["org.iso.18013.5.1", "age_over_21"],
  },
  {
    id: "age_over_25",
    label: "Over 25",
    path: ["org.iso.18013.5.1", "age_over_25"],
  },
  {
    id: "age_over_60",
    label: "Over 60",
    path: ["org.iso.18013.5.1", "age_over_60"],
  },
  {
    id: "age_over_62",
    label: "Over 62",
    path: ["org.iso.18013.5.1", "age_over_62"],
  },
  {
    id: "age_over_65",
    label: "Over 65",
    path: ["org.iso.18013.5.1", "age_over_65"],
  },
  {
    id: "age_over_68",
    label: "Over 68",
    path: ["org.iso.18013.5.1", "age_over_68"],
  },
  {
    id: "issuing_jurisdiction",
    label: "Issuing jurisdiction",
    path: ["org.iso.18013.5.1", "issuing_jurisdiction"],
  },
  {
    id: "nationality",
    label: "Nationality",
    path: ["org.iso.18013.5.1", "nationality"],
  },
  {
    id: "resident_city",
    label: "Resident city",
    path: ["org.iso.18013.5.1", "resident_city"],
  },
  {
    id: "resident_state",
    label: "Resident state",
    path: ["org.iso.18013.5.1", "resident_state"],
  },
  {
    id: "resident_postal_code",
    label: "Resident postal code",
    path: ["org.iso.18013.5.1", "resident_postal_code"],
  },
  {
    id: "resident_country",
    label: "Resident country",
    path: ["org.iso.18013.5.1", "resident_country"],
  },
  {
    id: "family_name_national_character",
    label: "Family name national characters",
    path: ["org.iso.18013.5.1", "family_name_national_character"],
  },
  {
    id: "given_name_national_character",
    label: "Given name national characters",
    path: ["org.iso.18013.5.1", "given_name_national_character"],
  },
  {
    id: "signature_usual_mark",
    label: "Signature usual mark",
    path: ["org.iso.18013.5.1", "signature_usual_mark"],
  },
  {
    id: "biometric_template_face",
    label: "Face biometric template",
    path: ["org.iso.18013.5.1", "biometric_template_face"],
  },
  {
    id: "biometric_template_finger",
    label: "Finger biometric template",
    path: ["org.iso.18013.5.1", "biometric_template_finger"],
  },
  {
    id: "biometric_template_signature_sign",
    label: "Signature biometric template",
    path: ["org.iso.18013.5.1", "biometric_template_signature_sign"],
  },
  {
    id: "biometric_template_iris",
    label: "Iris biometric template",
    path: ["org.iso.18013.5.1", "biometric_template_iris"],
  },
];

export const SIMPLE_CREDENTIAL_OPTIONS: SimpleCredentialOption[] = [
  {
    id: "w3c-open-badge",
    title: "OpenBadge",
    description:
      "An achievement credential for certificates, course completions, awards, and badges.",
    profileId: "openBadgeCredential",
    format: "jwt_vc_json",
    pills: [{ label: "W3C VC JWT", tone: "blue" }],
    defaultCredentialData: {
      "@context": [
        "https://www.w3.org/ns/credentials/v2",
        "https://purl.imsglobal.org/spec/ob/v3p0/context-3.0.3.json",
        "https://purl.imsglobal.org/spec/ob/v3p0/extensions.json",
      ],
      id: "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
      type: ["VerifiableCredential", "OpenBadgeCredential"],
      issuer: {
        type: ["Profile"],
        id: "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        name: "Jobs for the Future (JFF)",
        url: "https://www.jff.org/",
        image:
          "https://w3c-ccg.github.io/vc-ed/plugfest-1-2022/images/JFF_LogoLockup.png",
      },
      validFrom: "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
      name: "Example University Degree",
      credentialSubject: {
        id: "THIS WILL BE REPLACED WITH DYNAMIC DATA FUNCTION",
        type: ["AchievementSubject"],
        achievement: {
          id: "https://example.com/achievements/21st-century-skills/teamwork",
          type: ["Achievement"],
          criteria: {
            narrative:
              "Team members are nominated for this badge by their peers and recognized upon review by Example Corp management.",
          },
          description:
            "This badge recognizes the development of the capacity to collaborate within a group environment.",
          name: "Teamwork",
        },
      },
      credentialSchema: [
        {
          id: "https://purl.imsglobal.org/spec/ob/v3p0/schema/json/ob_v3p0_achievementcredential_schema.json",
          type: "1EdTechJsonSchemaValidator2019",
        },
      ],
    },
    verifier: {
      credentialId: "simple_openbadge_jwt_vc",
      meta: {
        type_values: [["VerifiableCredential", "OpenBadgeCredential"]],
      },
      claims: [
        { id: "context", label: "Credential context", path: ["@context"] },
        { id: "id", label: "Credential ID", path: ["id"] },
        { id: "type", label: "Credential type", path: ["type"] },
        { id: "issuer_type", label: "Issuer type", path: ["issuer", "type"] },
        { id: "issuer_id", label: "Issuer ID", path: ["issuer", "id"] },
        { id: "issuer_name", label: "Issuer name", path: ["issuer", "name"] },
        { id: "issuer_url", label: "Issuer URL", path: ["issuer", "url"] },
        {
          id: "issuer_image",
          label: "Issuer image",
          path: ["issuer", "image"],
        },
        { id: "valid_from", label: "Valid from", path: ["validFrom"] },
        { id: "name", label: "Badge name", path: ["name"] },
        {
          id: "subject_id",
          label: "Subject ID",
          path: ["credentialSubject", "id"],
        },
        {
          id: "subject_type",
          label: "Subject type",
          path: ["credentialSubject", "type"],
        },
        {
          id: "achievement_id",
          label: "Achievement ID",
          path: ["credentialSubject", "achievement", "id"],
        },
        {
          id: "achievement_type",
          label: "Achievement type",
          path: ["credentialSubject", "achievement", "type"],
        },
        {
          id: "achievement_name",
          label: "Achievement name",
          path: ["credentialSubject", "achievement", "name"],
        },
        {
          id: "achievement_description",
          label: "Achievement description",
          path: ["credentialSubject", "achievement", "description"],
        },
        {
          id: "achievement_criteria_narrative",
          label: "Achievement criteria",
          path: ["credentialSubject", "achievement", "criteria", "narrative"],
        },
        {
          id: "credential_schema",
          label: "Credential schema",
          path: ["credentialSchema"],
        },
      ],
    },
  },
  {
    id: "sdjwt-identity",
    title: "Identity Credential",
    description:
      "A privacy-friendly identity credential with personal attributes that can be selectively disclosed.",
    profileId: "identityCredentialSdJwt",
    format: "dc+sd-jwt",
    pills: [{ label: "IETF SD-JWT", tone: "green" }],
    defaultCredentialData: {
      given_name: "John",
      family_name: "Doe",
      email: "johndoe@example.com",
      phone_number: "+1-202-555-0101",
      address: {
        street_address: "123 Main St",
        locality: "Anytown",
        region: "Anystate",
        country: "US",
      },
      birthdate: "1940-01-01",
      is_over_18: true,
      is_over_21: true,
      is_over_65: true,
    },
    verifier: {
      credentialId: "simple_identity_sd_jwt",
      meta: {
        vct_values: ["https://issuer2.demo.walt.id/openid4vci/identity_credential"],
      },
      claims: [
        { id: "given_name", label: "Given name", path: ["given_name"] },
        { id: "family_name", label: "Family name", path: ["family_name"] },
        { id: "email", label: "Email", path: ["email"] },
        { id: "phone_number", label: "Phone number", path: ["phone_number"] },
        {
          id: "street_address",
          label: "Street address",
          path: ["address", "street_address"],
        },
        { id: "locality", label: "Locality", path: ["address", "locality"] },
        { id: "region", label: "Region", path: ["address", "region"] },
        { id: "country", label: "Country", path: ["address", "country"] },
        { id: "birthdate", label: "Birthdate", path: ["birthdate"] },
        { id: "is_over_18", label: "Over 18", path: ["is_over_18"] },
        { id: "is_over_21", label: "Over 21", path: ["is_over_21"] },
        { id: "is_over_65", label: "Over 65", path: ["is_over_65"] },
      ],
    },
  },
  {
    id: "mdoc-mdl",
    title: "Mobile Drivers License",
    description:
      "A mobile driving licence containing identity details, age attestations, and driving privileges.",
    profileId: "isoMdl",
    format: "mso_mdoc",
    pills: [{ label: "ISO 18013-5", tone: "purple" }],
    defaultCredentialData: MDL_DEFAULT_CREDENTIAL_DATA,
    verifier: {
      credentialId: "simple_iso_mdl",
      meta: {
        doctype_value: "org.iso.18013.5.1.mDL",
      },
      claims: MDL_VERIFIER_CLAIMS,
    },
  },
  {
    id: "mdoc-mdl-dc-api",
    title: "mDL over DC API",
    description:
      "Verify a mobile driving licence through the browser Digital Credentials API using OpenID4VP or ISO 18013-7.",
    profileId: "isoMdl",
    format: "mso_mdoc",
    pills: [
      { label: "ISO 18013-5", tone: "purple" },
      { label: "DC API", tone: "blue" },
      { label: "18013-7", tone: "slate" },
    ],
    defaultCredentialData: {},
    verifyOnly: true,
    verifierFlowType: "dc_api",
    verifier: {
      credentialId: "simple_iso_mdl_dc_api",
      meta: {
        doctype_value: "org.iso.18013.5.1.mDL",
      },
      claims: MDL_VERIFIER_CLAIMS,
    },
  },
  {
    id: "pid",
    title: "PID",
    description:
      "A person identification data credential using within the eIDAS 2 framework.",
    profileId: "isoMdl",
    format: "mso_mdoc",
    pills: [{ label: "eIDAS 2", tone: "purple" }],
    defaultCredentialData: {},
    verifyOnly: true,
    verifier: {
      credentialId: "simple_pid",
      meta: {
        doctype_value: "eu.europa.ec.eudi.pid.1",
      },
      claims: [],
    },
  },
];

export function getSimpleCredentialOption(id: string): SimpleCredentialOption {
  return (
    SIMPLE_CREDENTIAL_OPTIONS.find((option) => option.id === id) ??
    SIMPLE_CREDENTIAL_OPTIONS[0]!
  );
}
