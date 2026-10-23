const hre = require("hardhat");
const fs = require("fs");
const path = require("path");

async function main() {
  const [deployer] = await hre.ethers.getSigners();
  // On a local node we fall back to the deployer so the demo works without extra setup.
  const admin = process.env.ADMIN_ADDRESS || deployer.address;
  if (!hre.ethers.isAddress(admin)) throw new Error("ADMIN_ADDRESS is not a valid address");

  const network = await hre.ethers.provider.getNetwork();
  console.log(`Network: ${hre.network.name} (chainId ${network.chainId})`);
  console.log(`Deployer: ${deployer.address}`);
  console.log(`Admin:    ${admin}`);

  const factory = await hre.ethers.deployContract("ChainLanceFactory", [admin]);
  await factory.waitForDeployment();
  const address = await factory.getAddress();
  console.log(`ChainLanceFactory deployed to: ${address}`);

  const dir = path.join(__dirname, "..", "deployments");
  fs.mkdirSync(dir, { recursive: true });
  const out = { network: hre.network.name, chainId: Number(network.chainId), factory: address, admin, deployedAt: new Date().toISOString() };
  fs.writeFileSync(path.join(dir, `${hre.network.name}.json`), JSON.stringify(out, null, 2));
  console.log(`Saved deployments/${hre.network.name}.json`);
}

main().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
