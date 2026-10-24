/**
 * Single place that reads Vite environment variables.
 * Everything here is PUBLIC (it ends up in the browser bundle): never add secrets.
 */
const raw = import.meta.env;

const chainId = Number(raw.VITE_EXPECTED_CHAIN_ID ?? '11155111');

export const env = {
  /** Spring Boot base URL without trailing slash. Service paths already start with /api. */
  apiBaseUrl: (raw.VITE_API_BASE_URL ?? 'http://localhost:8080').replace(/\/+$/, ''),
  /** When true, marketplace services use labelled sample data (see src/mocks). Auth, profile and wallet stay real. */
  useMocks: String(raw.VITE_USE_MOCKS ?? 'false').toLowerCase() === 'true',
  /** Chain MetaMask is expected to be on. 11155111 = Ethereum Sepolia. */
  expectedChainId: Number.isFinite(chainId) && chainId > 0 ? chainId : 11155111,
} as const;
