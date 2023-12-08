/* jshint esversion: 6 */
/* jslint node: true */
'use strict';
const test = require('ava');
const aesCbcMac = require('../');

test('AES-MAC-128/64', (t) => {
  const msg = Buffer.from('84634D414343A1010E4054546869732069732074686520636F6E74656E742E', 'hex');
  const key = Buffer.from('849B57219DAE48DE646D07DBB533566E', 'hex');

  const tag = aesCbcMac.create(key, msg, 8);
  t.is(tag.toString('hex').toLocaleUpperCase(), 'C1CA820E6E247089');
});

test('AES-MAC-128/128', (t) => {
  const msg = Buffer.from('84634D414344A10118194054546869732069732074686520636F6E74656E742E', 'hex');
  const key = Buffer.from('849B57219DAE48DE646D07DBB533566E', 'hex');

  const tag = aesCbcMac.create(key, msg, 16);
  t.is(tag.toString('hex').toLocaleUpperCase(), 'B242D2A935FEB4D66FF8334AC95BF72B');
});

test('AES-MAC-256/64', (t) => {
  const msg = Buffer.from('84634D414343A1010F4054546869732069732074686520636F6E74656E742E', 'hex');
  const key = Buffer.from('849B57219DAE48DE646D07DBB533566E976686457C1491BE3A76DCEA6C427188', 'hex');

  const tag = aesCbcMac.create(key, msg, 8);
  t.is(tag.toString('hex').toLocaleUpperCase(), '9E1226BA1F81B848');
});

test('AES-MAC-256/128', (t) => {
  const msg = Buffer.from('84634D414344A101181A4054546869732069732074686520636F6E74656E742E', 'hex');
  const key = Buffer.from('849B57219DAE48DE646D07DBB533566E976686457C1491BE3A76DCEA6C427188', 'hex');

  const tag = aesCbcMac.create(key, msg, 16);
  t.is(tag.toString('hex').toLocaleUpperCase(), 'DB9C7598A0751C5FF3366B6205BD2AA9');
});

test('AES-MAC invalid key lenght', (t) => {
  try {
    const msg = Buffer.from('84', 'hex');
    const key = Buffer.from('849B57219DAE48DE646D07DBB533566E97668', 'hex');

    aesCbcMac.create(key, msg, 16);
    t.is(false); // Must not get here
  } catch (error) {
    t.is(error.message, 'Unsupported key length 18');
  }
});

test('AES-MAC invalid key type', (t) => {
  try {
    const msg = Buffer.from('84', 'hex');

    aesCbcMac.create('asdad', msg, 16);
    t.is(false); // Must not get here
  } catch (error) {
    t.is(error.message, 'Key must be of type Buffer');
  }
});

test('AES-MAC invlaid msg type', (t) => {
  try {
    const key = Buffer.from('84', 'hex');

    aesCbcMac.create(key, 'asdf', 16);
    t.is(false); // Must not get here
  } catch (error) {
    t.is(error.message, 'Msg must be of type Buffer');
  }
});

test('AES-MAC invalid len', (t) => {
  try {
    const key = Buffer.from('84', 'hex');
    const msg = Buffer.from('84', 'hex');

    aesCbcMac.create(key, msg, 128);
    t.is(false); // Must not get here
  } catch (error) {
    t.is(error.message, 'Len must be 8 or 16');
  }
});
