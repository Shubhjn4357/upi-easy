import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { IconCopy, IconExternalLink, IconQrCode } from '@/components/ui/icons';
import Modal from '@/components/Modal';
import { sanitizeUrl } from '@/lib/auth';
import type { UpiAccount } from '@/types';

export interface QrCodeModalProps {
  upiAccount: UpiAccount | null;
  onClose: () => void;
  onCopyLink: (link: string) => void;
}

// Store QR Code Generator Modal with strict TypeScript types
export function QrCodeModal({
  upiAccount,
  onClose,
  onCopyLink,
}: QrCodeModalProps) {
  const [customAmount, setCustomAmount] = useState<string>('');
  if (!upiAccount) return null;

  const vpa = upiAccount.vpa || upiAccount.upiId || '';
  const payee = upiAccount.payeeName || upiAccount.accountHolderName || 'Merchant';
  const amt = parseFloat(customAmount);
  const upiUri = `upi://pay?pa=${encodeURIComponent(vpa)}&pn=${encodeURIComponent(payee)}&cu=INR${!isNaN(amt) && amt > 0 ? `&am=${amt.toFixed(2)}` : ''}`;
  const safeUpiUri = sanitizeUrl(upiUri);
  const qrUrl = `https://api.qrserver.com/v1/create-qr-code/?size=250x250&data=${encodeURIComponent(safeUpiUri)}`;

  return (
    <Modal
      isOpen={Boolean(upiAccount)}
      onClose={onClose}
      title="Store Counter QR Code"
      subtitle={`Instant UPI collection for ${vpa}`}
      icon={<IconQrCode className="w-4 h-4" />}>
      <div className="space-y-4 text-center">
        {/* QR container */}
        <div className="bg-white p-4 rounded-3xl w-60 h-60 mx-auto shadow-2xl flex items-center justify-center border border-border/60 transition-transform hover:scale-[1.02] duration-200">
          <img src={qrUrl} alt="UPI QR" className="w-full h-full object-contain" />
        </div>

        {/* Dynamic Amount input */}
        <div className="text-left bg-muted/20 p-3.5 rounded-2xl border border-border/70 space-y-2">
          <Input
            label="Specific Request Amount (Optional)"
            type="number"
            step="0.01"
            placeholder="e.g. 500"
            value={customAmount}
            onChange={(e) => setCustomAmount(e.target.value)}
            leftIcon={<span className="font-bold text-foreground">₹</span>}
            className="font-bold text-sm"
            helperText="Leave empty for customer-entered open amount"
          />
        </div>

        {/* Action buttons */}
        <div className="flex flex-col sm:flex-row gap-2.5 justify-center pt-2">
          <Button
            variant="outline"
            onClick={() => onCopyLink(safeUpiUri)}
            leftIcon={<IconCopy className="w-3.5 h-3.5" />}
            className="w-full sm:w-auto">
            Copy UPI Link
          </Button>
          <a
            href={safeUpiUri}
            rel="noopener noreferrer"
            className="w-full sm:w-auto">
            <Button
              variant="brand"
              leftIcon={<IconExternalLink className="w-3.5 h-3.5" />}
              className="w-full">
              Open UPI App
            </Button>
          </a>
        </div>
      </div>
    </Modal>
  );
}

export default QrCodeModal;
