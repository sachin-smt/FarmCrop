import { useState } from 'react';
import { EmptyState, PageHeader, inputClassName } from '../../features/portals/components/PortalUI';
import type { MarketplaceOrderStatus } from '../../features/portals/types';

const orderStatuses: MarketplaceOrderStatus[] = ['PLACED', 'ACCEPTED', 'REJECTED', 'PROCESSING', 'READY_FOR_DISPATCH', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export function ManageOrders() {
  const [status, setStatus] = useState<'ALL' | MarketplaceOrderStatus>('ALL');
  const [query, setQuery] = useState('');
  return <div>
    <PageHeader eyebrow="Marketplace operations" title="Orders" description="Order records, buyer/seller details, and fulfillment status will be supplied by the backend." />
    <div className="mb-4 grid gap-3 sm:grid-cols-2">
      <label className="sr-only" htmlFor="order-search">Search order ID</label>
      <input id="order-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search by order ID" className={inputClassName} />
      <label className="sr-only" htmlFor="order-status">Filter order status</label>
      <select id="order-status" value={status} onChange={(event) => setStatus(event.target.value as 'ALL' | MarketplaceOrderStatus)} className={inputClassName}><option value="ALL">All order statuses</option>{orderStatuses.map((item) => <option key={item} value={item}>{item.replaceAll('_', ' ')}</option>)}</select>
    </div>
    <EmptyState title="Order data is not connected" description={`No demo orders are being shown${query || status !== 'ALL' ? ' for the current filter' : ''}. No purchase or payment actions are simulated here.`} />
  </div>;
}
