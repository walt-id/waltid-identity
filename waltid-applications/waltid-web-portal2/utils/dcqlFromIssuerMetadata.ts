export const PID_CONFIGURATION_IDS = [
  "eu.europa.ec.eudi.pid.1",
  "urn:eudi:pid:1",
] as const;

interface IssuerCredentialClaim {
  path?: string[];
  display?: Array<{ name?: string; locale?: string }>;
}

export interface IssuerMetadataForDcql {
  format?: string;
  doctype?: string;
  vct?: string;
  credential_definition?: {
    type?: string[];
    [key: string]: unknown;
  };
  credential_metadata?: {
    claims?: IssuerCredentialClaim[];
  };
}

export interface MetadataClaimOption {
  id: string;
  label: string;
  path: string[];
}

export interface DcqlCredentialQuery {
  id: string;
  format: string;
  meta: Record<string, unknown>;
  claims: Array<{ path: string[] }>;
}

/** OpenID4VP §6.1: credential query ids are alphanumeric, `_`, or `-`. */
const DCQL_IDENTIFIER = /^[A-Za-z0-9_-]+$/;

function claimId(path: string[]): string {
  return path.join(".");
}

export function dcqlCredentialQueryId(configurationId: string): string {
  const sanitized = configurationId
    .replace(/[^A-Za-z0-9_-]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return DCQL_IDENTIFIER.test(sanitized) ? sanitized : "credential";
}

function claimLabel(claim: IssuerCredentialClaim, path: string[]): string {
  const named = claim.display?.find((entry) => entry.name?.trim())?.name?.trim();
  if (named) return named;
  const last = path[path.length - 1];
  return last ? last.replaceAll("_", " ") : claimId(path);
}

export function isPidConfiguration(
  configuration?: IssuerMetadataForDcql,
  configurationId?: string,
): boolean {
  if (
    configurationId &&
    (PID_CONFIGURATION_IDS as readonly string[]).includes(configurationId)
  ) {
    return true;
  }
  const doctype = configuration?.doctype;
  const vct = configuration?.vct;
  return (
    doctype === "eu.europa.ec.eudi.pid.1" ||
    configurationId === "eu.europa.ec.eudi.pid.1" ||
    (typeof vct === "string" && vct.includes("urn:eudi:pid:1")) ||
    configurationId === "urn:eudi:pid:1"
  );
}

export function isMdocConfiguration(
  configuration?: IssuerMetadataForDcql,
): boolean {
  return configuration?.format === "mso_mdoc";
}

/** mdoc PID claims. SD-JWT PID uses the rulebook names below. */
export const DEFAULT_PID_CLAIM_NAMES = new Set([
  "family_name",
  "given_name",
  "birth_date",
]);

/** PID rulebook section 4.1 claim names for `urn:eudi:pid:1`. */
export const SD_JWT_PID_DEFAULT_CLAIM_NAMES = new Set([
  "family_name",
  "given_name",
  "birthdate",
]);

const SD_JWT_PID_CLAIM_PATHS: Record<string, string[]> = {
  birth_date: ["birthdate"],
  birthdate: ["birthdate"],
  nationality: ["nationalities"],
  nationalities: ["nationalities"],
  resident_address: ["address", "formatted"],
  resident_country: ["address", "country"],
  resident_state: ["address", "region"],
  resident_city: ["address", "locality"],
  resident_postal_code: ["address", "postal_code"],
  resident_street: ["address", "street_address"],
  resident_house_number: ["address", "house_number"],
  family_name_birth: ["birth_family_name"],
  given_name_birth: ["birth_given_name"],
  birth_family_name: ["birth_family_name"],
  birth_given_name: ["birth_given_name"],
  email_address: ["email"],
  email: ["email"],
  mobile_phone_number: ["phone_number"],
  phone_number: ["phone_number"],
  portrait: ["picture"],
  picture: ["picture"],
  expiry_date: ["date_of_expiry"],
  date_of_expiry: ["date_of_expiry"],
  issuance_date: ["date_of_issuance"],
  date_of_issuance: ["date_of_issuance"],
};

/** Map a PID claim path onto the SD-JWT rulebook name. mdoc paths stay unchanged. */
export function sdJwtPidClaimPath(path: string[]): string[] {
  if (path[0] === "birth_place") return ["place_of_birth", ...path.slice(1)];
  if (path.length === 1) {
    const mapped = SD_JWT_PID_CLAIM_PATHS[path[0] ?? ""];
    if (mapped) return mapped;
  }
  return path;
}

export function defaultSelectedClaimIds(
  configuration?: IssuerMetadataForDcql,
  configurationId?: string,
): string[] {
  const claims = claimsFromIssuerMetadata(configuration);
  if (!isPidConfiguration(configuration, configurationId)) {
    return claims.map((claim) => claim.id);
  }

  const sdJwtPid = configuration?.format === "dc+sd-jwt";
  const defaults = claims.filter((claim) => {
    const path = sdJwtPid ? sdJwtPidClaimPath(claim.path) : claim.path;
    const name = path[path.length - 1];
    const names = sdJwtPid ? SD_JWT_PID_DEFAULT_CLAIM_NAMES : DEFAULT_PID_CLAIM_NAMES;
    return name !== undefined && names.has(name);
  });
  return defaults.length > 0
    ? defaults.map((claim) => claim.id)
    : claims.map((claim) => claim.id);
}

export function claimsFromIssuerMetadata(
  configuration?: IssuerMetadataForDcql,
): MetadataClaimOption[] {
  const claims = configuration?.credential_metadata?.claims ?? [];
  const options: MetadataClaimOption[] = [];
  for (const claim of claims) {
    const path = (claim.path ?? []).filter(
      (segment): segment is string => typeof segment === "string" && segment.length > 0,
    );
    if (path.length === 0) continue;
    options.push({
      id: claimId(path),
      label: claimLabel(claim, path),
      path,
    });
  }
  return options;
}

export function dcqlMetaFromIssuerMetadata(
  configuration: IssuerMetadataForDcql,
): Record<string, unknown> {
  const format = configuration.format;
  if (format === "mso_mdoc") {
    if (!configuration.doctype) {
      throw new Error("mdoc credential metadata is missing doctype");
    }
    return { doctype_value: configuration.doctype };
  }
  if (format === "dc+sd-jwt") {
    if (!configuration.vct) {
      throw new Error("SD-JWT credential metadata is missing vct");
    }
    return { vct_values: [configuration.vct] };
  }
  if (format === "jwt_vc_json") {
    const type = configuration.credential_definition?.type;
    if (!Array.isArray(type) || type.length === 0) {
      throw new Error("JWT credential metadata is missing credential_definition.type");
    }
    return { type_values: [type] };
  }
  throw new Error(`Unsupported credential format: ${format ?? "unknown"}`);
}

export function dcqlFromIssuerMetadata(
  configurationId: string,
  configuration: IssuerMetadataForDcql,
  selectedClaims: MetadataClaimOption[],
): DcqlCredentialQuery {
  if (!configuration.format) {
    throw new Error("credential metadata is missing format");
  }
  if (selectedClaims.length === 0) {
    throw new Error("at least one claim must be selected");
  }
  const sdJwtPid =
    configuration.format === "dc+sd-jwt" &&
    isPidConfiguration(configuration, configurationId);
  return {
    id: dcqlCredentialQueryId(configurationId),
    format: configuration.format,
    meta: dcqlMetaFromIssuerMetadata(configuration),
    claims: selectedClaims.map((claim) => ({
      path: sdJwtPid ? sdJwtPidClaimPath(claim.path) : claim.path,
    })),
  };
}

export function annexCRequestedElements(
  configuration: IssuerMetadataForDcql,
  selectedClaims: MetadataClaimOption[],
): Record<string, Record<string, string[]>> {
  const doctype =
    typeof configuration.doctype === "string" && configuration.doctype.length > 0
      ? configuration.doctype
      : "org.iso.18013.5.1.mDL";

  const namespaceToElements: Record<string, string[]> = {};
  for (const claim of selectedClaims) {
    if (claim.path.length < 2) continue;
    const namespace = claim.path[0]!;
    const elementId = claim.path[1]!;
    const elements = namespaceToElements[namespace] ?? [];
    if (!elements.includes(elementId)) elements.push(elementId);
    namespaceToElements[namespace] = elements;
  }

  return { [doctype]: namespaceToElements };
}
