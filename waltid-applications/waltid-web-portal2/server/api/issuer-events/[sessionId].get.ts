import { createError, defineEventHandler, getQuery, getRouterParam, proxyRequest } from "h3";
import { getWallet, isWalletId } from "../../../utils/walletCatalog";
import { enterpriseEventsUrl, enterpriseIssuerTarget } from "../../../utils/offerRouting";
import { enterpriseAuthorization } from "../../utils/enterpriseSession";

export default defineEventHandler(async (event) => {
  const walletId = getQuery(event).walletId;
  const sessionId = getRouterParam(event, "sessionId");
  if (!isWalletId(walletId) || !sessionId) {
    throw createError({
      statusCode: 400,
      statusMessage: "walletId and sessionId are required",
    });
  }
  const wallet = getWallet(walletId);
  if (wallet.issuer !== "enterprise") {
    throw createError({
      statusCode: 400,
      statusMessage: "Issuer events are proxied only for Enterprise wallets",
    });
  }

  let target: string;
  try {
    target = enterpriseEventsUrl(enterpriseIssuerTarget(wallet), sessionId);
  } catch (error) {
    throw createError({
      statusCode: 502,
      statusMessage: error instanceof Error ? error.message : "Enterprise issuer is not configured",
    });
  }

  return proxyRequest(event, target, {
    headers: {
      authorization: await enterpriseAuthorization(),
      accept: "text/event-stream",
    },
  });
});
