import { EmptyState, PageHeader } from '../../features/portals/components/PortalUI';

export function ManageBuyers() {
  return <div>
    <PageHeader eyebrow="Marketplace accounts" title="Buyers" description="Buyer profiles and account status will be available when the Spring Boot user service is connected." />
    <EmptyState title="Buyer records are not connected" description="No buyer data is seeded or fabricated in this local demo. Buyer search and account management will be backed by the API in a later phase." />
  </div>;
}
