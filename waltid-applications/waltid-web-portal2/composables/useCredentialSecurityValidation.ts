function isRecord(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function isNonEmptyString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}

export function parseJwkKey(raw: string): Record<string, unknown> | null {
  if (!raw.trim()) return null

  const parsed = JSON.parse(raw)
  if (!isRecord(parsed)) throw new Error('Key must be a JSON object.')
  if (parsed.type !== 'jwk') throw new Error('Key must have type "jwk".')
  if (!isRecord(parsed.jwk)) throw new Error('Key must contain a jwk object.')

  const jwk = parsed.jwk
  if (!isNonEmptyString(jwk.kty)) throw new Error('JWK must contain a non-empty kty.')

  if (jwk.kty === 'EC' && (!isNonEmptyString(jwk.crv) || !isNonEmptyString(jwk.x) || !isNonEmptyString(jwk.y))) {
    throw new Error('EC JWK must contain crv, x, and y.')
  }

  if (jwk.kty === 'RSA' && (!isNonEmptyString(jwk.n) || !isNonEmptyString(jwk.e))) {
    throw new Error('RSA JWK must contain n and e.')
  }

  return parsed
}

export function parseVerifierX5c(values: string[]): string[] {
  return values.map(value => value.trim()).filter(Boolean).map((value, index) => {
    if (value.includes('BEGIN CERTIFICATE') || value.includes('END CERTIFICATE')) {
      throw new Error(`Verifier x5c certificate ${index + 1} must be base64 DER only, without PEM markers.`)
    }
    if (!/^[A-Za-z0-9+/=]+$/.test(value)) {
      throw new Error(`Verifier x5c certificate ${index + 1} must be base64 encoded.`)
    }
    return value
  })
}

function decodeBase64Certificate(certificate: string): Uint8Array {
  const base64 = certificate
    .replace(/-----BEGIN CERTIFICATE-----/g, '')
    .replace(/-----END CERTIFICATE-----/g, '')
    .replace(/\s+/g, '')

  const binary = atob(base64)
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

function encodeBase64Url(bytes: Uint8Array): string {
  let binary = ''
  for (const byte of bytes) {
    binary += String.fromCharCode(byte)
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '')
}

export async function computeX509HashClientId(certificate: string): Promise<string> {
  if (!globalThis.crypto?.subtle) {
    throw new Error('Web Crypto API is not available to compute x509_hash.')
  }

  const der = decodeBase64Certificate(certificate)
  const digest = await globalThis.crypto.subtle.digest('SHA-256', der)
  return `x509_hash:${encodeBase64Url(new Uint8Array(digest))}`
}

export function parseIssuerPemCertificates(values: string[]): string[] {
  return values.map(value => value.trim()).filter(Boolean).map((value, index) => {
    if (!value.includes('-----BEGIN CERTIFICATE-----') || !value.includes('-----END CERTIFICATE-----')) {
      throw new Error(`Issuer certificate ${index + 1} must include BEGIN CERTIFICATE and END CERTIFICATE markers.`)
    }

    const body = value
      .replace('-----BEGIN CERTIFICATE-----', '')
      .replace('-----END CERTIFICATE-----', '')
      .replace(/\s/g, '')

    if (!body || !/^[A-Za-z0-9+/=]+$/.test(body)) {
      throw new Error(`Issuer certificate ${index + 1} must contain valid base64 PEM content.`)
    }

    return value
  })
}
