import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, rm } from "node:fs/promises";
import { createServer, request } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { test } from "node:test";

// Run after `npm run build`: exercise Nitro's actual read/transform/forward boundary.
test("payment proxy preserves outbound decimal tokens and rejects malformed JSON", { timeout: 30_000 }, async (t) => {
  const directory = await mkdtemp(join(tmpdir(), "ts12-proxy-"));
  const captured = [];
  const upstream = createServer(async (req, res) => {
    let body = "";
    for await (const chunk of req) body += chunk;
    captured.push(body);
    res.writeHead(200, { "content-type": "application/json" });
    res.end('{"sessionId":"synthetic-payment"}');
  });
  await new Promise(resolve => upstream.listen(0, "127.0.0.1", resolve));
  const socketPath = join(directory, "portal.sock");
  const portal = spawn(process.execPath, [".output/server/index.mjs"], {
    env: { ...process.env, NITRO_UNIX_SOCKET: socketPath,
      NUXT_VERIFIER_PROXY_TARGET: `http://127.0.0.1:${upstream.address().port}` },
    stdio: ["ignore", "pipe", "pipe"],
  });
  t.after(async () => {
    if (portal.exitCode === null) {
      const exited = once(portal, "exit");
      portal.kill("SIGTERM");
      await exited;
    }
    await new Promise(resolve => upstream.close(resolve));
    await rm(directory, { recursive: true, force: true });
  });
  await new Promise((resolve, reject) => {
    const timeout = setTimeout(() => reject(new Error("Built portal did not start; run npm run build first")), 15_000);
    let output = "";
    portal.once("error", reject);
    portal.once("exit", code => { clearTimeout(timeout); reject(new Error(`Built portal exited: ${code}`)); });
    portal.stdout.on("data", chunk => {
      output += chunk;
      if (output.includes("Listening on unix socket")) { clearTimeout(timeout); resolve(); }
    });
  });
  const post = body => new Promise((resolve, reject) => {
    const req = request({ socketPath, path: "/api/verification-session/create", method: "POST",
      headers: { "content-type": "application/json", "content-length": Buffer.byteLength(body) } }, async res => {
      let response = "";
      for await (const chunk of res) response += chunk;
      resolve({ status: res.statusCode, body: response });
    });
    req.on("error", reject);
    req.end(body);
  });
  const body = '{"walletId":"walt-id","flow_type":"cross_device","core_flow":{"dcql_query":{"credentials":[]}},"openid":{"transactionData":[{"type":"urn:eudi:sca:payment:1","credential_ids":["payment"],"payload":{"transaction_id":"test","payee":{"name":"Shop","id":"merchant"},"currency":"EUR","amount":9007199254740993.01,"recurrence":{"frequency":"MNTH","number":2e0,"mit_options":{"max_amount":9007199254740993.01,"apr":0.1234567890123456789}}}}]}}';
  const response = await post(body);
  assert.equal(response.status, 200);
  assert.equal(JSON.parse(response.body).sessionId, "synthetic-payment");
  assert.equal(captured.length, 1);
  assert.match(captured[0], /"amount":9007199254740993\.01/);
  assert.match(captured[0], /"max_amount":9007199254740993\.01/);
  assert.match(captured[0], /"apr":0\.1234567890123456789/);
  assert.match(captured[0], /"number":2e0/);
  assert.equal((await post('{"walletId":')).status, 400);
  assert.equal(captured.length, 1, "Malformed drafts must not reach the verifier");
});
