import type { ReactNode } from 'react';

export function PageHeader({ eyebrow, title, description, action }: {
  eyebrow?: string;
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return <div className="mb-6 flex flex-col gap-4 sm:mb-8 sm:flex-row sm:items-end sm:justify-between">
    <div>
      {eyebrow && <p className="text-xs font-bold uppercase tracking-[.15em] text-[#0b6b3a]">{eyebrow}</p>}
      <h1 className="serif mt-1 text-2xl font-bold text-[#122018] sm:text-3xl">{title}</h1>
      {description && <p className="mt-2 max-w-3xl text-sm leading-6 text-[#66736b]">{description}</p>}
    </div>
    {action && <div className="shrink-0">{action}</div>}
  </div>;
}

export function MetricCard({ label, value, note, icon }: {
  label: string;
  value: string | number;
  note?: string;
  icon?: ReactNode;
}) {
  return <article className="min-w-0 rounded-2xl border border-[#e5eae3] bg-white p-4 shadow-sm sm:p-5">
    <div className="flex items-start justify-between gap-3">
      <p className="text-xs font-semibold leading-5 text-[#66736b] sm:text-sm">{label}</p>
      {icon && <span className="grid size-9 shrink-0 place-items-center rounded-xl bg-[#e8f5ec] text-[#0b6b3a]">{icon}</span>}
    </div>
    <p className="mt-3 break-words text-2xl font-bold text-[#122018] sm:text-3xl">{value}</p>
    {note && <p className="mt-1 text-xs leading-5 text-[#87928a]">{note}</p>}
  </article>;
}

export function StatusBadge({ status }: { status: string }) {
  const tone = status === 'APPROVED' || status === 'ACTIVE' || status === 'DELIVERED'
    ? 'bg-[#e6f4ea] text-[#17643a]'
    : status === 'REJECTED' || status === 'SUSPENDED' || status === 'CANCELLED'
      ? 'bg-[#fff0ed] text-[#a94837]'
      : status === 'UNDER_REVIEW' || status === 'PROCESSING' || status === 'SHIPPED'
        ? 'bg-[#edf2ff] text-[#4056a1]'
        : status === 'RESUBMISSION_REQUIRED' || status === 'PAUSED'
          ? 'bg-[#fff5dd] text-[#8a5d00]'
          : 'bg-[#f0f2ed] text-[#53635a]';

  return <span className={`inline-flex max-w-full items-center rounded-full px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide ${tone}`}>
    {status.replaceAll('_', ' ')}
  </span>;
}

export function EmptyState({ title, description, action }: {
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return <div className="rounded-2xl border border-dashed border-[#cfdacf] bg-white px-5 py-10 text-center">
    <h2 className="text-base font-bold text-[#263a2d]">{title}</h2>
    <p className="mx-auto mt-2 max-w-lg text-sm leading-6 text-[#66736b]">{description}</p>
    {action && <div className="mt-5 flex justify-center">{action}</div>}
  </div>;
}

export function Panel({ title, action, children }: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
}) {
  return <section className="min-w-0 rounded-2xl border border-[#e5eae3] bg-white shadow-sm">
    <div className="flex items-center justify-between gap-3 border-b border-[#edf0e9] px-4 py-4 sm:px-5">
      <h2 className="text-sm font-bold text-[#263a2d] sm:text-base">{title}</h2>
      {action}
    </div>
    <div className="p-4 sm:p-5">{children}</div>
  </section>;
}

export function DataTable({ headers, children }: {
  headers: string[];
  children: ReactNode;
}) {
  return <div className="overflow-x-auto rounded-xl border border-[#edf0e9]">
    <table className="w-full min-w-[720px] border-collapse text-left text-sm">
      <thead className="bg-[#f7f9f5] text-[10px] font-bold uppercase tracking-[.1em] text-[#66736b]">
        <tr>{headers.map((header) => <th key={header} className="px-4 py-3">{header}</th>)}</tr>
      </thead>
      <tbody className="divide-y divide-[#edf0e9]">{children}</tbody>
    </table>
  </div>;
}

export function formatDate(value: string): string {
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(new Date(value));
}

export const inputClassName = 'w-full rounded-xl border border-[#dfe7df] bg-white px-3.5 py-3 text-sm text-[#122018] outline-none transition focus:border-[#0b6b3a] focus:ring-2 focus:ring-[#0b6b3a]/10';

export const primaryButtonClassName = 'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl bg-[#0b6b3a] px-4 py-2.5 text-sm font-bold text-white transition hover:bg-[#064e2a] disabled:cursor-not-allowed disabled:opacity-50';

export const secondaryButtonClassName = 'inline-flex min-h-11 items-center justify-center gap-2 rounded-xl border border-[#dfe7df] bg-white px-4 py-2.5 text-sm font-bold text-[#31533d] transition hover:bg-[#f5f8f4] disabled:cursor-not-allowed disabled:opacity-50';
