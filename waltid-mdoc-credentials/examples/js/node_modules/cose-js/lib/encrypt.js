/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const cbor = require('cbor');
const crypto = require('crypto');
const Promise = require('any-promise');
const common = require('./common');
const HKDF = require('node-hkdf-sync');

const Tagged = cbor.Tagged;

const EMPTY_BUFFER = common.EMPTY_BUFFER;
const EncryptTag = exports.EncryptTag = 96;
const Encrypt0Tag = exports.Encrypt0Tag = 16;

const runningInNode = common.runningInNode;

const TagToAlg = {
  1: 'A128GCM',
  2: 'A192GCM',
  3: 'A256GCM',
  10: 'AES-CCM-16-64-128',
  11: 'AES-CCM-16-64-256',
  12: 'AES-CCM-64-64-128',
  13: 'AES-CCM-64-64-256',
  30: 'AES-CCM-16-128-128',
  31: 'AES-CCM-16-128-256',
  32: 'AES-CCM-64-128-128',
  33: 'AES-CCM-64-128-256'
};

const COSEAlgToNodeAlg = {
  A128GCM: 'aes-128-gcm',
  A192GCM: 'aes-192-gcm',
  A256GCM: 'aes-256-gcm',

  'AES-CCM-16-64-128': 'aes-128-ccm',
  'AES-CCM-16-64-256': 'aes-256-ccm',
  'AES-CCM-64-64-128': 'aes-128-ccm',
  'AES-CCM-64-64-256': 'aes-256-ccm',
  'AES-CCM-16-128-128': 'aes-128-ccm',
  'AES-CCM-16-128-256': 'aes-256-ccm',
  'AES-CCM-64-128-128': 'aes-128-ccm',
  'AES-CCM-64-128-256': 'aes-256-ccm'
};

const isNodeAlg = {
  1: true, // A128GCM
  2: true, // A192GCM
  3: true // A256GCM
};

const isCCMAlg = {
  10: true, // AES-CCM-16-64-128
  11: true, // AES-CCM-16-64-256
  12: true, // AES-CCM-64-64-128
  13: true, // AES-CCM-64-64-256
  30: true, // AES-CCM-16-128-128
  31: true, // AES-CCM-16-128-256
  32: true, // AES-CCM-64-128-128
  33: true // AES-CCM-64-128-256
};

const authTagLength = {
  1: 16,
  2: 16,
  3: 16,
  10: 8, // AES-CCM-16-64-128
  11: 8, // AES-CCM-16-64-256
  12: 8, // AES-CCM-64-64-128
  13: 8, // AES-CCM-64-64-256
  30: 16, // AES-CCM-16-128-128
  31: 16, // AES-CCM-16-128-256
  32: 16, // AES-CCM-64-128-128
  33: 16 // AES-CCM-64-128-256
};

const ivLenght = {
  1: 12, // A128GCM
  2: 12, // A192GCM
  3: 12, // A256GCM
  10: 13, // AES-CCM-16-64-128
  11: 13, // AES-CCM-16-64-256
  12: 7, // AES-CCM-64-64-128
  13: 7, // AES-CCM-64-64-256
  30: 13, // AES-CCM-16-128-128
  31: 13, // AES-CCM-16-128-256
  32: 7, // AES-CCM-64-128-128
  33: 7 // AES-CCM-64-128-256
};

const keyLength = {
  1: 16, // A128GCM
  2: 24, // A192GCM
  3: 32, // A256GCM
  10: 16, // AES-CCM-16-64-128
  11: 32, // AES-CCM-16-64-256
  12: 16, // AES-CCM-64-64-128
  13: 32, // AES-CCM-64-64-256
  30: 16, // AES-CCM-16-128-128
  31: 32, // AES-CCM-16-128-256
  32: 16, // AES-CCM-64-128-128
  33: 32, // AES-CCM-64-128-256
  'P-521': 66,
  'P-256': 32
};

const HKDFAlg = {
  'ECDH-ES': 'sha256',
  'ECDH-ES-512': 'sha512',
  'ECDH-SS': 'sha256',
  'ECDH-SS-512': 'sha512'
};

const nodeCRV = {
  'P-521': 'secp521r1',
  'P-256': 'prime256v1'
};

function createAAD (p, context, externalAAD) {
  p = (!p.size) ? EMPTY_BUFFER : cbor.encode(p);
  const encStructure = [
    context,
    p,
    externalAAD
  ];
  return cbor.encode(encStructure);
}

function _randomSource (bytes) {
  return crypto.randomBytes(bytes);
}

function nodeEncrypt (payload, key, alg, iv, aad, ccm = false) {
  const nodeAlg = COSEAlgToNodeAlg[TagToAlg[alg]];
  const chiperOptions = ccm ? { authTagLength: authTagLength[alg] } : null;
  const aadOptions = ccm ? { plaintextLength: Buffer.byteLength(payload) } : null;
  const cipher = crypto.createCipheriv(nodeAlg, key, iv, chiperOptions);
  cipher.setAAD(aad, aadOptions);
  return Buffer.concat([
    cipher.update(payload),
    cipher.final(),
    cipher.getAuthTag()
  ]);
}

