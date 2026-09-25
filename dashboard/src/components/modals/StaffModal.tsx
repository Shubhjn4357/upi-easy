import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import Modal from '@/components/Modal';
import type { StaffMember } from '@/types';

interface ModulePermissionOption {
  id: string;
  name: string;
  description: string;
  icon: string;
}

const MODULE_OPTIONS: ModulePermissionOption[] = [
  {
    id: 'transactions',
    name: 'Transactions & Ledger',
    description: 'Record & view payments, filter history and export ledger CSV',
    icon: '💳',
  },
  {
    id: 'upi',
    name: 'UPI Accounts & QR Codes',
    description: 'Add VPAs, generate store counter QR codes and show payment links',
    icon: '⚡',
  },
  {
    id: 'accounts',
    name: 'Settlement Bank Accounts',
    description: 'View and link settlement destination bank accounts',
    icon: '🏦',
  },
  {
    id: 'staff',
    name: 'Staff & Team Management',
    description: 'Invite members, manage store cashiers and adjust role permissions',
    icon: '👥',
  },
  {
    id: 'reports',
    name: 'Overview & Reports',
    description: 'View revenue dashboard, analytics and performance statistics',
    icon: '📊',
  },
];

const ROLE_DEFAULT_MODULES: Record<string, string[]> = {
  OWNER: ['transactions', 'upi', 'accounts', 'staff', 'reports'],
  MANAGER: ['transactions', 'upi', 'accounts', 'staff', 'reports'],
  CASHIER: ['transactions', 'upi'],
  ACCOUNTANT: ['transactions', 'accounts', 'reports'],
};

export interface StaffModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialData?: StaffMember | null;
  isSaving?: boolean;
  onSubmitStaff: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Staff Invite and Role Update Modal with Role & Module Based Selection
