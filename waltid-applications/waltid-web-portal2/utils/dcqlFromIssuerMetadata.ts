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
  return {
    id: dcqlCredentialQueryId(configurationId),
    format: configuration.format,
    meta: dcqlMetaFromIssuerMetadata(configuration),
    claims: selectedClaims.map((claim) => ({
      path: claim.path,
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
