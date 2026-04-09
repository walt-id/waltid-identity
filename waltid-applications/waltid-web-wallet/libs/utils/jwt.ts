function parseJwt(token: string) {
    const base64Url = token.split(".")[1];
    if (!base64Url) {
        throw new Error("JWT is missing a payload segment.");
    }

    const base64 = base64UrlToBase64(base64Url);
    const jsonPayload = decodeURIComponent(window.atob(base64).split("").map(function (c) {
        return "%" + ("00" + c.charCodeAt(0).toString(16)).slice(-2);
    }).join(""));

    try {
        return JSON.parse(jsonPayload);
    } catch (error) {
        return jsonPayload;
    }
}

function base64UrlToBase64(value: string) {
    const base64 = value.replaceAll("-", "+").replaceAll("_", "/");
    const padding = (4 - (base64.length % 4)) % 4;
    return base64 + "=".repeat(padding);
}

export { parseJwt };
