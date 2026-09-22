import React from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { IconPlus, IconLandmark } from '@/components/ui/icons';
import type { BankAccount } from '@/types';

export interface AccountsPageProps {
  bankAccounts: BankAccount[];
  canManageAccounts?: boolean;
  loading?: boolean;
  onOpenNewBank: () => void;
}

// Settlement Bank Accounts Page using shadcn/ui with strict TypeScript types
export function AccountsPage({ bankAccounts, canManageAccounts = true, loading = false, onOpenNewBank }: AccountsPageProps) {
  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            Settlement Bank Accounts
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Bank accounts linked for automated merchant payment settlement
          </p>
        </div>
        {canManageAccounts && (
          <Button
            variant="brand"
            size="sm"
            onClick={onOpenNewBank}
            className="rounded-xl gap-1.5 font-bold self-start sm:self-auto">
            <IconPlus className="w-3.5 h-3.5" />
            <span>Link New Bank Account</span>
          </Button>
        )}
      </div>

      {loading ? (
        <Card className="p-12 flex flex-col items-center justify-center text-center">
          <div className="w-8 h-8 rounded-full border-2 border-brand-500 border-t-transparent animate-spin mb-3" />
          <p className="text-xs text-muted-foreground font-medium">Loading settlement bank accounts...</p>
        </Card>
      ) : bankAccounts.length === 0 ? (
        <Card className="p-12 text-center flex flex-col items-center justify-center space-y-3">
          <div className="w-12 h-12 rounded-2xl bg-brand-500/10 text-brand-600 dark:text-cyan-400 flex items-center justify-center">
            <IconLandmark className="w-6 h-6" />
          </div>
          <h3 className="font-bold text-base text-foreground">No Bank Accounts Linked</h3>
          <p className="text-xs text-muted-foreground max-w-sm">
            Connect a verified settlement bank account to receive automated daily payouts.
          </p>
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
            <Card key={acc.id} className="p-6 space-y-4">
              <div className="flex items-center justify-between">
                <CardTitle className="text-base font-bold">{acc.bankName}</CardTitle>
                <Badge variant="cyan" className="text-[10px] font-bold">
                  {acc.accountType || 'CURRENT'}
                </Badge>
              </div>

              <div className="p-4 rounded-xl bg-muted/40 border border-border space-y-2 text-xs">
                <div className="flex justify-between">
                  <span className="text-muted-foreground">Account Holder:</span>
                  <span className="font-semibold text-foreground">{acc.accountHolderName}</span>
                </div>
                <div className="flex justify-between font-mono">
                  <span className="text-muted-foreground">Account Number:</span>
                  <span className="text-foreground font-semibold">{acc.accountNumberMasked || acc.accountNumber || '••••'}</span>
                </div>
                <div className="flex justify-between font-mono">
                  <span className="text-muted-foreground">IFSC Code:</span>
                  <span className="text-brand-600 dark:text-cyan-400 font-semibold">{acc.ifscCode}</span>
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}

export default AccountsPage;
