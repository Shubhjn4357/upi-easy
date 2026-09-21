// UPI IDs & QR Codes Management Page using shadcn/ui
function UpiPage({ upiAccounts, onOpenNewUpi, onSelectUpiAction }) {
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
        <Button
          variant="brand"
          size="sm"
          onClick={onOpenNewUpi}
          className="rounded-xl gap-1.5 font-bold self-start sm:self-auto">
          <IconPlus className="w-3.5 h-3.5" />
          <span>Add UPI ID</span>
        </Button>
      </div>

      {/* UPI Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {upiAccounts.map(upi => (
          <Card 
            key={upi.id} 
            className="p-5 flex flex-col justify-between relative overflow-hidden">
            <div>
              <div className="flex items-center justify-between">
                <span className="font-mono text-xs text-brand-600 dark:text-cyan-400 bg-brand-500/10 px-2.5 py-1 rounded-lg border border-brand-500/20 font-bold">
                  {upi.vpa}
                </span>
                {upi.isDefault && (
                  <Badge variant="success" className="text-[10px] font-bold">
                    PRIMARY
                  </Badge>
                )}
              </div>

              <div className="mt-4">
                <h4 className="font-bold text-foreground text-base">{upi.payeeName}</h4>
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
                onClick={() => onSelectUpiAction(upi, "qr")}
                className="flex-1 rounded-xl gap-1.5 font-semibold">
                <IconQrCode className="w-3.5 h-3.5" />
                <span>Show QR</span>
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => onSelectUpiAction(upi, "menu")}
                className="rounded-xl gap-1 font-semibold">
                <span>Actions</span>
                <IconMoreVertical className="w-3.5 h-3.5" />
              </Button>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

export { UpiPage };
export default UpiPage;
