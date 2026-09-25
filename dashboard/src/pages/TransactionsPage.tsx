import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { DataTable } from '@/components/ui/DataTable';
import {
  IconPlus,
  IconDownload,
  IconRefreshCw,
  IconMoreVertical,
  IconTrash,
} from '@/components/ui/icons';
import { TransactionsPageSkeleton } from '@/components/ui/Skeleton';
import type { Transaction, TransactionsPageProps } from '@/types';

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
  onBulkDelete,
}: TransactionsPageProps) {
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  if (loading && transactions.length === 0) {
    return <TransactionsPageSkeleton />;
  }

  const handleExportCsv = () => {
    const dataToExport = selectedIds.size > 0
      ? transactions.filter((t) => selectedIds.has(t.id))
      : transactions;

    const csv =
      'ID,Amount,Status,Direction,Payer,VPA,RefNo,Date\n' +
      dataToExport
        .map(
          (t) =>
            `"${t.id}",${t.amount},"${t.status}","${t.direction || ''}","${t.payerName || ''}","${t.payeeVpa || ''}","${t.referenceNumber || ''}","${t.occurredAt || t.createdAt}"`
        )
        .join('\n');
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `transactions_${Date.now()}.csv`;
    a.click();
  };

  const columns = [
    {
      header: 'UTR Reference / ID',
      cell: (txn: Transaction) => (
        <div className="font-mono">
          <div className="font-bold text-foreground">
            {txn.referenceNumber || txn.id.slice(0, 10)}
          </div>
          <div className="text-[10px] text-muted-foreground">{txn.id}</div>
        </div>
      ),
    },
    {
      header: 'Date',
      cell: (txn: Transaction) => (
        <span className="text-muted-foreground text-[11px]">
          {new Date(txn.occurredAt || txn.createdAt).toLocaleString()}
        </span>
      ),
    },
    {
      header: 'Direction',
      cell: (txn: Transaction) => (
        <Badge variant={txn.direction === 'RECEIVED' ? 'success' : 'warning'}>
          {txn.direction || 'RECEIVED'}
        </Badge>
      ),
    },
    {
      header: 'Amount',
      cell: (txn: Transaction) => (
        <span className="font-extrabold text-sm text-foreground">
          ₹{Number(txn.amount).toFixed(2)}
        </span>
      ),
    },
    {
      header: 'Customer / VPA',
      cell: (txn: Transaction) => (
        <div>
          <div className="text-foreground font-medium">
            {txn.payerName || txn.payerVpa || 'Customer'}
          </div>
          <div className="font-mono text-[10px] text-muted-foreground">
            {txn.payeeVpa}
          </div>
        </div>
      ),
    },
    {
      header: 'Status',
      cell: (txn: Transaction) => (
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
      ),
    },
    {
      header: 'Actions',
      headerClassName: 'text-right',
      cellClassName: 'text-right',
      cell: (txn: Transaction) => (
        <Button
          variant="outline"
          size="sm"
          onClick={() => onSelectTxnAction('options', txn)}
          className="rounded-lg h-7 px-2.5 text-xs gap-1">
          <IconMoreVertical className="w-3 h-3" />
        </Button>
      ),
    },
  ];

  return (
    <div className="space-y-5 animate-fade-in max-w-7xl mx-auto">
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
        </div>
      </div>

      {/* Modular DataTable with Menu-style dropdown and bulk actions */}
      <DataTable
        data={transactions}
        keyExtractor={(t) => t.id}
        columns={columns}
        loading={loading}
        emptyMessage="No transactions found matching your criteria."
        search={search}
        onSearchChange={onSearchChange}
        searchPlaceholder="Search by UTR Reference, Payer, VPA, Note..."
        menuDropdowns={[
          {
            id: 'status',
            label: 'Status',
            value: status,
            options: [
              { value: '', label: 'All Statuses' },
              { value: 'SUCCESS', label: 'Success' },
              { value: 'PENDING', label: 'Pending' },
              { value: 'FAILED', label: 'Failed' },
            ],
            onChange: onStatusChange,
          },
        ]}
        enableBulkSelect={true}
        selectedIds={selectedIds}
        onSelectionChange={setSelectedIds}
        bulkActions={[
          {
            label: 'Export Selected',
            icon: <IconDownload className="w-3.5 h-3.5" />,
            variant: 'outline',
            onClick: handleExportCsv,
          },
          ...(onBulkDelete
            ? [
                {
                  label: 'Delete Selected',
                  icon: <IconTrash className="w-3.5 h-3.5" />,
                  variant: 'destructive' as const,
                  onClick: async (ids: string[]) => {
                    await onBulkDelete(ids);
                    setSelectedIds(new Set());
                  },
                },
              ]
            : []),
        ]}
        headerActions={
          <>
            <Button
              variant="outline"
              size="sm"
              onClick={handleExportCsv}
              className="rounded-xl h-9 gap-1.5 text-xs">
              <IconDownload className="w-3.5 h-3.5" />
              <span>Export CSV</span>
            </Button>
            <Button
              variant="secondary"
              size="sm"
              onClick={onRefresh}
              className="rounded-xl h-9 gap-1.5 text-xs">
              <IconRefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
              <span>Refresh</span>
            </Button>
          </>
        }
      />
    </div>
  );
}

export default TransactionsPage;
