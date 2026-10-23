import { createError, defineEventHandler, readBody } from "h3";
import {
  communityIssuerBase,
  enterpriseAuthorizationServerMetadataUrl,
  enterpriseMetadataUrl,
  joinUrl,
  portalEventsUrl,
  resolveOfferForward,
} from "../../utils/offerRouting";
import { enterpriseAuthorization } from "../utils/enterpriseSession";
import { forwardJson, httpError, readJsonObject } from "../utils/forwardJson";
import { redactVerifierSecrets } from "../utils/pidMaterials";

function metadataUrls(forward: ReturnType<typeof resolveOfferForward>) {
  if (forward.wallet.issuer === "community") {
    const base = communityIssuerBase();
    return {
      credentialIssuerMetadataUrl: joinUrl(
        base,
        "/.well-known/openid-credential-issuer/openid4vci",
      ),
      authorizationServerMetadataUrl: joinUrl(
        base,
        "/.well-known/oauth-authorization-server/openid4vci",
      ),
    };
  }
  const issuerTarget = forward.issuerTarget!;
  return {
    credentialIssuerMetadataUrl: enterpriseMetadataUrl(issuerTarget),
    authorizationServerMetadataUrl:
      enterpriseAuthorizationServerMetadataUrl(issuerTarget),
  };
}

export default defineEventHandler(async (event) => {
  const payload = await readJsonObject(await readBody(event));
  let forward: ReturnType<typeof resolveOfferForward>;
  try {
    forward = resolveOfferForward(payload);
  } catch (error) {
    httpError(error);
  }

  let authorization: string | undefined;
  if (forward.wallet.issuer === "enterprise") {
    try {
      authorization = await enterpriseAuthorization();
    } catch (error) {
      httpError(error);
    }
  }
  const result = await forwardJson(forward.url, forward.body, authorization);
  if (result.status < 200 || result.status >= 300) {
    throw createError({
      statusCode: result.status,
      statusMessage: "Credential offer creation failed",
      data: redactVerifierSecrets(result.body),
    });
  }

  const response = redactVerifierSecrets(result.body);
  if (!response || typeof response !== "object" || Array.isArray(response)) {
    return response;
  }
  const record = response as Record<string, unknown>;
  const sessionId = record.sessionId ?? record.offerId;
  const eventsUrl =
    typeof sessionId === "string"
      ? forward.wallet.issuer === "enterprise"
        ? portalEventsUrl(forward.wallet.id, sessionId)
        : joinUrl(
            communityIssuerBase(),
            `/issuer2/sessions/${encodeURIComponent(sessionId)}/events`,
          )
      : undefined;

  return {
    ...record,
    eventsUrl,
    ...metadataUrls(forward),
  };
});
