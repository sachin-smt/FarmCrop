import { EmptyState, PageHeader } from '../../features/portals/components/PortalUI';

export function AdminPayments() {
  return <div>
    <PageHeader eyebrow="Marketplace operations" title="Payments" description="Payment records and settlements are not connected to this frontend demo." />
    <EmptyState title="No payment data" description="Payment processing is intentionally not simulated. Connect the approved backend payment service before displaying financial records." />
  </div>;
}
