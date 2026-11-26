import { EmptyState, PageHeader } from '../../features/portals/components/PortalUI';

export function SellerCustomers() {
  return <div>
    <PageHeader eyebrow="Customers" title="Your customers" description="Buyer information will be shown here when real orders are available from the backend." />
    <EmptyState title="Customer information is not connected" description="No customer profiles or sample personal details are stored in this demo." />
  </div>;
}
