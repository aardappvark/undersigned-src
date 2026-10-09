import { test } from "node:test";
import assert from "node:assert/strict";
import crypto from "node:crypto";
import { verifyWebhook } from "../api.js";

const secret = "whsec_test", body = '{"hook":"h1","events":[]}', now = 1_790_000_000;
const v2 = (t, b = body) => `t=${t},v1=${crypto.createHmac("sha256", secret).update(`${t}.${b}`).digest("hex")}`;

test("a fresh V2 delivery is valid", () => assert.equal(verifyWebhook({ secret, body, signatureV2: v2(now), now }).valid, true));
test("a V2 delivery older than five minutes is refused (replay)", () => assert.equal(verifyWebhook({ secret, body, signatureV2: v2(now - 301), now }).valid, false));
test("a changed body is refused", () => assert.equal(verifyWebhook({ secret, body: body + " ", signatureV2: v2(now), now }).valid, false));
test("the wrong secret is refused", () => assert.equal(verifyWebhook({ secret: "other", body, signatureV2: v2(now), now }).valid, false));
test("a malformed header is refused, not thrown", () => assert.equal(verifyWebhook({ secret, body, signatureV2: "garbage", now }).valid, false));
test("the older sha256= header still verifies", () => {
  const sig = "sha256=" + crypto.createHmac("sha256", secret).update(body).digest("hex");
  assert.equal(verifyWebhook({ secret, body, signature: sig }).valid, true);
});
