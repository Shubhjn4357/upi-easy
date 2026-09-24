import React from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Table, TableHeader, TableBody, TableRow, TableHead, TableCell } from '@/components/ui/table';
import { IconPlus, IconDownload, IconSearch, IconRefreshCw, IconMoreVertical } from '@/components/ui/icons';
import { TransactionsPageSkeleton } from '@/components/ui/Skeleton';
import type { Transaction, TransactionsPageProps } from '@/types';

// Transactions & Ledger Explorer Page using shadcn/ui & Action Bottom Sheet with strict TypeScript types
export function TransactionsPage({
  transactions,
  loading,
  onRefresh,
  search,
  onSearchChange,
  status,
  onStatusChange,
  onOpenNewTxn,
  onSelectTxnAction,
}: TransactionsPageProps) {
  if (loading && transactions.length === 0) {
    return <TransactionsPageSkeleton />;
  }

  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            Transactions Ledger
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Immutable audit trail of all UPI collections, intents, and settlements
          </p>
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="brand"
            size="sm"
            onClick={onOpenNewTxn}
            className="rounded-xl gap-1.5 font-bold">
            <IconPlus className="w-3.5 h-3.5" />
            <span>Record Payment</span>
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              const csv =
                'ID,Amount,Status,Direction,Payer,VPA,RefNo,Date\n' +
                transactions
                  .map(
                    (t: any) =>
                      `"${t.id}",${t.amount},"${t.status}","${t.direction || ''}","${t.payerName || ''}","${t.payeeVpa || ''}","${t.referenceNumber || ''}","${t.occurredAt || t.createdAt}"`
                  )
                  .join('\n');
              const blob = new Blob([csv], { type: 'text/csv' });
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `transactions_${Date.now()}.csv`;
              a.click();
            }}
            className="rounded-xl gap-1.5">
            <IconDownload className="w-3.5 h-3.5" />
            <span>Export CSV</span>
          </Button>
        </div>
      </div>

      {/* Filter Bar */}
      <Card className="p-3.5 flex flex-col md:flex-row items-center gap-3">
        <div className="relative flex-1 w-full">
          <IconSearch className="absolute left-3 top-2.5 w-4 h-4 text-muted-foreground pointer-events-none" />
          <Input
            type="text"
            placeholder="Search by UTR Reference, Payer, VPA, Note..."
            value={search}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => onSearchChange(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="flex items-center gap-2 w-full md:w-auto">
          <select
            value={status}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => onStatusChange(e.target.value)}
            className="h-9 bg-background border border-input rounded-xl px-3 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring">
            <option value="" className="bg-card text-card-foreground">
              All Statuses
            </option>
            <option value="SUCCESS" className="bg-card text-card-foreground">
              SUCCESS
            </option>
            <option value="PENDING" className="bg-card text-card-foreground">
              PENDING
            </option>
            <option value="FAILED" className="bg-card text-card-foreground">
              FAILED
            </option>
          </select>
          <Button
            variant="secondary"
            size="sm"
            onClick={onRefresh}
            className="rounded-xl gap-1.5">
            <IconRefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
            <span>Refresh</span>
          </Button>
        </div>
      </Card>

      {/* Full Info Table */}
      <Card className="overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>UTR Reference / ID</TableHead>
              <TableHead>Date</TableHead>
              <TableHead>Direction</TableHead>
              <TableHead>Amount</TableHead>
              <TableHead>Customer / VPA</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                  Loading ledger data...
                </TableCell>
              </TableRow>
            ) : transactions.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
                  No transactions found.
                </TableCell>
              </TableRow>
            ) : (
              transactions.map((txn: any) => (
                <TableRow key={txn.id}>
                  <TableCell className="font-mono">
                    <div className="font-bold text-foreground">{txn.referenceNumber || txn.id.slice(0, 10)}</div>
                    <div className="text-[10px] text-muted-foreground">{txn.id}</div>
                  </TableCell>
                  <TableCell className="text-muted-foreground text-[11px]">
                    {new Date(txn.occurredAt || txn.createdAt).toLocaleString()}
                  </TableCell>
                  <TableCell>
                    <Badge variant={txn.direction === 'RECEIVED' ? 'success' : 'warning'}>
                      {txn.direction || 'RECEIVED'}
                    </Badge>
                  </TableCell>
                  <TableCell className="font-extrabold text-sm">
                    ₹{Number(txn.amount).toFixed(2)}
                  </TableCell>
                  <TableCell>
                    <div className="text-foreground font-medium">{txn.payerName || txn.payerVpa || 'Customer'}</div>
                    <div className="font-mono text-[10px] text-muted-foreground">{txn.payeeVpa}</div>
                  </TableCell>
                  <TableCell>
                    <Badge
                      variant={
                        txn.status === 'SUCCESS' || txn.status === 'COMPLETED'
                          ? 'success'
                          : txn.status === 'PENDING'
                          ? 'warning'
                          : 'destructive'
                      }>
                      {txn.status}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => onSelectTxnAction('options', txn)}
                      className="rounded-lg h-7 px-2.5 text-xs gap-1">
                      
                      <IconMoreVertical className="w-3 h-3" />
                    </Button>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>
    </div>
  );
}

export default TransactionsPage;
