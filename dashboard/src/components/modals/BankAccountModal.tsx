import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import Modal from '@/components/Modal';
import { IconCreditCard } from '@/components/ui/icons';
import type { BankAccount, Organization } from '@/types';

export interface BankAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  activeOrg: Organization | null;
  accountToEdit?: BankAccount | null;
  isSaving?: boolean;
  onSubmitBank: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Link & Edit Settlement Bank Account Modal
export function BankAccountModal({
  isOpen,
  onClose,
  activeOrg,
  accountToEdit,
  isSaving = false,
  onSubmitBank,
}: BankAccountModalProps) {
  const isEditing = Boolean(accountToEdit);
  const [accountType, setAccountType] = useState(accountToEdit?.accountType || 'CURRENT');

  return (
    <Modal
      isOpen={isOpen}
      onClose={isSaving ? () => {} : onClose}
      title={isEditing ? 'Edit Bank Account' : 'Link Bank Account'}
      subtitle={isEditing ? 'Update settlement destination details' : 'Connect settlement bank destination'}
      icon={<IconCreditCard className="w-4 h-4" />}>
      <form onSubmit={onSubmitBank} className="space-y-4 text-xs">
        <Input
          label="Bank Name"
          type="text"
          name="bankName"
          defaultValue={accountToEdit?.bankName || ''}
          placeholder="e.g. HDFC Bank, State Bank of India"
          required
          disabled={isSaving}
        />

        <Input
          label="Account Holder Name"
          type="text"
          name="holderName"
          defaultValue={accountToEdit?.accountHolderName || activeOrg?.name || ''}
          required
          disabled={isSaving}
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input
            label="Account Number"
            type="text"
            name="accountNumber"
            placeholder={isEditing ? `Keep: ${accountToEdit?.accountNumberMasked || '••••'}` : '1234567890'}
            required={!isEditing}
            disabled={isSaving}
            className="font-mono"
          />

          <Input
            label="IFSC Code"
            type="text"
            name="ifsc"
            defaultValue={accountToEdit?.ifscCode || ''}
            placeholder="HDFC0001234"
            required
            disabled={isSaving}
            className="font-mono uppercase"
          />
        </div>

        <Select
          label="Account Type"
          name="type"
          value={accountType}
          onChange={setAccountType}
          disabled={isSaving}
          options={[
            { value: 'CURRENT', label: 'CURRENT Account', description: 'Standard business operating account' },
            { value: 'SAVINGS', label: 'SAVINGS Account', description: 'Individual / proprietor account' },
            { value: 'OVERDRAFT', label: 'OVERDRAFT (OD)', description: 'Credit-linked business account' },
          ]}
        />

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
            loadingText={isEditing ? 'Updating...' : 'Linking...'}
            className="min-w-[120px]">
            {isEditing ? 'Save Changes' : 'Link Account'}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default BankAccountModal;
