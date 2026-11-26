import { useState } from 'react';
import { EmptyState, PageHeader, inputClassName } from '../../features/portals/components/PortalUI';
import type { MarketplaceOrderStatus } from '../../features/portals/types';

const statuses: MarketplaceOrderStatus[] = ['PLACED', 'ACCEPTED', 'REJECTED', 'PROCESSING', 'READY_FOR_DISPATCH', 'SHIPPED', 'DELIVERED', 'CANCELLED'];

export function SellerOrders() {
  const [status, setStatus] = useState<'ALL' | MarketplaceOrderStatus>('ALL');
  const [query, setQuery] = useState('');
  return <div>
    <PageHeader eyebrow="Orders" title="Orders" description="When the backend is connected, you can review buyers, crops, and delivery progress here." />
    <div className="mb-4 grid gap-3 sm:grid-cols-2">
      <label className="sr-only" htmlFor="seller-order-search">Search order ID</label>
      <input id="seller-order-search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search by order ID" className={inputClassName} />
      <label className="sr-only" htmlFor="seller-order-status">Filter order status</label>
      <select id="seller-order-status" value={status} onChange={(event) => setStatus(event.target.value as 'ALL' | MarketplaceOrderStatus)} className={inputClassName}><option value="ALL">All order statuses</option>{statuses.map((item) => <option key={item} value={item}>{item.replaceAll('_', ' ')}</option>)}</select>
    </div>
    <EmptyState title="No orders yet" description={`Order records are not connected${query || status !== 'ALL' ? ' for the selected filter' : ''}. Accepting orders and changing fulfillment status will be enabled with the backend.`} />
    <p className="mt-5 text-xs leading-5 text-[#87928a]">Supported order lifecycle when connected: {statuses.join(' → ')}. No payment processing is simulated.</p>
  </div>;
}
