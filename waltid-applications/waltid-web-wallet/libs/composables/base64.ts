function base64ToBytes(base64: string) {
    const binString = atob(base64);
    return Uint8Array.from(binString, (m) => m.codePointAt(0));
}

function bytesToBase64(bytes: Uint8Array) {
    const binString = String.fromCodePoint(...bytes);
    return btoa(binString);
}

export function encodeUtf8ToBase64(utf8source: string): string {
    return bytesToBase64(new TextEncoder().encode(utf8source));
}

export function decodeBase64ToUtf8(base64: string): string {
    return new TextDecoder().decode(base64ToBytes(base64));
}
