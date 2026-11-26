import { ChartNoAxesCombined } from 'lucide-react';
import { usePortal } from '../../features/portals/PortalContext';
import { EmptyState, PageHeader, Panel, MetricCard } from '../../features/portals/components/PortalUI';

export function AdminReports() {
  const { applications, crops } = usePortal();
  const approved = applications.filter((application) => application.status === 'APPROVED').length;
  const activeCrops = crops.filter((crop) => crop.availability === 'ACTIVE').length;
  return <div>
    <PageHeader eyebrow="Marketplace insights" title="Reports & Analytics" description="This view reports only data created during the local demo session." />
    <div className="mb-5 grid gap-3 sm:grid-cols-2">
      <MetricCard label="Applications recorded" value={applications.length} note="Local browser-session records" icon={<ChartNoAxesCombined size={18} />} />
      <MetricCard label="Approved sellers" value={approved} note="Current local application state" icon={<ChartNoAxesCombined size={18} />} />
      <MetricCard label="Crop listings" value={crops.length} note="Local demo listings" icon={<ChartNoAxesCombined size={18} />} />
      <MetricCard label="Active listings" value={activeCrops} note="Local demo listings" icon={<ChartNoAxesCombined size={18} />} />
    </div>
    <Panel title="Marketplace reporting">
      <EmptyState title="Financial and order reporting is not connected" description="No revenue, payment, buyer, or order statistics are fabricated. These reports will be populated by the backend when real data services are available." />
    </Panel>
  </div>;
}
