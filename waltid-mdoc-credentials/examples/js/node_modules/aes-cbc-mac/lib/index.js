/* jshint esversion: 6 */
/* jslint node: true */
'use strict';

const crypto = require('crypto');

const toAlg = {
  16: 'aes-128-cbc',
  32: 'aes-256-cbc'
};

const supportedLen = {
  8: true,
  16: true
};

const iv = Buffer.alloc(16, 0); // Initialization vector.

exports.create = function (key, msg, len) {
  if (!Buffer.isBuffer(key)) {
    throw new Error('Key must be of type Buffer');
  }
  if (!Buffer.isBuffer(msg)) {
    throw new Error('Msg must be of type Buffer');
  }
  if (!supportedLen[len]) {
    throw new Error('Len must be 8 or 16');
  }

  const algorithm = toAlg[key.length];

  if (!algorithm) {
    throw new Error('Unsupported key length ' + key.length);
  }

  const msgLen = msg.length;
  const padLen = 16 - (msgLen % 16);
  const padding = (padLen === 16) ? Buffer.alloc(0, 0) : Buffer.alloc(padLen, 0);
  const paddedMsg = Buffer.concat([msg, padding]);

  const cipher = crypto.createCipheriv(algorithm, key, iv);
  const enc = cipher.update(paddedMsg);
  const tagStart = enc.length - 16;
  const tag = enc.slice(tagStart, tagStart + len);

  return tag;
};
