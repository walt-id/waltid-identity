/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const cose = require('../');
const test = require('ava');
const base64url = require('base64url');
const cbor = require('cbor');
const jsonfile = require('jsonfile');

test('create and verify really huge payload', async (t) => {
  // uses the keys from here but it has nothing to do with the test
  const example = jsonfile.readFileSync('test/Examples/sign-tests/ecdsa-01.json');
  const BIG_LENGHT = 100 * 1000;
  const p = example.input.sign.protected;
  const u = example.input.sign.unprotected;
  const signers = [{
    key: {
      d: base64url.toBuffer(example.input.sign.signers[0].key.d)
    },
    u: example.input.sign.signers[0].unprotected,
    p: example.input.sign.signers[0].protected
  }];
  const plaintext = Buffer.from('a'.repeat(BIG_LENGHT));

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign.signers[0].key.x),
      y: base64url.toBuffer(example.input.sign.signers[0].key.y),
      kid: example.input.sign.signers[0].key.kid
    }
  };

  const header = { p: p, u: u };
  const buf = await cose.sign.create(header, plaintext, signers);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);

  const actual = await cbor.decodeFirst(buf);
  t.is(actual.value[2].length, BIG_LENGHT);

  const verifiedBuf = await cose.sign.verify(buf, verifier);
  t.true(Buffer.isBuffer(verifiedBuf));
  t.true(verifiedBuf.length > 0);
  t.is(verifiedBuf.toString('utf8'), plaintext.toString('utf8'));
});
