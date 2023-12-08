# node-hkdf

The HMAC-based Key Derivation Function for node.js.

spec: https://tools.ietf.org/html/rfc5869

## install

    npm install hkdf

## use

    const HKDF = require('hkdf');
    
    // `key` is a Buffer, that can be serialized however one desires
    var hkdf = new HKDF('sha256', 'salt123', 'initialKeyingMaterial');
    var key = hkdf.derive('info', 42);
    key.toString('hex');
    
## license

Apache License 2.0
