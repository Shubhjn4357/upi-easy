import React, { useState, useMemo } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import {
  IconPlus,
  IconLandmark,
  IconEdit,
  IconCheckCircle,
  IconTrash,
  IconMoreVertical,
} from '@/components/ui/icons';
import { AccountsPageSkeleton } from '@/components/ui/Skeleton';
import { DataTable } from '@/components/ui/DataTable';
import type { BankAccount, AccountsPageProps } from '@/types';

// Settlement Bank Accounts Page with App-like Bento Cards, DataTable and Bulk Deletion
export function AccountsPage({
  bankAccounts,
  canManageAccounts = true,
  loading = false,
  onOpenNewBank,
  onSelectAccountAction,
  onBulkDelete,
}: AccountsPageProps) {
  const [viewMode, setViewMode] = useState<'cards' | 'table'>('cards');
  const [search, setSearch] = useState('');
  const [filterType, setFilterType] = useState('ALL');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());

  const filteredAccounts = useMemo(() => {
    return bankAccounts.filter((acc) => {
      const bank = (acc.bankName || '').toLowerCase();
      const holder = (acc.accountHolderName || '').toLowerCase();
      const ifsc = (acc.ifscCode || '').toLowerCase();
      const query = search.toLowerCase().trim();

      const matchesSearch = !query || bank.includes(query) || holder.includes(query) || ifsc.includes(query);
      if (!matchesSearch) return false;

      if (filterType === 'DEFAULT') {
        return Boolean(acc.isDefault);
      }
      return true;
    });
  }, [bankAccounts, search, filterType]);

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
    return <AccountsPageSkeleton />;
  }

  const tableColumns = [
    {
      header: 'Bank & Account',
      cell: (item: BankAccount) => (
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-primary/10 text-primary flex items-center justify-center shrink-0">
            <IconLandmark className="w-4 h-4" />
          </div>
          <div>
            <div className="font-bold text-sm text-foreground flex items-center gap-1.5">
              <span>{item.bankName}</span>
              {item.isDefault && (
                <span className="flex items-center gap-0.5 text-[10px] text-emerald-600 dark:text-emerald-400 font-bold">
                  <IconCheckCircle className="w-3 h-3" /> Default
                </span>
              )}
            </div>
            <p className="text-[11px] text-muted-foreground">{item.accountHolderName}</p>
          </div>
        </div>
      ),
    },
    {
      header: 'Account Number',
      cell: (item: BankAccount) => (
        <span className="font-mono text-xs font-semibold text-foreground">
          {item.accountNumberMasked || item.accountNumber || '••••'}
        </span>
      ),
    },
    {
      header: 'IFSC Code',
      cell: (item: BankAccount) => (
        <span className="font-mono text-xs font-bold text-primary">{item.ifscCode}</span>
      ),
    },
    {
      header: 'Type',
      cell: (item: BankAccount) => (
        <Badge variant="cyan" className="text-[10px] font-bold">
          {item.accountType || 'CURRENT'}
        </Badge>
      ),
    },
    {
      header: 'Actions',
      cell: (item: BankAccount) => (
        <div className="flex items-center justify-end gap-1.5">
          {canManageAccounts && onSelectAccountAction && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => onSelectAccountAction('menu', item)}
              className="rounded-lg h-7 px-2 text-xs font-semibold gap-1">
              <IconMoreVertical className="w-3.5 h-3.5" />
              <span>Options</span>
            </Button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground flex items-center gap-2">
            <span>Settlement Bank Accounts</span>
            <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-600 dark:text-emerald-400 font-semibold border border-emerald-500/20">
              0% MDR Payouts
            </span>
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Verified bank accounts linked for automated merchant daily payouts & reconciliation
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

          {canManageAccounts && (
            <Button
              variant="brand"
              size="sm"
              onClick={onOpenNewBank}
              className="rounded-xl gap-1.5 font-bold shadow-sm">
              <IconPlus className="w-3.5 h-3.5" />
              <span>Link Bank Account</span>
            </Button>
          )}
        </div>
      </div>

      {bankAccounts.length === 0 ? (
        <Card className="p-12 text-center flex flex-col items-center justify-center space-y-4 rounded-3xl border border-border/60 bg-card/60 backdrop-blur-md">
          <div className="w-14 h-14 rounded-2xl bg-primary/10 text-primary flex items-center justify-center shadow-inner">
            <IconLandmark className="w-7 h-7" />
          </div>
          <div>
            <h3 className="font-bold text-base text-foreground">No Bank Accounts Linked</h3>
            <p className="text-xs text-muted-foreground max-w-sm mt-1">
              Connect a verified settlement bank account to receive automated daily payouts with 0% MDR.
            </p>
          </div>
          {canManageAccounts && (
            <Button
              variant="brand"
              size="sm"
              onClick={onOpenNewBank}
              className="rounded-xl gap-1.5 font-bold mt-2">
              <IconPlus className="w-3.5 h-3.5" />
              <span>Link Settlement Account</span>
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
          searchPlaceholder="Search bank accounts by bank name, IFSC..."
          menuDropdowns={[
            {
              id: 'filter',
              label: 'Filter',
              value: filterType,
              options: [
                { value: 'ALL', label: 'All Accounts', count: bankAccounts.length },
                {
                  value: 'DEFAULT',
                  label: 'Default Only',
                  count: bankAccounts.filter((b) => b.isDefault).length,
                },
              ],
              onChange: setFilterType,
            },
          ]}
          enableBulkSelect={canManageAccounts && Boolean(onBulkDelete)}
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
          emptyMessage="No bank accounts match your search filters."
        />
      ) : (
        /* Bento Cards View */
        <div className="space-y-4">
          <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-3">
            <div className="relative flex-1 max-w-sm">
              <input
                type="text"
                placeholder="Search bank accounts..."
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                className="w-full bg-card border border-border rounded-xl px-3.5 py-1.5 text-xs text-foreground placeholder:text-muted-foreground focus:outline-none focus:ring-2 focus:ring-primary/20"
              />
            </div>
            <div className="flex items-center gap-2">
              {['ALL', 'DEFAULT'].map((f) => (
                <button
                  key={f}
                  type="button"
                  onClick={() => setFilterType(f)}
                  className={`text-xs px-3 py-1 rounded-lg font-semibold transition-colors ${
                    filterType === f
                      ? 'bg-primary text-primary-foreground shadow-sm'
                      : 'bg-muted/50 text-muted-foreground hover:bg-muted'
                  }`}>
                  {f === 'ALL' ? 'All Accounts' : 'Default Account'}
                </button>
              ))}
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
            {filteredAccounts.map((acc: BankAccount) => {
              const isSelected = selectedIds.has(acc.id);
              return (
                <Card
                  key={acc.id}
                  className={`p-6 space-y-4 rounded-3xl border border-border/70 bg-card/75 backdrop-blur-md hover:border-primary/40 hover:shadow-lg transition-all duration-200 ${
                    isSelected ? 'ring-2 ring-primary border-primary/50' : ''
                  }`}>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      {canManageAccounts && onBulkDelete && (
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => handleToggleCardSelect(acc.id)}
                          className="w-4 h-4 rounded border-border text-primary focus:ring-primary/20 cursor-pointer"
                        />
                      )}
                      <div className="w-10 h-10 rounded-2xl bg-primary/10 text-primary flex items-center justify-center shrink-0">
                        <IconLandmark className="w-5 h-5" />
                      </div>
                      <div>
                        <CardTitle className="text-base font-bold flex items-center gap-2">
                          <span>{acc.bankName}</span>
                          {acc.isDefault && (
                            <span className="flex items-center gap-1 text-[10px] text-emerald-600 dark:text-emerald-400 font-semibold">
                              <IconCheckCircle className="w-3 h-3" /> Default
                            </span>
                          )}
                        </CardTitle>
                        <p className="text-[11px] text-muted-foreground">{acc.accountHolderName}</p>
                      </div>
                    </div>
                    <Badge variant="cyan" className="text-[10px] font-bold px-2 py-0.5 rounded-lg">
                      {acc.accountType || 'CURRENT'}
                    </Badge>
                  </div>

                  <div className="p-4 rounded-2xl bg-muted/30 border border-border/60 space-y-2 text-xs">
                    <div className="flex justify-between">
                      <span className="text-muted-foreground">Account Holder:</span>
                      <span className="font-semibold text-foreground">{acc.accountHolderName}</span>
                    </div>
                    <div className="flex justify-between font-mono">
                      <span className="text-muted-foreground">Account Number:</span>
                      <span className="text-foreground font-semibold">
                        {acc.accountNumberMasked || acc.accountNumber || '••••'}
                      </span>
                    </div>
                    <div className="flex justify-between font-mono">
                      <span className="text-muted-foreground">IFSC Code:</span>
                      <span className="text-primary font-semibold">{acc.ifscCode}</span>
                    </div>
                  </div>

                  {canManageAccounts && onSelectAccountAction && (
                    <div className="pt-1 flex items-center justify-end gap-2">
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onSelectAccountAction('menu', acc)}
                        className="rounded-xl gap-1.5 text-xs font-semibold hover:border-primary/50">
                        <IconEdit className="w-3.5 h-3.5" />
                        <span>Manage Account</span>
                      </Button>
                    </div>
                  )}
                </Card>
              );
            })}
          </div>

          {/* Floating Bulk Action Bar for Cards */}
          {selectedIds.size > 0 && canManageAccounts && onBulkDelete && (
            <div className="fixed bottom-6 left-1/2 -translate-x-1/2 z-50 flex items-center gap-3 px-5 py-3 rounded-2xl bg-popover/90 border border-border shadow-2xl backdrop-blur-xl animate-slide-up">
              <span className="text-xs font-bold text-foreground">
                {selectedIds.size} bank account{selectedIds.size > 1 ? 's' : ''} selected
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

export default AccountsPage;
