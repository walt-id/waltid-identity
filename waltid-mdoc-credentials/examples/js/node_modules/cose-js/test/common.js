/* jshint esversion: 6 */
/* jslint node: true */
'use strict';
const test = require('ava');
const cose = require('../');

test('translate headers', (t) => {
  let h = cose.common.TranslateHeaders({});
  t.is(h.constructor.name, 'Map');
  h = cose.common.TranslateHeaders({ alg: 'SHA-256', crit: 2 });
  t.is(h.constructor.name, 'Map');
  t.is(h.size, 2);
  t.is(h.get(cose.common.HeaderParameters.alg), 5);
  t.is(h.get(cose.common.HeaderParameters.crit), 2);
});

/*
test('translate headers', (t) => {
  const result = cose.common.TranslateHeaders({
    'ephemeral_key': Buffer.from('beef', 'hex'),
    'partyUNonce': Buffer.from('dead', 'hex'),
    'kid': Buffer.from('0b0b', 'hex'),
  });
  console.log(result);
});
*/

test('invalid', (t) => {
  t.throws(() => {
    cose.common.TranslateHeaders({ 'fizzle stomp': 12 });
  });
});

test('xor1', (t) => {
  const a = Buffer.from('00ff0f', 'hex');
  const b = Buffer.from('f0f0', 'hex');
  const actual = cose.common.xor(a, b);
  const expected = '000fff';
  t.is(actual.toString('hex'), expected);
});

test('xor2', (t) => {
  const a = Buffer.from('f0f0', 'hex');
  const b = Buffer.from('00ff0f', 'hex');
  const actual = cose.common.xor(a, b);
  const expected = '000fff';
  t.is(actual.toString('hex'), expected);
});

test('xor3', (t) => {
  const a = Buffer.from('f0f0f0', 'hex');
  const b = Buffer.from('00ff0f', 'hex');
  const actual = cose.common.xor(a, b);
  const expected = 'f00fff';
  t.is(actual.toString('hex'), expected);
});
