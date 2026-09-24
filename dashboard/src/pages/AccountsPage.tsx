import React from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { IconPlus, IconLandmark, IconEdit, IconCheckCircle } from '@/components/ui/icons';
import { AccountsPageSkeleton } from '@/components/ui/Skeleton';
import type { BankAccount } from '@/types';

export interface AccountsPageProps {
  bankAccounts: BankAccount[];
  canManageAccounts?: boolean;
  loading?: boolean;
  onOpenNewBank: () => void;
  onEditBank?: (account: BankAccount) => void;
}

// Settlement Bank Accounts Page with App-like Bento Cards and Skeleton Loaders
export function AccountsPage({
  bankAccounts,
  canManageAccounts = true,
  loading = false,
  onOpenNewBank,
  onEditBank,
}: AccountsPageProps) {
  if (loading) {
    return <AccountsPageSkeleton />;
  }

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
        {canManageAccounts && (
          <Button
            variant="brand"
            size="sm"
            onClick={onOpenNewBank}
            className="rounded-xl gap-1.5 font-bold self-start sm:self-auto shadow-sm">
            <IconPlus className="w-3.5 h-3.5" />
            <span>Link New Bank Account</span>
          </Button>
        )}
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
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
          {bankAccounts.map((acc: BankAccount) => (
            <Card
              key={acc.id}
              className="p-6 space-y-4 rounded-3xl border border-border/70 bg-card/75 backdrop-blur-md hover:border-primary/40 hover:shadow-lg transition-all duration-200">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
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

              {canManageAccounts && onEditBank && (
                <div className="pt-1 flex items-center justify-end gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onEditBank(acc)}
                    className="rounded-xl gap-1.5 text-xs font-semibold hover:border-primary/50">
                    <IconEdit className="w-3.5 h-3.5" />
                    <span>Edit Bank Details</span>
                  </Button>
                </div>
              )}
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

export default AccountsPage;
