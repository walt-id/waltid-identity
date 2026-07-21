import { defineEventHandler } from "h3";
import { proxyToTarget } from "../../utils/proxy";

export default defineEventHandler((event) =>
  proxyToTarget(event, /^\/issuer-api/, "NUXT_ISSUER_PROXY_TARGET"),
);
