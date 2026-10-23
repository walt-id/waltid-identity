import { createError, defineEventHandler, getQuery } from "h3";
import { getWallet, isWalletId } from "../../utils/walletCatalog";
import {
  communityMetadataUrl,
  communityProfilesUrl,
  enterpriseApiKey,
  enterpriseIssuerTarget,
  enterpriseMetadataUrl,
} from "../../utils/offerRouting";

async function fetchJson(url: string, authorization?: string): Promise<unknown> {
  const headers: Record<string, string> = { accept: "application/json" };
  if (authorization) headers.authorization = authorization;
  const response = await fetch(url, { headers });
  if (!response.ok) {
    throw createError({
      statusCode: response.status,
      statusMessage: `Issuer metadata request failed (${response.status})`,
    });
  }
  return response.json();
}

export default defineEventHandler(async (event) => {
  const walletId = getQuery(event).walletId;
  if (!isWalletId(walletId)) {
    throw createError({
      statusCode: 400,
      statusMessage:
        "walletId must be walt-id, eudi-reference-wallet, france-identite, or d-you",
    });
  }

  const wallet = getWallet(walletId);
  if (wallet.issuer === "community") {
    const [credentialIssuerMetadata, profiles] = await Promise.all([
      fetchJson(communityMetadataUrl()),
      fetchJson(communityProfilesUrl()).catch(() => []),
    ]);
    const publicProfiles = (Array.isArray(profiles) ? profiles : []).flatMap((profile) => {
      if (!profile || typeof profile !== "object") return [];
      const record = profile as Record<string, unknown>;
      const profileId = record.profileId;
      const credentialConfigurationId = record.credentialConfigurationId;
      if (typeof profileId !== "string") return [];
      return [{
        profileId,
        name: typeof record.name === "string" ? record.name : profileId,
        credentialConfigurationId:
          typeof credentialConfigurationId === "string" ? credentialConfigurationId : undefined,
      }];
    });
    return {
      walletId,
      issuerKind: "community",
      credentialIssuerMetadata,
      profiles: publicProfiles,
    };
  }

  let issuerTarget: string;
  try {
    issuerTarget = enterpriseIssuerTarget(wallet);
  } catch (error) {
    throw createError({
      statusCode: 502,
      statusMessage: error instanceof Error ? error.message : "Enterprise issuer is not configured",
    });
  }

  const url = enterpriseMetadataUrl(issuerTarget);
  try {
    const credentialIssuerMetadata = await fetchJson(url);
    return { walletId, issuerKind: "enterprise", credentialIssuerMetadata, profiles: [] };
  } catch {
    const credentialIssuerMetadata = await fetchJson(url, `Bearer ${enterpriseApiKey()}`);
    return { walletId, issuerKind: "enterprise", credentialIssuerMetadata, profiles: [] };
  }
});
