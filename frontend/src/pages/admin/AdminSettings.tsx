import { useState } from 'react';
import { PageHeader, Panel, secondaryButtonClassName } from '../../features/portals/components/PortalUI';

export function AdminSettings() {
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [reviewNotifications, setReviewNotifications] = useState(true);
  const preferences = [
    { label: 'Application review updates', checked: reviewNotifications, onChange: setReviewNotifications },
    { label: 'Email notifications', checked: emailNotifications, onChange: setEmailNotifications },
  ];

  return <div>
    <PageHeader eyebrow="Preferences" title="Admin Settings" description="Manage local notification preferences for the Admin workspace." />
    <div className="grid gap-4 lg:grid-cols-2">
      <Panel title="Access and security">
        <p className="text-sm leading-6 text-[#53635a]">Admin pages are available without sign-in during frontend development. Authentication and authorization will be integrated with the backend later.</p>
      </Panel>
      <Panel title="Notification preferences">
        <div className="space-y-4">
          {preferences.map(({ label, checked, onChange }) => <label key={label} className="flex min-h-12 items-center justify-between gap-4 text-sm">
            <span className="font-medium text-[#263a2d]">{label}</span>
            <input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} className="size-5 accent-[#0b6b3a]" />
          </label>)}
          <p className="text-xs text-[#87928a]">Preferences apply to this page session only. No email is sent.</p>
        </div>
      </Panel>
      <Panel title="Security information">
        <p className="text-sm leading-6 text-[#53635a]">This frontend-only access mode does not protect data. Production security must be enforced by the Spring Boot API.</p>
        <button type="button" disabled className={`${secondaryButtonClassName} mt-5`}>Authentication setup pending</button>
      </Panel>
    </div>
  </div>;
}
