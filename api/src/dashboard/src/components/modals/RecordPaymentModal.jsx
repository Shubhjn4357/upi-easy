// Record Counter Payment Modal
import React from 'react';
import { Modal } from '../Modal.jsx';
import { Button } from '../ui/button.jsx';
import { Input, Label } from '../ui/input.jsx';

export function RecordPaymentModal({
  isOpen,
  onClose,
  activeOrg,
  defaultVpa,
  onSubmitPayment,
}) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
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
            />
          </div>
          <div className="space-y-1.5">
            <Label>Customer UPI VPA</Label>
            <Input
              type="text"
              name="payerVpa"
              defaultValue="karan@okhdfcbank"
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
          />
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
            Record Payment
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default RecordPaymentModal;
