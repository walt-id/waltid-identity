export type SimpleCredentialFormat = "jwt_vc_json" | "dc+sd-jwt" | "mso_mdoc";

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
  verifier: {
    credentialId: string;
    meta: Record<string, unknown>;
    claims: SimpleCredentialClaim[];
  };
}

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
        vct_values: ["https://credentials.example.com/identity_credential"],
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
    defaultCredentialData: {
      "org.iso.18013.5.1": {
        family_name: "Musterfrau",
        given_name: "Anna Maria",
        birth_date: "1988-08-25",
        issue_date: "2025-01-15",
        expiry_date: "2035-01-15",
        issuing_country: "AT",
        issuing_authority: "Bundesministerium für Inneres",
        document_number: "DL-AT-2025-00018427",
        portrait:
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0i8AAAAASUVORK5CYII=",
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
        portrait_capture_date: "2024-12-20",
        age_in_years: 36,
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
        nationality: "AUT",
        resident_city: "Wien",
        resident_state: "Wien",
        resident_postal_code: "1070",
        resident_country: "AT",
        family_name_national_character: "Musterfrau",
        given_name_national_character: "Anna Maria",
        signature_usual_mark:
          "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO7Z0i8AAAAASUVORK5CYII=",
        biometric_template_face: null,
        biometric_template_finger: null,
        biometric_template_signature_sign: null,
        biometric_template_iris: null,
      },
    },
    verifier: {
      credentialId: "simple_iso_mdl",
      meta: {
        doctype_value: "org.iso.18013.5.1.mDL",
      },
      claims: [
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
      ],
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
