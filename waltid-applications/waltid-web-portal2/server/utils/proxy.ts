import {
  createError,
  getRequestURL,
  type H3Event,
  proxyRequest,
} from "h3";

export function proxyToTarget(event: H3Event, prefix: string | RegExp, envName: string) {
  const target = process.env[envName]?.replace(/\/+$/, "");

  if (!target) {
    throw createError({
      statusCode: 502,
      statusMessage: `${envName} is not configured`,
    });
  }

  const requestUrl = getRequestURL(event);
  const proxyPath = requestUrl.pathname.replace(prefix, "");

  return proxyRequest(event, `${target}${proxyPath}${requestUrl.search}`, {
    headers: {
      "ngrok-skip-browser-warning": "true",
    },
  });
}
