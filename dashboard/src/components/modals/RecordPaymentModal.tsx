import React from 'react';
import { Button } from '@/components/ui/button';
import { Input, Label } from '@/components/ui/input';
import Modal from '@/components/Modal';
import type { Organization } from '@/types';

export interface RecordPaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  activeOrg: Organization | null;
  defaultVpa?: string;
  isSaving?: boolean;
  onSubmitPayment: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Record Counter Payment Modal with strict TypeScript types
export function RecordPaymentModal({
  isOpen,
  onClose,
  activeOrg,
  defaultVpa,
  isSaving = false,
  onSubmitPayment,
}: RecordPaymentModalProps) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={isSaving ? () => {} : onClose}
      title="Record Counter Payment"
      subtitle="Simulate verified incoming UPI collection">
      <form onSubmit={onSubmitPayment} className="space-y-4 text-xs">
        <div className="space-y-1.5">
          <Label>Amount (INR)</Label>
          <div className="relative">
            <span className="absolute inset-y-0 left-0 pl-3 flex items-center text-muted-foreground font-bold">₹</span>
            <Input
              type="number"
              step="0.01"
              name="amount"
              defaultValue="250.00"
              required
              disabled={isSaving}
              className="pl-8 text-base font-bold"
            />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-3">
          <div className="space-y-1.5">
            <Label>Customer Name</Label>
            <Input
              type="text"
              name="payerName"
              defaultValue="Karan Patel"
              disabled={isSaving}
            />
          </div>
          <div className="space-y-1.5">
            <Label>Customer UPI VPA</Label>
            <Input
              type="text"
              name="payerVpa"
              defaultValue="karan@okhdfcbank"
              disabled={isSaving}
              className="font-mono"
            />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label>Reference Note</Label>
          <Input
            type="text"
            name="note"
            defaultValue="Counter Bill #409"
            disabled={isSaving}
          />
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
            className="gap-2 min-w-[140px]">
            {isSaving ? (
              <>
                <div className="w-3.5 h-3.5 rounded-full border-2 border-white border-t-transparent animate-spin" />
                <span>Recording...</span>
              </>
            ) : (
              'Record Payment'
            )}
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default RecordPaymentModal;
