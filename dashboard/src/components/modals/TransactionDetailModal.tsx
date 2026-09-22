import React from 'react';
import { Badge } from '@/components/ui/badge';
import Modal from '@/components/Modal';
import type { Transaction } from '@/types';

export interface TransactionDetailModalProps {
  transaction: Transaction | null;
  onClose: () => void;
}

// Transaction Audit & Detail Modal with strict TypeScript types
export function TransactionDetailModal({
  transaction,
  onClose,
}: TransactionDetailModalProps) {
  if (!transaction) return null;

  return (
    <Modal
      isOpen={Boolean(transaction)}
      onClose={onClose}
      title="Transaction Audit Record"
      subtitle={transaction.referenceNumber || transaction.id}>
      <div className="space-y-4 text-xs">
        <div className="grid grid-cols-2 gap-3 p-4 rounded-xl bg-muted/40 border border-border">
          <div>
            <span className="text-muted-foreground block">Amount</span>
            <span className="text-xl font-black text-foreground">
              ₹{Number(transaction.amount).toFixed(2)}
            </span>
          </div>
          <div>
            <span className="text-muted-foreground block">Status</span>
            <Badge
              variant={
                transaction.status === 'SUCCESS' || transaction.status === 'COMPLETED'
                  ? 'success'
                  : 'warning'
              }
              className="mt-1 font-bold">
              {transaction.status}
            </Badge>
          </div>
          <div>
            <span className="text-muted-foreground block">Customer</span>
            <span className="font-semibold text-foreground">
              {transaction.payerName || 'Unknown'}
            </span>
          </div>
          <div>
            <span className="text-muted-foreground block">Customer VPA</span>
            <span className="font-mono text-foreground">
              {transaction.payerVpa || '—'}
            </span>
          </div>
          <div>
            <span className="text-muted-foreground block">Payee VPA</span>
            <span className="font-mono text-brand-600 dark:text-cyan-400">
              {transaction.payeeVpa}
            </span>
          </div>
          <div>
            <span className="text-muted-foreground block">Method</span>
            <span className="text-foreground">
              {transaction.paymentMode || 'UPI'} · {transaction.source || 'APP'}
            </span>
          </div>
        </div>

        <div>
          <h4 className="font-bold text-foreground mb-1.5">JSON Payload</h4>
          <pre className="p-3 rounded-xl bg-muted/60 border border-border text-[11px] font-mono text-foreground overflow-x-auto">
            {JSON.stringify(transaction, null, 2)}
          </pre>
        </div>
      </div>
    </Modal>
  );
}

export default TransactionDetailModal;
