const cose = require('../');

async function run () {
  try {
    const plaintext = 'Important message!';
    const headers = {
      p: { alg: 'ES256' },
      u: { kid: '11' }
    };
    const signer = {
      key: {
        d: Buffer.from('6c1382765aec5358f117733d281c1c7bdc39884d04a45a1e6c67c858bc206c19', 'hex')
      }
    };
    const buf = await cose.sign.create(headers, plaintext, signer);
    console.log('Signed message: ' + buf.toString('hex'));
  } catch (error) {
    console.log(error);
  }

  try {
    const verifier = {
      key: {
        x: Buffer.from('143329cce7868e416927599cf65a34f3ce2ffda55a7eca69ed8919a394d42f0f', 'hex'),
        y: Buffer.from('60f7f1a780d8a783bfb7a2dd6b2796e8128dbbcef9d3d168db9529971a36e7b9', 'hex')
      }
    };
    const COSEMessage = Buffer.from('d28443a10126a10442313172496d706f7274616e74206d6573736167652158404c2b6b66dfedc4cfef0f221cf7ac7f95087a4c4245fef0063a0fd4014b670f642d31e26d38345bb4efcdc7ded3083ab4fe71b62a23f766d83785f044b20534f9', 'hex');
    const buf = await cose.sign.verify(COSEMessage, verifier);
    console.log('Verified message: ' + buf.toString('utf8'));
  } catch (error) {
    console.log(error);
  }
}
run();
