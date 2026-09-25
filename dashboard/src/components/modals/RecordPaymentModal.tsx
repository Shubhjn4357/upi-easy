import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import Modal from '@/components/Modal';
import { IconCreditCard } from '@/components/ui/icons';
import type { Organization } from '@/types';

export interface RecordPaymentModalProps {
  isOpen: boolean;
  onClose: () => void;
  activeOrg: Organization | null;
  defaultVpa?: string;
  isSaving?: boolean;
  onSubmitPayment: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

// Record Counter Payment Modal with smooth custom components
export function RecordPaymentModal({
  isOpen,
  onClose,
  activeOrg,
  defaultVpa,
  isSaving = false,
  onSubmitPayment,
}: RecordPaymentModalProps) {
  const [status, setStatus] = useState('SUCCESS');

  return (
    <Modal
      isOpen={isOpen}
      onClose={isSaving ? () => {} : onClose}
      title="Record Counter Payment"
      subtitle={`Simulate verified incoming UPI collection for ${activeOrg?.name || 'Organization'}${defaultVpa ? ` • ${defaultVpa}` : ''}`}
      icon={<IconCreditCard className="w-4 h-4" />}>
      <form onSubmit={onSubmitPayment} className="space-y-4 text-xs">
        <Input
          label="Amount (INR)"
          type="number"
          step="0.01"
          name="amount"
          defaultValue="250.00"
          required
          disabled={isSaving}
          leftIcon={<span className="font-bold text-sm text-foreground">₹</span>}
          className="text-base font-bold"
        />

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input
            label="Customer Name"
            type="text"
            name="payerName"
            defaultValue="Karan Patel"
            disabled={isSaving}
          />

          <Input
            label="Customer UPI VPA"
            type="text"
            name="payerVpa"
            defaultValue="karan@okhdfcbank"
            disabled={isSaving}
            className="font-mono"
          />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          <Input
            label="UTR / Reference (Optional)"
            type="text"
            name="referenceNumber"
            placeholder="Auto-generated if empty"
            disabled={isSaving}
            className="font-mono text-[11px]"
          />

          <Select
            label="Status"
            name="status"
            value={status}
            onChange={setStatus}
            disabled={isSaving}
            options={[
              {
                value: 'SUCCESS',
                label: 'SUCCESS (Received)',
                description: 'Payment verified and confirmed',
              },
              {
                value: 'PENDING',
                label: 'PENDING (Awaiting)',
                description: 'Awaiting customer transfer',
              },
              {
                value: 'FAILED',
                label: 'FAILED',
                description: 'Declined or timed out',
              },
            ]}
          />
        </div>

        <Input
          label="Reference Note"
          type="text"
          name="note"
          defaultValue="Counter Bill #409"
          disabled={isSaving}
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
            loadingText="Recording..."
            className="min-w-[140px]">
            Record Payment
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default RecordPaymentModal;
