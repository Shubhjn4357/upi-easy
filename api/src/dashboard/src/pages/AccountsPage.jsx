// Settlement Bank Accounts Page using shadcn/ui
function AccountsPage({ bankAccounts, onOpenNewBank }) {
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
        <Button
          variant="brand"
          size="sm"
          onClick={onOpenNewBank}
          className="rounded-xl gap-1.5 font-bold self-start sm:self-auto">
          <IconPlus className="w-3.5 h-3.5" />
          <span>Link New Bank Account</span>
        </Button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
        {bankAccounts.map(acc => (
          <Card key={acc.id} className="p-6 space-y-4">
            <div className="flex items-center justify-between">
              <CardTitle className="text-base font-bold">{acc.bankName}</CardTitle>
              <Badge variant="cyan" className="text-[10px] font-bold">
                {acc.accountType}
              </Badge>
            </div>

            <div className="p-4 rounded-xl bg-muted/40 border border-border space-y-2 text-xs">
              <div className="flex justify-between">
                <span className="text-muted-foreground">Account Holder:</span>
                <span className="font-semibold text-foreground">{acc.accountHolderName}</span>
              </div>
              <div className="flex justify-between font-mono">
                <span className="text-muted-foreground">Account Number:</span>
                <span className="text-foreground font-semibold">{acc.accountNumberMasked}</span>
              </div>
              <div className="flex justify-between font-mono">
                <span className="text-muted-foreground">IFSC Code:</span>
                <span className="text-brand-600 dark:text-cyan-400 font-semibold">{acc.ifscCode}</span>
              </div>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

export { AccountsPage };
export default AccountsPage;
