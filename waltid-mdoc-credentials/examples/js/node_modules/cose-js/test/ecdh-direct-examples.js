/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const cose = require('../');
const test = require('ava');
const jsonfile = require('jsonfile');
const base64url = require('base64url');
const cbor = require('cbor');
const deepEqual = require('./util.js').deepEqual;

function randomSource (bytes) {
  if (bytes === 12) {
    return Buffer.from('C9CF4DF2FE6C632BF7886413', 'hex');
  } else {
    return Buffer.from('02D1F7E6F26C43D4868D87CEB2353161740AACF1F7163647984B522A848DF1C3', 'hex');
  }
}

test('create p256-hkdf-256-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p256-hkdf-256-01.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: randomSource
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create p256-hkdf-256-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p256-hkdf-256-02.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: randomSource
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

// create p256-hkdf-256-03

test('create p256-hkdf-512-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p256-hkdf-512-01.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: randomSource
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create p256-hkdf-512-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p256-hkdf-512-02.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: (bytes) => {
      if (bytes === 12) {
        return Buffer.from('C9CF4DF2FE6C632BF7886413', 'hex');
      } else {
        return Buffer.from('02D1F7E6F26C43D4868D87CEB2353161740AACF1F7163647984B522A848DF1C3', 'hex');
      }
    }
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

// create p256-hkdf-512-03

test('create p256-ss-hkdf-256-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p256-ss-hkdf-256-01.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    sender: {
      kty: example.input.enveloped.recipients[0].sender_key.kty,
      crv: example.input.enveloped.recipients[0].sender_key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].sender_key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].sender_key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].sender_key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: (bytes) => {
      if (bytes === 12) {
        return Buffer.from('D7923E677B71A3F40A179643', 'hex');
      } else {
        return Buffer.from('02D1F7E6F26C43D4868D87CEB2353161740AACF1F7163647984B522A848DF1C3C9CF4DF2FE6C632BF7886413F76E88523A8260B857D70B350027FD842B5E5947', 'hex');
      }
    }
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

// create p256-ss-hkdf-256-02

// create p256-ss-hkdf-256-03

// create p256-ss-hkdf-512-01

// create p256-ss-hkdf-512-02

// create p256-ss-hkdf-512-03

test('create p521-hkdf-256-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p521-hkdf-256-01.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: (bytes) => {
      if (bytes === 12) {
        return Buffer.from('3082660901A9B9CD87AACB71', 'hex');
      } else {
        return Buffer.from('02D1F7E6F26C43D4868D87CEB2353161740AACF1F7163647984B522A848DF1C3C9CF4DF2FE6C632BF7886413F76E885252908DF901D1581A045444DD996E1704B9B6', 'hex');
      }
    }
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create p521-hkdf-256-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p521-hkdf-256-02.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: (bytes) => {
      if (bytes === 12) {
        return Buffer.from('512C9CC879517ADCF0FA768E', 'hex');
      } else {
        return Buffer.from('02D1F7E6F26C43D4868D87CEB2353161740AACF1F7163647984B522A848DF1C3C9CF4DF2FE6C632BF7886413F76E88527FAC271A4C7EA34B7E28D7BBB54C682BED7A', 'hex');
      }
    }
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

// create p521-hkdf-256-03

test('create p521-hkdf-512-01', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p521-hkdf-512-01.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: (bytes) => {
      if (bytes === 12) {
        return Buffer.from('CFDC1CA6D690CF1964458D42', 'hex');
      } else {
        return Buffer.from('02D1F7E6F26C43D4868D87CEB2353161740AACF1F7163647984B522A848DF1C3C9CF4DF2FE6C632BF7886413F76E88526B37FD0B58734CA925A389AD361ECEF28358', 'hex');
      }
    }
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

test('create p521-hkdf-512-02', async t => {
  const example = jsonfile.readFileSync('test/Examples/ecdh-direct-examples/p521-hkdf-512-02.json');
  const p = example.input.enveloped.protected;
  const u = example.input.enveloped.unprotected;
  const plaintext = Buffer.from(example.input.plaintext);

  const recipient = [{
    key: {
      kty: example.input.enveloped.recipients[0].key.kty,
      kid: example.input.enveloped.recipients[0].key.kid,
      crv: example.input.enveloped.recipients[0].key.crv,
      x: base64url.toBuffer(example.input.enveloped.recipients[0].key.x),
      y: base64url.toBuffer(example.input.enveloped.recipients[0].key.y),
      d: base64url.toBuffer(example.input.enveloped.recipients[0].key.d)
    },
    p: example.input.enveloped.recipients[0].protected,
    u: example.input.enveloped.recipients[0].unprotected
  }];

  const options = {
    randomSource: (bytes) => {
      if (bytes === 12) {
        return Buffer.from('E89FD3534E1ABAF69C65CFE0', 'hex');
      } else {
        return Buffer.from('02D1F7E6F26C43D4868D87CEB2353161740AACF1F7163647984B522A848DF1C3C9CF4DF2FE6C632BF7886413F76E885238FB137F6C20764D89E26452937675B5E3B4', 'hex');
      }
    }
  };

  const header = { p: p, u: u };
  const buf = await cose.encrypt.create(header, plaintext, recipient, options);
  t.true(Buffer.isBuffer(buf));
  t.true(buf.length > 0);
  const actual = cbor.decodeFirstSync(buf);
  const expected = cbor.decodeFirstSync(example.output.cbor);
  t.true(deepEqual(actual, expected));
});

// create p521-hkdf-512-03

// create p521-ss-hkdf-256-01

// create p521-ss-hkdf-256-02

// create p521-ss-hkdf-256-03

// create p521-ss-hkdf-512-01

// create p521-ss-hkdf-512-02

// create p521-ss-hkdf-512-03
