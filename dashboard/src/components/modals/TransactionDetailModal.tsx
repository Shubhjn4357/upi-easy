import React, { useState } from 'react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { IconCheck, IconCopy, IconReceipt } from '@/components/ui/icons';
import Modal from '@/components/Modal';
import type { Transaction } from '@/types';

export interface TransactionDetailModalProps {
  transaction: Transaction | null;
  onClose: () => void;
}

// Transaction Audit & Detail Modal with smooth custom components
export function TransactionDetailModal({
  transaction,
  onClose,
}: TransactionDetailModalProps) {
  const [copied, setCopied] = useState(false);
  if (!transaction) return null;

  const isSuccess = transaction.status === 'SUCCESS' || transaction.status === 'COMPLETED';

  const copyJson = () => {
    navigator.clipboard.writeText(JSON.stringify(transaction, null, 2));
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <Modal
      isOpen={Boolean(transaction)}
      onClose={onClose}
      title="Transaction Audit Record"
      subtitle={transaction.referenceNumber || transaction.id}
      icon={<IconReceipt className="w-4 h-4" />}>
      <div className="space-y-4 text-xs">
        {/* Summary card */}
        <div className="grid grid-cols-2 gap-3.5 p-4 rounded-2xl bg-muted/20 border border-border/70">
          <div>
            <span className="text-[11px] text-muted-foreground block font-medium">Amount</span>
            <span className="text-2xl font-black text-foreground tracking-tight">
              ₹{Number(transaction.amount).toFixed(2)}
            </span>
          </div>
          <div>
            <span className="text-[11px] text-muted-foreground block font-medium">Status</span>
            <div className="mt-1">
              <Badge
                variant={isSuccess ? 'success' : 'warning'}
                dot
                pulse={isSuccess}
                size="default">
                {transaction.status}
              </Badge>
            </div>
          </div>
          <div>
            <span className="text-[11px] text-muted-foreground block font-medium">Customer</span>
            <span className="font-semibold text-foreground text-xs">
              {transaction.payerName || 'Unknown'}
            </span>
          </div>
          <div>
            <span className="text-[11px] text-muted-foreground block font-medium">Customer VPA</span>
            <span className="font-mono text-foreground text-[11px]">
              {transaction.payerVpa || '—'}
            </span>
          </div>
          <div>
            <span className="text-[11px] text-muted-foreground block font-medium">Payee VPA</span>
            <span className="font-mono text-brand-600 dark:text-cyan-400 text-[11px] font-semibold">
              {transaction.payeeVpa}
            </span>
          </div>
          <div>
            <span className="text-[11px] text-muted-foreground block font-medium">Method & Mode</span>
            <span className="text-foreground text-xs font-medium">
              {transaction.paymentMode || 'UPI'} · {transaction.source || 'APP'}
            </span>
          </div>
        </div>

        {/* JSON payload inspector */}
        <div className="space-y-1.5">
          <div className="flex items-center justify-between">
            <h4 className="font-semibold text-foreground text-xs">Diagnostic Payload</h4>
            <Button
              variant="ghost"
              size="xs"
              onClick={copyJson}
              leftIcon={copied ? <IconCheck className="w-3 h-3 text-emerald-500" /> : <IconCopy className="w-3 h-3" />}>
              {copied ? 'Copied' : 'Copy JSON'}
            </Button>
          </div>
          <pre className="p-3.5 rounded-2xl bg-muted/40 border border-border/80 text-[11px] font-mono text-foreground overflow-x-auto max-h-48 leading-relaxed">
            {JSON.stringify(transaction, null, 2)}
          </pre>
        </div>

        <div className="pt-2 flex justify-end border-t border-border/50">
          <Button variant="outline" onClick={onClose}>
            Done
          </Button>
        </div>
      </div>
    </Modal>
  );
}

export default TransactionDetailModal;
