import React from 'react';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import Modal from '@/components/Modal';
import type { Organization } from '@/types';

export interface BankAccountModalProps {
  isOpen: boolean;
  onClose: () => void;
  activeOrg: Organization | null;
  isSaving?: boolean;
  onSubmitBank: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Link Bank Account Modal with strict TypeScript types
export function BankAccountModal({
  isOpen,
  onClose,
  activeOrg,
  isSaving = false,
  onSubmitBank,
}: BankAccountModalProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={isSaving ? () => {} : onClose}
      title="Link Bank Account"
      subtitle="Connect settlement bank destination">
      <form onSubmit={onSubmitBank} className="space-y-4 text-xs">
        <div className="space-y-1.5">
          <Label>Bank Name</Label>
          <Input
            type="text"
            name="bankName"
            placeholder="e.g. HDFC Bank"
            required
            disabled={isSaving}
          />
        </div>
        <div className="space-y-1.5">
          <Label>Account Holder Name</Label>
          <Input
            type="text"
            name="holderName"
            defaultValue={activeOrg?.name || ''}
            required
            disabled={isSaving}
          />
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label>Account Number</Label>
            <Input
              type="text"
              name="accountNumber"
              placeholder="1234567890"
              required
              disabled={isSaving}
              className="font-mono"
            />
          </div>
          <div className="space-y-1.5">
            <Label>IFSC Code</Label>
            <Input
              type="text"
              name="ifsc"
              placeholder="HDFC0001234"
              required
              disabled={isSaving}
              className="font-mono uppercase"
            />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label>Account Type</Label>
          <select
            name="type"
            defaultValue="CURRENT"
            disabled={isSaving}
            className="w-full h-9 bg-background border border-input rounded-xl px-3 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring">
            <option value="CURRENT" className="bg-card text-card-foreground">CURRENT</option>
            <option value="SAVINGS" className="bg-card text-card-foreground">SAVINGS</option>
            <option value="OVERDRAFT" className="bg-card text-card-foreground">OVERDRAFT</option>
          </select>
        </div>
        <div className="pt-2 flex justify-end gap-2">
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
            disabled={isSaving}
            className="gap-2 min-w-[120px]">
            {isSaving ? (
              <>
                <div className="w-3.5 h-3.5 rounded-full border-2 border-white border-t-transparent animate-spin" />
                <span>Linking...</span>
              </>
            ) : (
              'Link Account'
            )}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default BankAccountModal;
