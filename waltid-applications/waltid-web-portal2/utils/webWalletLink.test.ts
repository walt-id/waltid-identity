import assert from "node:assert/strict";
import { test } from "node:test";
import { webWalletHref } from "./webWalletLink.ts";

const offer =
  "openid-credential-offer://?credential_offer_uri=https%3A%2F%2Fissuer.example%2Foffer%2Fa%2Bb";
const presentation =
  "openid4vp://?request_uri=https%3A%2F%2Fverifier.example%2Frequest";

test("hands the raw credential offer to the wallet root", () => {
  const href = webWalletHref("http://localhost:7106/", offer);
  const url = new URL(href);
  assert.equal(`${url.origin}${url.pathname}`, "http://localhost:7106/");
  assert.equal(url.searchParams.get("link"), offer);
  assert.equal(url.pathname.includes("api/siop"), false);
});

test("hands a presentation request through the same wallet entry", () => {
  const url = new URL(webWalletHref("http://localhost:7106", presentation));
  assert.equal(url.searchParams.get("link"), presentation);
  assert.equal(url.pathname, "/");
});

test("drops a query or hash already present on the wallet URL", () => {
  const url = new URL(
    webWalletHref("http://localhost:7106/wallet/?code=old#done", offer),
  );
  assert.equal(url.pathname, "/wallet/");
  assert.equal(url.hash, "");
  assert.equal(url.searchParams.get("code"), null);
  assert.equal(url.searchParams.get("link"), offer);
});
