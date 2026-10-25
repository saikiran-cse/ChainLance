const path = require('path');
// Resolve ethers from frontend node_modules
const ethers = require(path.join(__dirname, '..', 'frontend', 'node_modules', 'ethers'));

const action = process.argv[2];

if (action === 'create') {
  const w = ethers.Wallet.createRandom();
  console.log(JSON.stringify({ address: w.address, privateKey: w.privateKey }));
} else if (action === 'sign') {
  const privKey = process.argv[3];
  const message = process.argv[4];
  const w = new ethers.Wallet(privKey);
  w.signMessage(message).then((signature) => {
    console.log(JSON.stringify({ signature }));
  }).catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
