[![Build Status](https://travis-ci.com/erdtman/aes-cbc-mac.svg?branch=master)](https://travis-ci.com/erdtman/aes-cbc-mac)
[![Coverage Status](https://coveralls.io/repos/github/erdtman/aes-cbc-mac/badge.svg?branch=master)](https://coveralls.io/github/erdtman/aes-cbc-mac?branch=master)
# aes-cbc-mac
Node implementation for aes cbc mac.

## Security Considerations

A number of attacks exist against Cipher Block Chaining Message Authentication Code (CBC-MAC) that need to be considered.

* A single key must only be used for messages of a fixed and known length.  If this is not the case, an attacker will be able to generate a message with a valid tag given two message and tag pairs.  This can be addressed by using different keys for messages of different lengths.  The current structure mitigates this problem, as a specific encoding structure that includes lengths is built and signed.  (CMAC also addresses this issue.)
* Cipher Block Chaining (CBC) mode, if the same key is used for both encryption and authentication operations, an attacker can produce messages with a valid authentication code.
* If the IV can be modified, then messages can be forged.  This is addressed by fixing the IV to all zeros.

## Installation
```
npm install aes-cbc-mac --save
```
## Testing
```
npm run test
```
## Usage
```javascript

const aesCbcMac = require('aes-cbc-mac');

const message = 'Important message';
const key = Buffer.from('849B57219DAE48DE646D07DBB533566E', 'hex');
const hashLen = 8; // bytes, 64 bits
const hash = aesCbcMac.create(message, key, hashLen);

console.log(hash);

```
