const crypto = require('crypto');
const cose = require('../lib');

async function sample () {
  const plaintext = 'Important message!';
  const headers = {
    p: { alg: 'RS256' },
    u: { kid: '11' }
  };
  const keys = crypto.generateKeyPairSync('rsa', {
    modulusLength: 2048,
    publicKeyEncoding: {
      type: 'spki',
      format: 'pem'
    },
    privateKeyEncoding: {
      type: 'pkcs8',
      format: 'pem'
    }
  });
  const signer = {
    key: keys.privateKey
  };

  const msg = await cose.sign.create(headers, plaintext, signer);
  console.log('Signed message: ' + msg.toString('hex'));

  const verifier = {
    key: keys.publicKey
  };

  const plaintext2 = await cose.sign.verify(msg, verifier);
  console.log('Verified message: ' + plaintext2.toString('utf8'));
}
sample();