function createContext (rp, alg, partyUNonce) {
  return cbor.encode([
    alg, // AlgorithmID
    [ // PartyUInfo
      null, // identity
      (partyUNonce || null), // nonce
      null // other
    ],
    [ // PartyVInfo
      null, // identity
      null, // nonce
      null // other
    ],
    [
      keyLength[alg] * 8, // keyDataLength
      rp // protected
    ]
  ]);
}

exports.create = function (headers, payload, recipients, options) {
  return new Promise((resolve, reject) => {
    options = options || {};
    const externalAAD = options.externalAAD || EMPTY_BUFFER;
    const randomSource = options.randomSource || _randomSource;
    let u = headers.u || {};
    let p = headers.p || {};

    p = common.TranslateHeaders(p);
    u = common.TranslateHeaders(u);

    const alg = p.get(common.HeaderParameters.alg) || u.get(common.HeaderParameters.alg);

    if (!alg) {
      throw new Error('Missing mandatory parameter \'alg\'');
    }

    if (Array.isArray(recipients)) {
      if (recipients.length === 0) {
        throw new Error('There has to be at least one recipent');
      }
      if (recipients.length > 1) {
        throw new Error('Encrypting with multiple recipents is not implemented');
      }

      let iv;
      if (options.contextIv) {
        const partialIv = randomSource(2);
        iv = common.xor(partialIv, options.contextIv);
        u.set(common.HeaderParameters.Partial_IV, partialIv);
      } else {
        iv = randomSource(ivLenght[alg]);
        u.set(common.HeaderParameters.IV, iv);
      }

      const aad = createAAD(p, 'Encrypt', externalAAD);

      let key;
      let recipientStruct;
      // TODO do a more accurate check
      if (recipients[0] && recipients[0].p &&
        (recipients[0].p.alg === 'ECDH-ES' ||
          recipients[0].p.alg === 'ECDH-ES-512' ||
          recipients[0].p.alg === 'ECDH-SS' ||
          recipients[0].p.alg === 'ECDH-SS-512')) {
        const recipient = crypto.createECDH(nodeCRV[recipients[0].key.crv]);
        const generated = crypto.createECDH(nodeCRV[recipients[0].key.crv]);
        recipient.setPrivateKey(recipients[0].key.d);
        let pk = randomSource(keyLength[recipients[0].key.crv]);
        if (recipients[0].p.alg === 'ECDH-ES' ||
          recipients[0].p.alg === 'ECDH-ES-512') {
          pk = randomSource(keyLength[recipients[0].key.crv]);
          pk[0] = (recipients[0].key.crv !== 'P-521' || pk[0] === 1) ? pk[0] : 0;
        } else {
          pk = recipients[0].sender.d;
        }

        generated.setPrivateKey(pk);
        const senderPublicKey = generated.getPublicKey();
        const recipientPublicKey = Buffer.concat([
          Buffer.from('04', 'hex'),
          recipients[0].key.x,
          recipients[0].key.y
        ]);

        const generatedKey = common.TranslateKey({
          crv: recipients[0].key.crv,
          x: senderPublicKey.slice(1, keyLength[recipients[0].key.crv] + 1), // TODO slice based on key length
          y: senderPublicKey.slice(keyLength[recipients[0].key.crv] + 1),
          kty: 'EC2' // TODO use real value
        });
        const rp = cbor.encode(common.TranslateHeaders(recipients[0].p));
        const ikm = generated.computeSecret(recipientPublicKey);
        let partyUNonce = null;
        if (recipients[0].p.alg === 'ECDH-SS' || recipients[0].p.alg === 'ECDH-SS-512') {
          partyUNonce = randomSource(64); // TODO use real value
        }
        const context = createContext(rp, alg, partyUNonce);
        const nrBytes = keyLength[alg];
        const hkdf = new HKDF(HKDFAlg[recipients[0].p.alg], undefined, ikm);
        key = hkdf.derive(context, nrBytes);
        let ru = recipients[0].u;

        if (recipients[0].p.alg === 'ECDH-ES' ||
          recipients[0].p.alg === 'ECDH-ES-512') {
          ru.ephemeral_key = generatedKey;
        } else {
          ru.static_key = generatedKey;
        }

        ru.partyUNonce = partyUNonce;
        ru = common.TranslateHeaders(ru);

        recipientStruct = [[rp, ru, EMPTY_BUFFER]];
      } else {
        key = recipients[0].key;
        const ru = common.TranslateHeaders(recipients[0].u);
        recipientStruct = [[EMPTY_BUFFER, ru, EMPTY_BUFFER]];
      }

      let ciphertext;
      if (isNodeAlg[alg]) {
        ciphertext = nodeEncrypt(payload, key, alg, iv, aad);
      } else if (isCCMAlg[alg] && runningInNode()) {
        ciphertext = nodeEncrypt(payload, key, alg, iv, aad, true);
      } else {
        throw new Error('No implementation for algorithm, ' + alg);
      }

      if (p.size === 0 && options.encodep === 'empty') {
        p = EMPTY_BUFFER;
      } else {
        p = cbor.encode(p);
      }

      const encrypted = [p, u, ciphertext, recipientStruct];
      resolve(cbor.encode(options.excludetag ? encrypted : new Tagged(EncryptTag, encrypted)));
    } else {
      let iv;
      if (options.contextIv) {
        const partialIv = randomSource(2);
        iv = common.xor(partialIv, options.contextIv);
        u.set(common.HeaderParameters.Partial_IV, partialIv);
      } else {
        iv = randomSource(ivLenght[alg]);
        u.set(common.HeaderParameters.IV, iv);
      }

      const key = recipients.key;

      const aad = createAAD(p, 'Encrypt0', externalAAD);
      let ciphertext;
      if (isNodeAlg[alg]) {
        ciphertext = nodeEncrypt(payload, key, alg, iv, aad);
      } else if (isCCMAlg[alg] && runningInNode()) {
        ciphertext = nodeEncrypt(payload, key, alg, iv, aad, true);
      } else {
        throw new Error('No implementation for algorithm, ' + alg);
      }

      if (p.size === 0 && options.encodep === 'empty') {
        p = EMPTY_BUFFER;
      } else {
        p = cbor.encode(p);
      }
      const encrypted = [p, u, ciphertext];
      resolve(cbor.encode(options.excludetag ? encrypted : new Tagged(Encrypt0Tag, encrypted)));
    }
  });
};

