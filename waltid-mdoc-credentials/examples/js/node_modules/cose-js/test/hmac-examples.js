/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const cose = require('../');
const test = require('ava');
const jsonfile = require('jsonfile');
const base64url = require('base64url');
const cbor = require('cbor');
const deepEqual = require('./util.js').deepEqual;

test('create HMac-enc-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-01.json');
  const p = example.input.mac0.protected;
  const u = example.input.mac0.unprotected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const header = { p: p, u: u };
  const recipeient = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipeient);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create HMac-enc-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-02.json');
  const p = example.input.mac0.protected;
  const u = example.input.mac0.unprotected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const header = { p: p, u: u };
  const recipient = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipient);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create HMac-enc-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-03.json');
  const p = example.input.mac0.protected;
  const u = example.input.mac0.unprotected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const header = { p: p, u: u };
  const recipient = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipient);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

// HMac-enc-04 is a negative test and cannot be recreated

test('create HMac-enc-05', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-05.json');
  const p = example.input.mac0.protected;
  const u = example.input.mac0.unprotected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const header = { p: p, u: u };
  const recipeint = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipeint);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('verify HMac-enc-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-01.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify HMac-enc-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-02.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify HMac-enc-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-03.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify HMac-enc-04', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-04.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;
  try {
    await cose.mac.read(data, key);
    t.fail('Tag mismatch');
  } catch (error) {
    t.is(error.message, 'Tag mismatch');
  }
});

test('verify HMac-enc-05', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-enc-05.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('create HMac-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-01.json');
  const p = example.input.mac.protected;
  const u = example.input.mac.recipients[0].unprotected;
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const recipents = [{ key: key, u: u }];
  const header = { p: p };
  const buf = await cose.mac.create(header, plaintext, recipents);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create HMac-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-02.json');
  const p = example.input.mac.protected;
  const u = example.input.mac.recipients[0].unprotected;
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const recipents = [{ key: key, u: u }];
  const header = { p: p };
  const buf = await cose.mac.create(header, plaintext, recipents);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create HMac-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-03.json');
  const p = example.input.mac.protected;
  const u = example.input.mac.recipients[0].unprotected;
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const recipents = [{ key: key, u: u }];
  const header = { p: p };
  const buf = await cose.mac.create(header, plaintext, recipents);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

// HMac-04 is a negative test and cannot be recreated

test('create HMac-05', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-05.json');
  const p = example.input.mac.protected;
  const u = example.input.mac.recipients[0].unprotected;
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const recipents = [{ key: key, u: u }];
  const header = { p: p };
  const buf = await cose.mac.create(header, plaintext, recipents);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('verify HMac-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-01.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify HMac-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-02.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify HMac-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-03.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify HMac-04', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-04.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const data = example.output.cbor;
  try {
    await cose.mac.read(data, key);
    t.fail('Tag mismatch');
  } catch (error) {
    t.is(error.message, 'Tag mismatch');
  }
});

test('verify HMac-05', async t => {
  const example = jsonfile.readFileSync('test/Examples/hmac-examples/HMac-05.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});
