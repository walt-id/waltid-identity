const cose = require('../');

async function run () {
  try {
    const plaintext = 'Secret message!';
    const headers = {
      p: { alg: 'A128GCM' },
      u: { kid: 'our-secret' }
    };
    const recipient = {
      key: Buffer.from('231f4c4d4d3051fdc2ec0a3851d5b383', 'hex')
    };
    const buf = await cose.encrypt.create(headers, plaintext, recipient);
    console.log('Encrypted message: ' + buf.toString('hex'));
  } catch (error) {
    console.log(error);
  }

  try {
    const key = Buffer.from('231f4c4d4d3051fdc2ec0a3851d5b383', 'hex');
    const COSEMessage = Buffer.from('d8608443a10101a2044a6f75722d736563726574054c291a40271067ff57b1623c30581f23b663aaf9dfb91c5a39a175118ad7d72d416385b1b610e28b3b3fd824a397818340a040', 'hex');
    const buf = await cose.encrypt.read(COSEMessage, key);
    console.log('Protected message: ' + buf.toString('utf8'));
  } catch (error) {
    console.log(error);
  }
}
run();
