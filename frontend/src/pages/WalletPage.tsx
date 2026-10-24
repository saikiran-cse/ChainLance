import { WalletPanel } from '../components/wallet/WalletPanel';
import { PageHeader } from '../components/ui/Panel';
import { useDocumentTitle } from '../hooks/useDocumentTitle';

export default function WalletPage() {
  useDocumentTitle('Wallet');
  return (
    <div className="flex flex-col gap-8">
      <PageHeader title="Wallet">
        Connect MetaMask and prove you control your address. This is how your account will be tied to escrow contracts.
      </PageHeader>
      <WalletPanel />
    </div>
  );
}
