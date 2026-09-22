import React from 'react';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import Modal from '@/components/Modal';
import type { UpiAccount, Organization } from '@/types';

export interface UpiAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialData?: UpiAccount | null;
  activeOrg: Organization | null;
  onSubmitUpi: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Add / Edit UPI Account Modal with strict TypeScript types
export function UpiAccountModal({
  isOpen,
  onClose,
  initialData = null,
  activeOrg,
  onSubmitUpi,
}: UpiAccountModalProps) {
  const isEditing = Boolean(initialData);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      title={isEditing ? 'Edit UPI Account' : 'Add UPI Account'}
      subtitle={isEditing ? `Updating ${initialData?.vpa || initialData?.upiId}` : 'Connect a new VPA to this merchant organization'}>
      <form onSubmit={onSubmitUpi} className="space-y-4 text-xs">
        <div className="space-y-1.5">
          <Label>UPI VPA</Label>
          <Input
            type="text"
            name="vpa"
            defaultValue={initialData?.vpa || initialData?.upiId || ''}
            placeholder="e.g. storename@okhdfcbank"
            required
            className="font-mono"
          />
        </div>
        <div className="space-y-1.5">
          <Label>Payee Name</Label>
          <Input
            type="text"
            name="payeeName"
            defaultValue={initialData?.payeeName || initialData?.accountHolderName || activeOrg?.name || ''}
            required
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label>MCC Code</Label>
            <Input
              type="text"
              name="mcc"
              defaultValue={initialData?.merchantCategoryCode || '5411'}
              className="font-mono"
            />
          </div>
          <div className="flex items-center pt-5">
            <label className="flex items-center gap-2 text-foreground font-semibold cursor-pointer">
              <input
                type="checkbox"
                name="isDefault"
                defaultChecked={isEditing ? initialData?.isDefault || initialData?.isPrimary : true}
                className="rounded text-primary"
              />
              <span>{isEditing ? 'Set as Default' : 'Primary Default'}</span>
            </label>
          </div>
        </div>
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
            {isEditing ? 'Update Account' : 'Save Account'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default UpiAccountModal;
