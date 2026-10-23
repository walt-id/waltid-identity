import { createError, defineEventHandler, readBody } from "h3";
import { applyVerificationPolicy, walletForVerification } from "../../../utils/verificationRouting";
import { forwardJson, httpError, readJsonObject } from "../../utils/forwardJson";
import {
  injectPidMaterial,
  loadPidMaterial,
  redactVerifierSecrets,
  resolveVerifierForwardTarget,
} from "../../utils/pidMaterials";

export default defineEventHandler(async (event) => {
  const payload = await readJsonObject(await readBody(event));
  let wallet;
  let prepared;
  try {
    wallet = walletForVerification(payload);
    prepared = applyVerificationPolicy(payload, wallet);
  } catch (error) {
    httpError(error);
  }

  const forwarded = wallet.materialId
    ? injectPidMaterial(prepared, loadPidMaterial(wallet.materialId))
    : prepared;

  const target = `${resolveVerifierForwardTarget()}/verification-session/create`;
  const result = await forwardJson(target, forwarded);
  if (result.status < 200 || result.status >= 300) {
    throw createError({
      statusCode: result.status,
      statusMessage: "Verifier create failed",
      data: redactVerifierSecrets(result.body),
    });
  }
  return redactVerifierSecrets(result.body);
});
