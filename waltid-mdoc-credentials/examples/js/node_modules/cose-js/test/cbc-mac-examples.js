/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const cose = require('../');
const test = require('ava');
const jsonfile = require('jsonfile');
const base64url = require('base64url');
const cbor = require('cbor');
const deepEqual = require('./util.js').deepEqual;

test('create cbc-mac-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-01.json');
  const p = example.input.mac.protected;
  const u = example.input.mac.recipients[0].unprotected;
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p };
  const recipents = [{ key: key, u: u }];
  const buf = await cose.mac.create(header, plaintext, recipents);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create cbc-mac-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-02.json');
  const p = example.input.mac.protected;
  const u = example.input.mac.recipients[0].unprotected;
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p };
  const recipents = [{ key: key, u: u }];
  const buf = await cose.mac.create(header, plaintext, recipents);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);

  t.true(deepEqual(actual, expected));
});

test('create cbc-mac-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-03.json');
  const p = example.input.mac.protected;
  const u = example.input.mac.recipients[0].unprotected;
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p };
  const recipents = [{ key: key, u: u }];
  const buf = await cose.mac.create(header, plaintext, recipents);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);

  t.true(deepEqual(actual, expected));
});

test('create cbc-mac-04', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-04.json');
  const p = example.input.mac.protected;
  const u = example.input.mac.recipients[0].unprotected;
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p };
  const recipents = [{ key: key, u: u }];
  const buf = await cose.mac.create(header, plaintext, recipents);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);

  t.true(deepEqual(actual, expected));
});

test('create cbc-mac-enc-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-enc-01.json');
  const p = example.input.mac0.protected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p };
  const recipents = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipents);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);

  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);

  t.true(deepEqual(actual, expected));
});

test('create cbc-mac-enc-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-enc-02.json');
  const p = example.input.mac0.protected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p };
  const recipent = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipent);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);

  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);

  t.true(deepEqual(actual, expected));
});

test('create cbc-mac-enc-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-enc-03.json');
  const p = example.input.mac0.protected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p };
  const recipent = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipent);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);

  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);

  t.true(deepEqual(actual, expected));
});

test('create cbc-mac-enc-04', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-enc-04.json');
  const p = example.input.mac0.protected;
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);
  const plaintext = Buffer.from(example.input.plaintext);

  const header = { p: p };
  const recipent = { key: key };
  const buf = await cose.mac.create(header, plaintext, recipent);

  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);

  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);

  t.true(deepEqual(actual, expected));
});

test('verify cbc-mac-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-01.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify cbc-mac-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-02.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify cbc-mac-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-03.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify cbc-mac-04', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-04.json');
  const key = base64url.toBuffer(example.input.mac.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify cbc-mac-enc-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-enc-02.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify cbc-mac-enc-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-enc-02.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify cbc-mac-enc-03', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-enc-03.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});

test('verify cbc-mac-enc-04', async t => {
  const example = jsonfile.readFileSync('test/Examples/cbc-mac-examples/cbc-mac-enc-04.json');
  const key = base64url.toBuffer(example.input.mac0.recipients[0].key.k);

  const data = example.output.cbor;
  const buf = await cose.mac.read(data, key);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  t.is(buf.toString('utf8'), example.input.plaintext);
});
