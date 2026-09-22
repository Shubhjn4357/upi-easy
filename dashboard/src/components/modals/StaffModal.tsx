import React from 'react';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import Modal from '@/components/Modal';
import type { StaffMember } from '@/types';

export interface StaffModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialData?: StaffMember | null;
  onSubmitStaff: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Staff Invite and Role Update Modal with strict TypeScript types
export function StaffModal({
  isOpen,
  onClose,
  initialData = null,
  onSubmitStaff,
}: StaffModalProps) {
  const isEditing = Boolean(initialData);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? 'Edit Staff Member' : 'Add Staff Member'}
      subtitle={isEditing ? `Updating ${initialData?.fullName || initialData?.name || initialData?.mobileNumber}` : 'Assign mobile number and role access'}>
      <form onSubmit={onSubmitStaff} className="space-y-4 text-xs">
        {!isEditing ? (
          <>
            <div className="space-y-1.5">
              <Label>Full Name</Label>
              <Input
                type="text"
                name="name"
                placeholder="e.g. Sunil Verma"
                required
              />
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label>Mobile Number</Label>
                <Input
                  type="tel"
                  name="mobile"
                  placeholder="9876543210"
                  required
                  className="font-mono"
                />
              </div>
              <div className="space-y-1.5">
                <Label>Email (Optional)</Label>
                <Input
                  type="email"
                  name="email"
                  placeholder="staff@store.com"
                />
              </div>
            </div>
          </>
        ) : null}

        <div className="space-y-1.5">
          <Label>Assigned Role</Label>
          <select
            name="role"
            defaultValue={initialData?.role || 'CASHIER'}
            className="w-full h-9 bg-background border border-input rounded-xl px-3 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring">
            <option value="CASHIER" className="bg-card text-card-foreground">💳 CASHIER — Collect payments & show QR</option>
            <option value="MANAGER" className="bg-card text-card-foreground">💼 MANAGER — Manage staff & refunds</option>
            <option value="ACCOUNTANT" className="bg-card text-card-foreground">📊 ACCOUNTANT — View reports & ledger</option>
          </select>
        </div>

        {isEditing && (
          <div className="space-y-1.5">
            <Label>Access Status</Label>
            <select
              name="status"
              defaultValue={initialData?.status || 'ACTIVE'}
              className="w-full h-9 bg-background border border-input rounded-xl px-3 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring">
              <option value="ACTIVE" className="bg-card text-card-foreground">ACTIVE</option>
              <option value="SUSPENDED" className="bg-card text-card-foreground">SUSPENDED</option>
            </select>
          </div>
        )}

        <div className="pt-2 flex justify-end gap-2">
          <Button
            variant="outline"
            type="button"
            onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="brand"
            type="submit">
            {isEditing ? 'Update Staff' : 'Add Member'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default StaffModal;
