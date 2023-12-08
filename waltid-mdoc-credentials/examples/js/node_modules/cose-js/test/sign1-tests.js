/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const cose = require('../');
const test = require('ava');
const jsonfile = require('jsonfile');
const base64url = require('base64url');
const cbor = require('cbor');
const { deepEqual } = require('./util.js');

test('create sign-pass-01', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-pass-01.json');
  const u = example.input.sign0.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const signer = {
    key: {
      d: base64url.toBuffer(example.input.sign0.key.d)
    }
  };

  const header = { u: u };
  const buf = await cose.sign.create(header, plaintext, signer);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create sign-pass-02', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-pass-02.json');
  const p = example.input.sign0.protected;
  const u = example.input.sign0.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const signer = {
    key: {
      d: base64url.toBuffer(example.input.sign0.key.d)
    },
    externalAAD: Buffer.from(example.input.sign0.external, 'hex')
  };

  const header = { p: p, u: u };
  const buf = await cose.sign.create(header, plaintext, signer);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create sign-pass-03', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-pass-03.json');
  const p = example.input.sign0.protected;
  const u = example.input.sign0.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const signer = {
    key: {
      d: base64url.toBuffer(example.input.sign0.key.d)
    }
  };

  const header = { p: p, u: u };
  const options = { excludetag: true };
  const buf = await cose.sign.create(header, plaintext, signer, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('verify sign-pass-01', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-pass-01.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    }
  };

  const signature = Buffer.from(example.output.cbor, 'hex');

  const buf = await cose.sign.verify(signature, verifier);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify sign-pass-02', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-pass-02.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    },
    externalAAD: Buffer.from(example.input.sign0.external, 'hex')
  };

  const signature = Buffer.from(example.output.cbor, 'hex');

  const buf = await cose.sign.verify(signature, verifier);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify sign-pass-03', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-pass-03.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    }
  };

  const signature = Buffer.from(example.output.cbor, 'hex');

  const options = { defaultType: cose.sign.Sign1Tag };
  const buf = await cose.sign.verify(signature, verifier, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify sign-fail-01', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-fail-01.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    }
  };

  const signature = Buffer.from(example.output.cbor, 'hex');
  try {
    await cose.sign.verify(signature, verifier);
    t.fail('Unexpected cbor tag, \'998\'');
  } catch (error) {
    t.is(error.message, 'Unexpected cbor tag, \'998\'');
  }
});

test('verify sign-fail-02', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-fail-02.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    }
  };

  const signature = Buffer.from(example.output.cbor, 'hex');
  try {
    await cose.sign.verify(signature, verifier);
    t.fail('Signature missmatch');
  } catch (error) {
    t.is(error.message, 'Signature missmatch');
  }
});

test('verify sign-fail-03', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-fail-03.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    }
  };

  const signature = Buffer.from(example.output.cbor, 'hex');
  try {
    await cose.sign.verify(signature, verifier);
    t.fail('Unknown algorithm, -999');
  } catch (error) {
    t.is(error.message, 'Unknown algorithm, -999');
  }
});

test('verify sign-fail-04', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-fail-04.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    }
  };

  const signature = Buffer.from(example.output.cbor, 'hex');
  try {
    await cose.sign.verify(signature, verifier);
    t.fail('Unknown algorithm, unknown');
  } catch (error) {
    t.is(error.message, 'Unknown algorithm, unknown');
  }
});

test('verify sign-fail-06', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-fail-06.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    }
  };

  const signature = Buffer.from(example.output.cbor, 'hex');
  try {
    await cose.sign.verify(signature, verifier);
    t.fail('Signature missmatch');
  } catch (error) {
    t.is(error.message, 'Signature missmatch');
  }
});

test('verify sign-fail-07', async (t) => {
  const example = jsonfile.readFileSync('test/Examples/sign1-tests/sign-fail-07.json');

  const verifier = {
    key: {
      x: base64url.toBuffer(example.input.sign0.key.x),
      y: base64url.toBuffer(example.input.sign0.key.y)
    }
  };

  const signature = Buffer.from(example.output.cbor, 'hex');
  try {
    await cose.sign.verify(signature, verifier);
    t.fail('Signature missmatch');
  } catch (error) {
    t.is(error.message, 'Signature missmatch');
  }
});
