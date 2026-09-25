import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import Modal from '@/components/Modal';
import { IconZap } from '@/components/ui/icons';
import type { UpiAccount, Organization } from '@/types';

export interface UpiAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  initialData?: UpiAccount | null;
  activeOrg: Organization | null;
  isSaving?: boolean;
  onSubmitUpi: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Add / Edit UPI Account Modal with smooth custom inputs & switch
export function UpiAccountModal({
  isOpen,
  onClose,
  initialData = null,
  activeOrg,
  isSaving = false,
  onSubmitUpi,
}: UpiAccountModalProps) {
  const isEditing = Boolean(initialData);
  const [isDefault, setIsDefault] = useState<boolean>(
    isEditing ? Boolean(initialData?.isDefault || initialData?.isPrimary) : true
  );

  return (
    <Modal
      isOpen={isOpen}
      onClose={isSaving ? () => {} : onClose}
      title={isEditing ? 'Edit UPI Account' : 'Add UPI Account'}
      subtitle={isEditing ? `Updating ${initialData?.vpa || initialData?.upiId}` : 'Connect a new VPA to this merchant organization'}
      icon={<IconZap className="w-4 h-4" />}>
      <form onSubmit={onSubmitUpi} className="space-y-4 text-xs">
        <Input
          label="UPI VPA"
          type="text"
          name="vpa"
          defaultValue={initialData?.vpa || initialData?.upiId || ''}
          placeholder="e.g. storename@okhdfcbank"
          required
          disabled={isSaving}
          className="font-mono"
        />

        <Input
          label="Payee Name"
          type="text"
          name="payeeName"
          defaultValue={initialData?.payeeName || initialData?.accountHolderName || activeOrg?.name || ''}
          required
          disabled={isSaving}
        />

        <Input
          label="Merchant Category Code (MCC)"
          type="text"
          name="mcc"
          defaultValue={initialData?.merchantCategoryCode || '5411'}
          disabled={isSaving}
          className="font-mono"
          helperText="Standard retail grocery code is 5411"
        />

        {/* Hidden input for form submission */}
        <input type="hidden" name="isDefault" value={isDefault ? 'on' : 'off'} />

        <div className="p-3 rounded-2xl bg-muted/20 border border-border/70">
          <Switch
            checked={isDefault}
            onCheckedChange={setIsDefault}
            disabled={isSaving}
            label={isEditing ? 'Set as Default UPI' : 'Set as Primary Default'}
            description="Default account used for counter QR code generation and quick collection"
          />
        </div>

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
            {isEditing ? 'Update Account' : 'Save Account'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default UpiAccountModal;
