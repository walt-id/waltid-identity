import { createError, defineEventHandler, readBody } from "h3";
import {
  injectPidMaterial,
  loadPidMaterial,
  parsePidMaterialId,
  redactVerifierSecrets,
  resolveVerifierForwardTarget,
  stripClientSecrets,
} from "../../utils/pidMaterials";

function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === "object" && !Array.isArray(value);
}

export default defineEventHandler(async (event) => {
  const body = await readBody<unknown>(event);
  if (!isRecord(body)) {
    throw createError({
      statusCode: 400,
      statusMessage: "Request body must be a JSON object",
    });
  }

  const { materialId: rawMaterialId, ...payload } = body;
  const injected = rawMaterialId
    ? injectPidMaterial(payload, loadPidMaterial(parsePidMaterialId(rawMaterialId)))
    : stripClientSecrets(payload);

  const target = `${resolveVerifierForwardTarget()}/verification-session/create`;
  const response = await fetch(target, {
    method: "POST",
    headers: {
      accept: "application/json",
      "content-type": "application/json",
    },
    body: JSON.stringify(injected),
  });

  const text = await response.text();
  let parsed: unknown = text;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = text;
    }
  }

  if (!response.ok) {
    throw createError({
      statusCode: response.status,
      statusMessage: "Verifier create failed",
      data: redactVerifierSecrets(parsed),
    });
  }

  return redactVerifierSecrets(parsed);
});
