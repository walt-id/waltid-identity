/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const cose = require('../');
const test = require('ava');
const jsonfile = require('jsonfile');
const base64url = require('base64url');
const cbor = require('cbor');
const deepEqual = require('./util.js').deepEqual;

test('create HMac-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/HMac-01.json');
  const p = example.input.mac0.protected;
  const u = example.input.mac0.unprotected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p, u: u };
  const recipents = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipents);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('verify HMac-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/HMac-01.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('create mac-pass-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-pass-01.json');

  const u = example.input.mac0.unprotected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const header = { u: u };
  const recipents = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipents);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create mac-pass-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-pass-02.json');
  const p = undefined;
  const u = example.input.mac0.unprotected;
  const external = Buffer.from(example.input.mac0.external, 'hex');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const options = { encodep: 'empty' };

  const header = { p: p, u: u };
  const recipents = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipents, external, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create mac-pass-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-pass-03.json');
  const p = undefined;
  const u = example.input.mac0.unprotected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);
  const options = { encodep: 'empty', excludetag: true };

  const recipents = { key: key };
  const header = { p: p, u: u };
  const external = null;
  const buf = await cose.mac.create(header, plaintext, recipents, external, options);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('verify mac-pass-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-pass-01.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify mac-pass-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-pass-02.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const external = Buffer.from(example.input.mac0.external, 'hex');

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key, external);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify mac-pass-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-pass-03.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify mac-fail-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-fail-01.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;

  try {
    await cose.mac.read(data, key);
    t.fail('Unexpected cbor tag, \'992\'');
  } catch (error) {
    t.is(error.message, 'Unexpected cbor tag, \'992\'');
  }
});

test('verify mac-fail-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-fail-02.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;

  try {
    await cose.mac.read(data, key);
    t.fail('Tag mismatch');
  } catch (error) {
    t.is(error.message, 'Tag mismatch');
  }
});

test('verify mac-fail-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-fail-03.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;

  try {
    await cose.mac.read(data, key);
    t.fail('Unknown algorithm, -999');
  } catch (error) {
    t.is(error.message, 'Unknown algorithm, -999');
  }
});

test('verify mac-fail-04', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-fail-04.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;

  try {
    await cose.mac.read(data, key);
    t.fail('Unknown algorithm, Unknown');
  } catch (error) {
    t.is(error.message, 'Unknown algorithm, Unknown');
  }
});

test('verify mac-fail-06', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-fail-06.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;

  try {
    await cose.mac.read(data, key);
    t.fail('Tag mismatch');
  } catch (error) {
    t.is(error.message, 'Tag mismatch');
  }
});

test('verify mac-fail-07', async t => {
  const example = jsonfile.readFileSync('test/Examples/mac0-tests/mac-fail-07.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const data = example.output.cbor;

  try {
    await cose.mac.read(data, key);
    t.fail('Tag mismatch');
  } catch (error) {
    t.is(error.message, 'Tag mismatch');
  }
});
