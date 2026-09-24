import React from 'react';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { IconPlus, IconQrCode, IconMoreVertical } from '@/components/ui/icons';
import { UpiPageSkeleton } from '@/components/ui/Skeleton';
import type { UpiAccount } from '@/types';

export interface UpiPageProps {
  upiAccounts: any[];
  canManageUpi?: boolean;
  loading?: boolean;
  onOpenNewUpi: () => void;
  onSelectUpiAction: (upi: any, action: string) => void;
}

// UPI IDs & QR Codes Management Page using shadcn/ui with strict TypeScript types
export function UpiPage({ upiAccounts, canManageUpi = true, loading = false, onOpenNewUpi, onSelectUpiAction }: UpiPageProps) {
  if (loading) {
    return <UpiPageSkeleton />;
  }

  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            UPI Accounts & QR Codes
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Manage Virtual Payment Addresses (VPAs) and store counter QR codes
          </p>
        </div>
        {canManageUpi && (
          <Button
            variant="brand"
            size="sm"
            onClick={onOpenNewUpi}
            className="rounded-xl gap-1.5 font-bold self-start sm:self-auto">
            <IconPlus className="w-3.5 h-3.5" />
            <span>Add UPI ID</span>
          </Button>
        )}
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
      ) : (
        /* UPI Cards Grid */
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {upiAccounts.map((upi: any) => (
            <Card
              key={upi.id}
              className="p-5 flex flex-col justify-between relative overflow-hidden">
              <div>
                <div className="flex items-center justify-between">
                  <span className="font-mono text-xs text-brand-600 dark:text-cyan-400 bg-brand-500/10 px-2.5 py-1 rounded-lg border border-brand-500/20 font-bold">
                    {upi.vpa || upi.upiId}
                  </span>
                  {(upi.isDefault || upi.isPrimary) && (
                    <Badge variant="success" className="text-[10px] font-bold">
                      PRIMARY
                    </Badge>
                  )}
                </div>

                <div className="mt-4">
                  <h4 className="font-bold text-foreground text-base">{upi.payeeName || upi.accountHolderName}</h4>
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
          ))}
        </div>
      )}
    </div>
  );
}

export default UpiPage;
