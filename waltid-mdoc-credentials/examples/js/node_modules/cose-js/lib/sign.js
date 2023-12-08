/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const cbor = require('cbor');
const EC = require('elliptic').ec;
const crypto = require('crypto');
const NodeRSA = require('node-rsa');
const common = require('./common');
const EMPTY_BUFFER = common.EMPTY_BUFFER;
const Tagged = cbor.Tagged;

const SignTag = exports.SignTag = 98;
const Sign1Tag = exports.Sign1Tag = 18;

const AlgFromTags = {};
AlgFromTags[-7] = { sign: 'ES256', digest: 'SHA-256' };
AlgFromTags[-35] = { sign: 'ES384', digest: 'SHA-384' };
AlgFromTags[-36] = { sign: 'ES512', digest: 'SHA-512' };
AlgFromTags[-37] = { sign: 'PS256', digest: 'SHA-256' };
AlgFromTags[-38] = { sign: 'PS384', digest: 'SHA-384' };
AlgFromTags[-39] = { sign: 'PS512', digest: 'SHA-512' };
AlgFromTags[-257] = { sign: 'RS256', digest: 'SHA-256' };
AlgFromTags[-258] = { sign: 'RS384', digest: 'SHA-384' };
AlgFromTags[-259] = { sign: 'RS512', digest: 'SHA-512' };

const COSEAlgToNodeAlg = {
  ES256: { sign: 'p256', digest: 'sha256' },
  ES384: { sign: 'p384', digest: 'sha384' },
  ES512: { sign: 'p521', digest: 'sha512' },
  RS256: { sign: 'RSA-SHA256' },
  RS384: { sign: 'RSA-SHA384' },
  RS512: { sign: 'RSA-SHA512' },
  PS256: { alg: 'pss-sha256', saltLen: 32 },
  PS384: { alg: 'pss-sha384', saltLen: 48 },
  PS512: { alg: 'pss-sha512', saltLen: 64 }
};

function doSign (SigStructure, signer, alg) {
  if (!AlgFromTags[alg]) {
    throw new Error('Unknown algorithm, ' + alg);
  }
  if (!COSEAlgToNodeAlg[AlgFromTags[alg].sign]) {
    throw new Error('Unsupported algorithm, ' + AlgFromTags[alg].sign);
  }

  let ToBeSigned = cbor.encode(SigStructure);

  let sig;
  if (AlgFromTags[alg].sign.startsWith('ES')) {
    const hash = crypto.createHash(COSEAlgToNodeAlg[AlgFromTags[alg].sign].digest);
    hash.update(ToBeSigned);
    ToBeSigned = hash.digest();
    const ec = new EC(COSEAlgToNodeAlg[AlgFromTags[alg].sign].sign);
    const key = ec.keyFromPrivate(signer.key.d);
    const signature = key.sign(ToBeSigned);
    const bitLength = Math.ceil(ec.curve._bitLength / 8);
    sig = Buffer.concat([signature.r.toArrayLike(Buffer, undefined, bitLength), signature.s.toArrayLike(Buffer, undefined, bitLength)]);
  } else if (AlgFromTags[alg].sign.startsWith('PS')) {
    signer.key.dmp1 = signer.key.dp;
    signer.key.dmq1 = signer.key.dq;
    signer.key.coeff = signer.key.qi;
    const key = new NodeRSA().importKey(signer.key, 'components-private');
    key.setOptions({
      signingScheme: {
        scheme: COSEAlgToNodeAlg[AlgFromTags[alg].sign].alg.split('-')[0],
        hash: COSEAlgToNodeAlg[AlgFromTags[alg].sign].alg.split('-')[1],
        saltLength: COSEAlgToNodeAlg[AlgFromTags[alg].sign].saltLen
      }
    });
    sig = key.sign(ToBeSigned);
  } else {
    const sign = crypto.createSign(COSEAlgToNodeAlg[AlgFromTags[alg].sign].sign);
    sign.update(ToBeSigned);
    sign.end();
    sig = sign.sign(signer.key);
  }
  return sig;
}

exports.create = function (headers, payload, signers, options) {
  options = options || {};
  let u = headers.u || {};
  let p = headers.p || {};

  p = common.TranslateHeaders(p);
  u = common.TranslateHeaders(u);
  let bodyP = p || {};
  bodyP = (bodyP.size === 0) ? EMPTY_BUFFER : cbor.encode(bodyP);
  if (Array.isArray(signers)) {
    if (signers.length === 0) {
      throw new Error('There has to be at least one signer');
    }
    if (signers.length > 1) {
      throw new Error('Only one signer is supported');
    }
    // TODO handle multiple signers
    const signer = signers[0];
    const externalAAD = signer.externalAAD || EMPTY_BUFFER;
    let signerP = signer.p || {};
    let signerU = signer.u || {};

    signerP = common.TranslateHeaders(signerP);
    signerU = common.TranslateHeaders(signerU);
    const alg = signerP.get(common.HeaderParameters.alg);
    signerP = (signerP.size === 0) ? EMPTY_BUFFER : cbor.encode(signerP);

    const SigStructure = [
      'Signature',
      bodyP,
      signerP,
      externalAAD,
      payload
    ];

    const sig = doSign(SigStructure, signer, alg);
    if (p.size === 0 && options.encodep === 'empty') {
      p = EMPTY_BUFFER;
    } else {
      p = cbor.encode(p);
    }
    const signed = [p, u, payload, [[signerP, signerU, sig]]];
    return cbor.encodeAsync(options.excludetag ? signed : new Tagged(SignTag, signed));
  } else {
    const signer = signers;
    const externalAAD = signer.externalAAD || EMPTY_BUFFER;
    const alg = p.get(common.HeaderParameters.alg) || u.get(common.HeaderParameters.alg);
    const SigStructure = [
      'Signature1',
      bodyP,
      externalAAD,
      payload
    ];
    const sig = doSign(SigStructure, signer, alg);
    if (p.size === 0 && options.encodep === 'empty') {
      p = EMPTY_BUFFER;
    } else {
      p = cbor.encode(p);
    }
    const signed = [p, u, payload, sig];
    return cbor.encodeAsync(options.excludetag ? signed : new Tagged(Sign1Tag, signed), { canonical: true });
  }
};

