// Store QR Code Generator Modal
import React, { useState } from 'react';
import { Modal } from '../Modal.jsx';
import { Button } from '../ui/button.jsx';
import { Input, Label } from '../ui/input.jsx';
import { IconCopy, IconExternalLink } from '../ui/icons.jsx';
import { sanitizeUrl } from '../../lib/auth.js';

export function QrCodeModal({
  upiAccount,
  onClose,
  onCopyLink,
}) {
  const [customAmount, setCustomAmount] = useState("");
  if (!upiAccount) return null;

  const amt = parseFloat(customAmount);
  const upiUri = `upi://pay?pa=${encodeURIComponent(upiAccount.vpa)}&pn=${encodeURIComponent(upiAccount.payeeName)}&cu=INR${!isNaN(amt) && amt > 0 ? `&am=${amt.toFixed(2)}` : ''}`;
  const safeUpiUri = sanitizeUrl(upiUri);
  const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${encodeURIComponent(safeUpiUri)}`;

  return (
    <Modal
      isOpen={Boolean(upiAccount)}
      onClose={onClose}
      title="Store QR Code"
      subtitle={`Pay to ${upiAccount.vpa}`}>
      <div className="space-y-4 text-center">
        <div className="bg-white p-4 rounded-2xl w-60 h-60 mx-auto shadow-xl flex items-center justify-center border">
          <img src={qrUrl} alt="UPI QR" className="w-full h-full object-contain" />
        </div>

        <div className="text-left bg-muted/40 p-3 rounded-xl border border-border space-y-2">
          <Label>Custom Amount (Optional)</Label>
          <Input
            type="number"
            placeholder="Enter amount (₹)"
            value={customAmount}
            onChange={(e) => setCustomAmount(e.target.value)}
            className="font-bold"
          />
        </div>

        <div className="flex gap-2 justify-center">
          <Button
            variant="outline"
            onClick={() => onCopyLink(safeUpiUri)}
            className="gap-1.5">
            <IconCopy className="w-3.5 h-3.5" />
            <span>Copy Link</span>
          </Button>
          <a
            href={safeUpiUri}
            rel="noopener noreferrer"
            className="inline-flex items-center justify-center rounded-xl px-4 py-2 text-xs font-bold bg-brand-600 hover:bg-brand-500 text-white shadow-md transition gap-1.5">
            <IconExternalLink className="w-3.5 h-3.5" />
            <span>Open UPI App</span>
          </a>
        </div>
      </div>
    </Modal>
  );
}

export default QrCodeModal;
