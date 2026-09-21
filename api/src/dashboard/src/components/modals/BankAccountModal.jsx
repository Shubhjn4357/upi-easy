// Link Bank Account Modal
import React from 'react';
import { Modal } from '../Modal.jsx';
import { Button } from '../ui/button.jsx';
import { Input, Label } from '../ui/input.jsx';

export function BankAccountModal({
  isOpen,
  onClose,
  activeOrg,
  onSubmitBank,
}) {
  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
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
          />
        </div>
        <div className="space-y-1.5">
          <Label>Account Holder Name</Label>
          <Input
            type="text"
            name="holderName"
            defaultValue={activeOrg?.name || ""}
            required
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
              className="font-mono uppercase"
            />
          </div>
        </div>
        <div className="space-y-1.5">
          <Label>Account Type</Label>
          <select
            name="type"
            defaultValue="CURRENT"
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
            onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="brand"
            type="submit">
            Link Account
          </Button>
        </div>
      </form>
    </Modal>
  );
}

export default BankAccountModal;