function doVerify (SigStructure, verifier, alg, sig) {
  if (!AlgFromTags[alg]) {
    throw new Error('Unknown algorithm, ' + alg);
  }
  const nodeAlg = COSEAlgToNodeAlg[AlgFromTags[alg].sign];
  if (!nodeAlg) {
    throw new Error('Unsupported algorithm, ' + AlgFromTags[alg].sign);
  }
  const ToBeSigned = cbor.encode(SigStructure);

  if (AlgFromTags[alg].sign.startsWith('ES')) {
    const hash = crypto.createHash(nodeAlg.digest);
    hash.update(ToBeSigned);
    const msgHash = hash.digest();

    const pub = { x: verifier.key.x, y: verifier.key.y };
    const ec = new EC(nodeAlg.sign);
    const key = ec.keyFromPublic(pub);
    sig = { r: sig.slice(0, sig.length / 2), s: sig.slice(sig.length / 2) };
    if (!key.verify(msgHash, sig)) {
      throw new Error('Signature missmatch');
    }
  } else if (AlgFromTags[alg].sign.startsWith('PS')) {
    const key = new NodeRSA().importKey(verifier.key, 'components-public');
    key.setOptions({
      signingScheme: {
        scheme: COSEAlgToNodeAlg[AlgFromTags[alg].sign].alg.split('-')[0],
        hash: COSEAlgToNodeAlg[AlgFromTags[alg].sign].alg.split('-')[1],
        saltLength: COSEAlgToNodeAlg[AlgFromTags[alg].sign].saltLen
      }
    });
    if (!key.verify(ToBeSigned, sig, 'buffer', 'buffer')) {
      throw new Error('Signature missmatch');
    }
  } else {
    const verify = crypto.createVerify(nodeAlg.sign);
    verify.update(ToBeSigned);
    if (!verify.verify(verifier.key, sig)) {
      throw new Error('Signature missmatch');
    }
  }
}

function getSigner (signers, verifier) {
  for (let i = 0; i < signers.length; i++) {
    const kid = signers[i][1].get(common.HeaderParameters.kid); // TODO create constant for header locations
    if (kid.equals(Buffer.from(verifier.key.kid, 'utf8'))) {
      return signers[i];
    }
  }
}

function getCommonParameter (first, second, parameter) {
  let result;
  if (first.get) {
    result = first.get(parameter);
  }
  if (!result && second.get) {
    result = second.get(parameter);
  }
  return result;
}

exports.verify = async function (payload, verifier, options) {
  options = options || {};
  const obj = await cbor.decodeFirst(payload);
  return verifyInternal(verifier, options, obj);
};

exports.verifySync = function (payload, verifier, options) {
  options = options || {};
  const obj = cbor.decodeFirstSync(payload);
  return verifyInternal(verifier, options, obj);
};

function verifyInternal (verifier, options, obj) {
  options = options || {};
  let type = options.defaultType ? options.defaultType : SignTag;
  if (obj instanceof Tagged) {
    if (obj.tag !== SignTag && obj.tag !== Sign1Tag) {
      throw new Error('Unexpected cbor tag, \'' + obj.tag + '\'');
    }
    type = obj.tag;
    obj = obj.value;
  }

  if (!Array.isArray(obj)) {
    throw new Error('Expecting Array');
  }

  if (obj.length !== 4) {
    throw new Error('Expecting Array of lenght 4');
  }

  let [p, u, plaintext, signers] = obj;

  if (type === SignTag && !Array.isArray(signers)) {
    throw new Error('Expecting signature Array');
  }

  p = (!p.length) ? EMPTY_BUFFER : cbor.decodeFirstSync(p);
  u = (!u.size) ? EMPTY_BUFFER : u;

  const signer = (type === SignTag ? getSigner(signers, verifier) : signers);

  if (!signer) {
    throw new Error('Failed to find signer with kid' + verifier.key.kid);
  }

  if (type === SignTag) {
    const externalAAD = verifier.externalAAD || EMPTY_BUFFER;
    let [signerP, , sig] = signer;
    signerP = (!signerP.length) ? EMPTY_BUFFER : signerP;
    p = (!p.size) ? EMPTY_BUFFER : cbor.encode(p);
    const signerPMap = cbor.decode(signerP);
    const alg = signerPMap.get(common.HeaderParameters.alg);
    const SigStructure = [
      'Signature',
      p,
      signerP,
      externalAAD,
      plaintext
    ];
    doVerify(SigStructure, verifier, alg, sig);
    return plaintext;
  } else {
    const externalAAD = verifier.externalAAD || EMPTY_BUFFER;

    const alg = getCommonParameter(p, u, common.HeaderParameters.alg);
    p = (!p.size) ? EMPTY_BUFFER : cbor.encode(p);
    const SigStructure = [
      'Signature1',
      p,
      externalAAD,
      plaintext
    ];
    doVerify(SigStructure, verifier, alg, signer);
    return plaintext;
  }
}
