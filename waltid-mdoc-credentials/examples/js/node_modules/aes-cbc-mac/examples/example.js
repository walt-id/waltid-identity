
const aesCbcMac = require('../');

const message = 'Important message';
const key = Buffer.from('849B57219DAE48DE646D07DBB533566E', 'hex');
const hashLen = 8; // bytes, 64 bits
const hash = aesCbcMac.create(message, key, hashLen);

console.log(hash);