export function StaffModal({
  isOpen,
  onClose,
  initialData = null,
  isSaving = false,
  onSubmitStaff,
}: StaffModalProps) {
  const isEditing = Boolean(initialData);

  const [selectedRole, setSelectedRole] = useState<string>(
    initialData?.role || 'CASHIER'
  );
  const [selectedStatus, setSelectedStatus] = useState<string>(
    initialData?.status || 'ACTIVE'
  );

  const [selectedModules, setSelectedModules] = useState<string[]>(() => {
    const role = initialData?.role || 'CASHIER';
    return ROLE_DEFAULT_MODULES[role] || ['transactions', 'upi'];
  });

  const [prevIsOpen, setPrevIsOpen] = useState(isOpen);
  const [prevInitialData, setPrevInitialData] = useState(initialData);

  if (isOpen !== prevIsOpen || initialData !== prevInitialData) {
    setPrevIsOpen(isOpen);
    setPrevInitialData(initialData);
    if (initialData?.role) {
      setSelectedRole(initialData.role);
      setSelectedModules(ROLE_DEFAULT_MODULES[initialData.role] || ['transactions', 'upi']);
      setSelectedStatus(initialData.status || 'ACTIVE');
    } else {
      setSelectedRole('CASHIER');
      setSelectedModules(ROLE_DEFAULT_MODULES['CASHIER']);
      setSelectedStatus('ACTIVE');
    }
  }

  const handleRoleSelect = (newRole: string) => {
    setSelectedRole(newRole);
    if (ROLE_DEFAULT_MODULES[newRole]) {
      setSelectedModules(ROLE_DEFAULT_MODULES[newRole]);
    }
  };

  const handleToggleModule = (moduleId: string) => {
    setSelectedModules((prev) =>
      prev.includes(moduleId) ? prev.filter((id) => id !== moduleId) : [...prev, moduleId]
    );
  };

  return (
    <Modal
      isOpen={isOpen}
      onClose={isSaving ? () => {} : onClose}
      title={isEditing ? 'Edit Staff Member' : 'Add Team Member'}
      subtitle={
        isEditing
          ? `Updating permissions for ${initialData?.fullName || initialData?.name || initialData?.mobileNumber}`
          : 'Assign role access & authorized modules for this business'
      }>
      <form onSubmit={onSubmitStaff} className="space-y-4 text-xs">
        {!isEditing ? (
          <>
            <Input
              label="Email Address"
              type="email"
              name="email"
              placeholder="colleague@example.com"
              required
              disabled={isSaving}
            />
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <Input
                label="Full Name"
                type="text"
                name="name"
                placeholder="e.g. Sunil Verma"
                disabled={isSaving}
              />
              <Input
                label="Mobile Number (Optional)"
                type="tel"
                name="mobile"
                placeholder="9876543210"
                disabled={isSaving}
                className="font-mono"
              />
            </div>
          </>
        ) : null}

        {/* Custom Role Preset Selector */}
        <Select
          label="Assigned Role Preset"
          name="role"
          value={selectedRole}
          onChange={handleRoleSelect}
          disabled={isSaving}
          options={[
            {
              value: 'CASHIER',
              label: 'CASHIER',
              description: 'Collect payments, static/dynamic QR & transactions',
            },
            {
              value: 'MANAGER',
              label: 'MANAGER',
              description: 'Manage operations, staff, UPI IDs & bank accounts',
            },
            {
              value: 'ACCOUNTANT',
              label: 'ACCOUNTANT',
              description: 'Financial reconciliation, ledger export & reports',
            },
          ]}
        />

        {/* Module Permissions Picker */}
        <div className="space-y-2 pt-1">
          <div className="flex items-center justify-between">
            <Label className="text-muted-foreground font-semibold">Authorized Modules & Permissions</Label>
            <span className="text-[10px] text-brand-600 dark:text-cyan-400 font-medium">
              {selectedModules.length} of {MODULE_OPTIONS.length} selected
            </span>
          </div>

          <div className="space-y-2 border border-border/80 rounded-2xl p-3 bg-muted/20">
            {MODULE_OPTIONS.map((mod) => {
              const isChecked = selectedModules.includes(mod.id);
              return (
                <label
                  key={mod.id}
                  className={`flex items-start gap-2.5 p-2 rounded-xl border transition-all duration-150 cursor-pointer select-none ${
                    isChecked
                      ? 'bg-brand-500/10 border-brand-500/30 text-foreground shadow-sm'
                      : 'bg-background/60 border-border/60 text-muted-foreground hover:bg-muted/40'
                  }`}>
                  <input
                    type="checkbox"
                    name="modules"
                    value={mod.id}
                    checked={isChecked}
                    disabled={isSaving}
                    onChange={() => handleToggleModule(mod.id)}
                    className="mt-0.5 rounded text-brand-600 focus:ring-brand-500"
                  />
                  <div className="flex-1 min-w-0">
                    <div className="font-semibold text-xs flex items-center gap-1.5">
                      <span>{mod.icon}</span>
                      <span>{mod.name}</span>
                    </div>
                    <div className="text-[11px] text-muted-foreground mt-0.5 leading-snug">
                      {mod.description}
                    </div>
                  </div>
                </label>
              );
            })}
          </div>
        </div>

        {isEditing && (
          <Select
            label="Access Status"
            name="status"
            value={selectedStatus}
            onChange={setSelectedStatus}
            disabled={isSaving}
            options={[
              { value: 'ACTIVE', label: 'ACTIVE', description: 'Active and authorized for business operations' },
              { value: 'SUSPENDED', label: 'SUSPENDED', description: 'Temporarily disabled account access' },
            ]}
          />
        )}

        {/* Action Buttons with Loading Spinner */}
        <div className="pt-3 flex items-center justify-end gap-2.5 border-t border-border/50">
          <Button
            variant="outline"
            type="button"
            disabled={isSaving}
            onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="brand"
            type="submit"
            loading={isSaving}
            loadingText="Saving..."
            className="min-w-[120px]">
            {isEditing ? 'Update Staff' : 'Add Member'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default StaffModal;
