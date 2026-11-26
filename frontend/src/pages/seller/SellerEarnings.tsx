import { Banknote, ClipboardCheck, Wallet } from 'lucide-react';
import { MetricCard, EmptyState, PageHeader, Panel } from '../../features/portals/components/PortalUI';

export function SellerEarnings() {
  return <div>
    <PageHeader eyebrow="Sales" title="Earnings" description="Financial records will be available after order and payment APIs are connected." />
    <div className="grid gap-3 sm:grid-cols-3">
      <MetricCard label="Total sales" value="Not connected" note="No financial records" icon={<Banknote size={18} />} />
      <MetricCard label="Completed orders" value="Not connected" note="Order service unavailable" icon={<ClipboardCheck size={18} />} />
      <MetricCard label="Pending payments" value="Not connected" note="Payment service unavailable" icon={<Wallet size={18} />} />
    </div>
    <Panel title="Earnings history">
      <EmptyState title="No earnings history" description="No values are fabricated or estimated. Payment processing is not implemented in the frontend demo." />
    </Panel>
  </div>;
}
