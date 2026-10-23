import { createError } from "h3";

export async function readJsonObject(value: unknown): Promise<Record<string, unknown>> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw createError({
      statusCode: 400,
      statusMessage: "Request body must be a JSON object",
    });
  }
  return value as Record<string, unknown>;
}

export function httpError(error: unknown): never {
  const message = error instanceof Error ? error.message : "Request failed";
  const statusCode =
    message.includes("is required") || message.includes("must be")
      ? 400
      : 502;
  throw createError({ statusCode, statusMessage: message });
}

export async function forwardJson(
  url: string,
  body: unknown,
  authorization?: string,
): Promise<{ status: number; body: unknown }> {
  const headers: Record<string, string> = {
    accept: "application/json",
    "content-type": "application/json",
  };
  if (authorization) headers.authorization = authorization;

  const response = await fetch(url, {
    method: "POST",
    headers,
    body: JSON.stringify(body),
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
  return { status: response.status, body: parsed };
}