function nodeDecrypt (ciphertext, key, alg, iv, tag, aad, ccm = false) {
  const nodeAlg = COSEAlgToNodeAlg[TagToAlg[alg]];
  const chiperOptions = ccm ? { authTagLength: authTagLength[alg] } : null;
  const aadOptions = ccm ? { plaintextLength: Buffer.byteLength(ciphertext) } : null;
  const decipher = crypto.createDecipheriv(nodeAlg, key, iv, chiperOptions);
  decipher.setAuthTag(tag);
  decipher.setAAD(aad, aadOptions);
  return Buffer.concat([decipher.update(ciphertext), decipher.final()]);
}

exports.read = async function (data, key, options) {
  options = options || {};
  const externalAAD = options.externalAAD || EMPTY_BUFFER;
  let obj = await cbor.decodeFirst(data);
  let msgTag = options.defaultType ? options.defaultType : EncryptTag;
  if (obj instanceof Tagged) {
    if (obj.tag !== EncryptTag && obj.tag !== Encrypt0Tag) {
      throw new Error('Unknown tag, ' + obj.tag);
    }
    msgTag = obj.tag;
    obj = obj.value;
  }

  if (!Array.isArray(obj)) {
    throw new Error('Expecting Array');
  }

  if (msgTag === EncryptTag && obj.length !== 4) {
    throw new Error('Expecting Array of lenght 4 for COSE Encrypt message');
  }

  if (msgTag === Encrypt0Tag && obj.length !== 3) {
    throw new Error('Expecting Array of lenght 4 for COSE Encrypt0 message');
  }

  let [p, u, ciphertext] = obj;

  p = (p.length === 0) ? EMPTY_BUFFER : cbor.decodeFirstSync(p);
  p = (!p.size) ? EMPTY_BUFFER : p;
  u = (!u.size) ? EMPTY_BUFFER : u;

  const alg = (p !== EMPTY_BUFFER) ? p.get(common.HeaderParameters.alg) : (u !== EMPTY_BUFFER) ? u.get(common.HeaderParameters.alg) : undefined;
  if (!TagToAlg[alg]) {
    throw new Error('Unknown or unsupported algorithm ' + alg);
  }

  let iv = u.get(common.HeaderParameters.IV);
  const partialIv = u.get(common.HeaderParameters.Partial_IV);
  if (iv && partialIv) {
    throw new Error('IV and Partial IV parameters MUST NOT both be present in the same security layer');
  }
  if (partialIv && !options.contextIv) {
    throw new Error('Context IV must be provided when Partial IV is used');
  }
  if (partialIv && options.contextIv) {
    iv = common.xor(partialIv, options.contextIv);
  }

  const tagLength = authTagLength[alg];
  const tag = ciphertext.slice(ciphertext.length - tagLength, ciphertext.length);
  ciphertext = ciphertext.slice(0, ciphertext.length - tagLength);

  const aad = createAAD(p, (msgTag === EncryptTag ? 'Encrypt' : 'Encrypt0'), externalAAD);
  if (isNodeAlg[alg]) {
    return nodeDecrypt(ciphertext, key, alg, iv, tag, aad);
  } else if (isCCMAlg[alg] && runningInNode()) {
    return nodeDecrypt(ciphertext, key, alg, iv, tag, aad, true);
  } else {
    throw new Error('No implementation for algorithm, ' + alg);
  }
};
