import { defineEventHandler } from "h3";
import { proxyToTarget } from "../../utils/proxy";

export default defineEventHandler((event) =>
  proxyToTarget(event, /^\/verifier-api/, "NUXT_VERIFIER_PROXY_TARGET"),
);
