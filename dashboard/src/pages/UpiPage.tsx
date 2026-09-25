import React, { useState, useMemo } from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  IconPlus,
  IconQrCode,
  IconMoreVertical,
  IconTrash,
} from '@/components/ui/icons';
import { UpiPageSkeleton } from '@/components/ui/Skeleton';
import { DataTable } from '@/components/ui/DataTable';
import type { UpiAccount } from '@/types';

export interface UpiPageProps {
  upiAccounts: UpiAccount[];
  canManageUpi?: boolean;
  loading?: boolean;
  onOpenNewUpi: () => void;
  onSelectUpiAction: (upi: UpiAccount, action: string) => void;
  onBulkDelete?: (ids: string[]) => Promise<void> | void;
}

export function UpiPage({
  upiAccounts,
  canManageUpi = true,
  loading = false,
  onOpenNewUpi,
  onSelectUpiAction,
  onBulkDelete,
}: UpiPageProps) {
  const [viewMode, setViewMode] = useState<'cards' | 'table'>('cards');
  const [search, setSearch] = useState('');
  const [filterType, setFilterType] = useState('ALL');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  // Filter accounts
  const filteredAccounts = useMemo(() => {
    return upiAccounts.filter((account) => {
      const vpa = (account.vpa || account.upiId || '').toLowerCase();
      const payee = (account.payeeName || account.accountHolderName || '').toLowerCase();
      const query = search.toLowerCase().trim();

      const matchesSearch = !query || vpa.includes(query) || payee.includes(query);
      if (!matchesSearch) return false;

      if (filterType === 'PRIMARY') {
        return Boolean(account.isDefault || account.isPrimary);
      }
      if (filterType === 'ACTIVE') {
        return account.isActive !== false && account.status !== 'INACTIVE';
      }
      return true;
    });
  }, [upiAccounts, search, filterType]);

  const handleToggleCardSelect = (id: string) => {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  };

  const handleBulkDelete = async (ids: string[]) => {
    if (onBulkDelete) {
      await onBulkDelete(ids);
      setSelectedIds(new Set());
    }
  };

  if (loading) {
    return <UpiPageSkeleton />;
  }

  const tableColumns = [
    {
      header: 'UPI VPA / Handle',
      cell: (item: UpiAccount) => (
        <div className="flex items-center gap-2">
          <span className="font-mono text-xs text-brand-600 dark:text-cyan-400 font-bold bg-brand-500/10 px-2.5 py-1 rounded-lg border border-brand-500/20">
            {item.vpa || item.upiId}
          </span>
          {(item.isDefault || item.isPrimary) && (
            <Badge variant="success" className="text-[10px] font-bold">
              PRIMARY
            </Badge>
          )}
        </div>
      ),
    },
    {
      header: 'Payee Name',
      cell: (item: UpiAccount) => (
        <div>
          <div className="font-bold text-sm text-foreground">
            {item.payeeName || item.accountHolderName || 'Merchant'}
          </div>
          <div className="text-[11px] text-muted-foreground">MCC: {item.merchantCategoryCode || '5411'}</div>
        </div>
      ),
    },
    {
      header: 'Settlement Account',
      cell: (item: UpiAccount) => (
        <div className="text-xs text-muted-foreground">
          {item.bankName ? (
            <span>
              {item.bankName} ({item.accountNumberMasked || '••••'})
            </span>
          ) : (
            <span className="italic text-muted-foreground/60">Not Linked</span>
          )}
        </div>
      ),
    },
    {
      header: 'Transactions',
      cell: (item: UpiAccount) => (
        <div className="text-xs font-semibold text-foreground">
          {item.transactionCount || 0} txn
        </div>
      ),
    },
    {
      header: 'Status',
      cell: (item: UpiAccount) => (
        <Badge
          variant={item.isActive !== false && item.status !== 'INACTIVE' ? 'success' : 'secondary'}
          className="text-[10px] font-bold">
          {item.isActive !== false && item.status !== 'INACTIVE' ? 'ACTIVE' : 'INACTIVE'}
        </Badge>
      ),
    },
    {
      header: 'Actions',
      cell: (item: UpiAccount) => (
        <div className="flex items-center justify-end gap-1.5">
          <Button
            variant="secondary"
            size="sm"
            onClick={() => onSelectUpiAction(item, 'qr')}
            className="rounded-lg h-7 px-2.5 text-xs font-semibold gap-1">
            <IconQrCode className="w-3.5 h-3.5" />
            <span>QR</span>
          </Button>
          {canManageUpi && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => onSelectUpiAction(item, 'menu')}
              className="rounded-lg h-7 w-7 p-0">
              <IconMoreVertical className="w-3.5 h-3.5" />
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Top Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            UPI Accounts & QR Codes
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Manage Virtual Payment Addresses (VPAs) and store counter QR codes
          </p>
        </div>
        <div className="flex items-center gap-2 self-start sm:self-auto">
          {/* View toggle */}
          <div className="flex items-center bg-muted/60 p-0.5 rounded-xl border border-border/80">
            <button
              type="button"
              onClick={() => setViewMode('cards')}
              className={`px-3 py-1 text-xs font-bold rounded-lg transition-colors ${
                viewMode === 'cards'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}>
              Cards
            </button>
            <button
              type="button"
              onClick={() => setViewMode('table')}
              className={`px-3 py-1 text-xs font-bold rounded-lg transition-colors ${
                viewMode === 'table'
                  ? 'bg-background text-foreground shadow-sm'
                  : 'text-muted-foreground hover:text-foreground'
              }`}>
              Table
            </button>
          </div>

          {canManageUpi && (
            <Button
              variant="brand"
              size="sm"
              onClick={onOpenNewUpi}
              className="rounded-xl gap-1.5 font-bold shadow-sm">
              <IconPlus className="w-3.5 h-3.5" />
              <span>Add UPI ID</span>
            </Button>
          )}
        </div>
      </div>

      {upiAccounts.length === 0 ? (
        <Card className="p-12 text-center flex flex-col items-center justify-center space-y-3">
          <div className="w-12 h-12 rounded-2xl bg-brand-500/10 text-brand-600 dark:text-cyan-400 flex items-center justify-center">
            <IconQrCode className="w-6 h-6" />
          </div>
          <h3 className="font-bold text-base text-foreground">No UPI Accounts Configured</h3>
          <p className="text-xs text-muted-foreground max-w-sm">
            Add a merchant UPI VPA to accept instantaneous customer payments and generate counter QR codes.
          </p>
          {canManageUpi && (
            <Button
              variant="brand"
              size="sm"
              onClick={onOpenNewUpi}
              className="rounded-xl gap-1.5 font-bold mt-2">
              <IconPlus className="w-3.5 h-3.5" />
              <span>Configure First UPI ID</span>
            </Button>
          )}
        </Card>
      ) : viewMode === 'table' ? (
        <DataTable
          data={filteredAccounts}
          keyExtractor={(item) => item.id}
          columns={tableColumns}
          search={search}
          onSearchChange={setSearch}
          searchPlaceholder="Search by UPI VPA or payee name..."
          menuDropdowns={[
            {
              id: 'filter',
              label: 'Filter',
              value: filterType,
              options: [
                { value: 'ALL', label: 'All UPI IDs', count: upiAccounts.length },
                {
                  value: 'PRIMARY',
                  label: 'Primary Only',
                  count: upiAccounts.filter((u) => u.isDefault || u.isPrimary).length,
                },
                {
                  value: 'ACTIVE',
                  label: 'Active Only',
                  count: upiAccounts.filter((u) => u.isActive !== false && u.status !== 'INACTIVE').length,
                },
              ],
              onChange: setFilterType,
            },
          ]}
          enableBulkSelect={canManageUpi && Boolean(onBulkDelete)}
          selectedIds={selectedIds}
          onSelectionChange={setSelectedIds}
          bulkActions={[
            {
              label: 'Delete Selected',
              icon: <IconTrash className="w-4 h-4" />,
              variant: 'destructive',
              onClick: handleBulkDelete,
            },
          ]}
          emptyMessage="No UPI accounts match your search filters."
        />
      ) : (
        /* Bento Cards Grid View */
        <div className="space-y-4">
          {/* Card View Header Filters */}
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
            <div className="relative flex-1 max-w-sm">
              <input
                type="text"
                placeholder="Search UPI VPAs..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full bg-card border border-border rounded-xl px-3.5 py-1.5 text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>
            <div className="flex items-center gap-2">
              {['ALL', 'PRIMARY', 'ACTIVE'].map((f) => (
                <button
                  key={f}
                  type="button"
                  onClick={() => setFilterType(f)}
                  className={`text-xs px-3 py-1 rounded-lg font-semibold transition-colors ${
                    filterType === f
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'bg-muted/50 text-muted-foreground hover:bg-muted'
                  }`}>
                  {f === 'ALL' ? 'All' : f === 'PRIMARY' ? 'Primary' : 'Active'}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
            {filteredAccounts.map((upi: UpiAccount) => {
              const isSelected = selectedIds.has(upi.id);
              return (
                <Card
                  key={upi.id}
                  className={`p-5 flex flex-col justify-between relative overflow-hidden transition-all duration-200 ${
                    isSelected ? 'ring-2 ring-primary border-primary/50' : ''
                  }`}>
                  <div>
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        {canManageUpi && onBulkDelete && (
                          <input
                            type="checkbox"
                            checked={isSelected}
                            onChange={() => handleToggleCardSelect(upi.id)}
                            className="w-4 h-4 rounded border-border text-primary focus:ring-primary/20 cursor-pointer"
                          />
                        )}
                        <span className="font-mono text-xs text-brand-600 dark:text-cyan-400 bg-brand-500/10 px-2.5 py-1 rounded-lg border border-brand-500/20 font-bold">
                          {upi.vpa || upi.upiId}
                        </span>
                      </div>
                      {(upi.isDefault || upi.isPrimary) && (
                        <Badge variant="success" className="text-[10px] font-bold">
                          PRIMARY
                        </Badge>
                      )}
                    </div>

                    <div className="mt-4">
                      <h4 className="font-bold text-foreground text-base">
                        {upi.payeeName || upi.accountHolderName}
                      </h4>
                      <div className="text-xs text-muted-foreground mt-1 flex items-center gap-2">
                        <span>MCC: {upi.merchantCategoryCode || '5411'}</span>
                        <span>·</span>
                        <span>{upi.transactionCount || 0} Transactions</span>
                      </div>
                      {upi.bankName && (
                        <div className="text-xs text-muted-foreground mt-1">
                          Settlement: {upi.bankName} ({upi.accountNumberMasked || '••••'})
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Quick Actions */}
                  <div className="mt-6 pt-3.5 border-t border-border flex items-center justify-between gap-2">
                    <Button
                      variant="secondary"
                      size="sm"
                      onClick={() => onSelectUpiAction(upi, 'qr')}
                      className="flex-1 rounded-xl gap-1.5 font-semibold">
                      <IconQrCode className="w-3.5 h-3.5" />
                      <span>Show QR</span>
                    </Button>
                    {canManageUpi && (
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onSelectUpiAction(upi, 'menu')}
                        className="rounded-xl gap-1 font-semibold">
                        <IconMoreVertical className="w-3.5 h-3.5" />
                      </Button>
                    )}
                  </div>
                </Card>
              );
            })}
          </div>

          {/* Floating Bulk Action Bar for Cards */}
          {selectedIds.size > 0 && canManageUpi && onBulkDelete && (
            <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 flex items-center gap-3 px-5 py-3 rounded-2xl bg-popover/90 border border-border shadow-2xl backdrop-blur-xl animate-slide-up">
              <span className="text-xs font-bold text-foreground">
                {selectedIds.size} account{selectedIds.size > 1 ? 's' : ''} selected
              </span>
              <div className="h-4 w-px bg-border" />
              <Button
                variant="destructive"
                size="sm"
                onClick={() => handleBulkDelete(Array.from(selectedIds))}
                className="rounded-xl gap-1.5 font-bold h-8 text-xs">
                <IconTrash className="w-3.5 h-3.5" />
                <span>Delete Selected</span>
              </Button>
              <button
                type="button"
                onClick={() => setSelectedIds(new Set())}
                className="text-xs text-muted-foreground hover:text-foreground font-semibold px-1">
                Cancel
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

export default UpiPage;
