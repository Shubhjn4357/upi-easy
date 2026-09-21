// Dashboard Overview & KPIs Page using shadcn/ui
function OverviewPage({ stats, onOpenNewTxn, onOpenNewUpi, onNavigate, onInspectTxn }) {
  return (
    <div className="space-y-6 animate-fade-in max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            Dashboard Overview
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Real-time merchant collections, UPI volume and transaction status
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
            onClick={onOpenNewUpi}
            className="rounded-xl">
            Add UPI ID
          </Button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <Card className="p-5 relative overflow-hidden">
          <CardDescription className="text-xs font-bold uppercase tracking-wider">
            Today's Collections
          </CardDescription>
          <div className="text-2xl sm:text-3xl font-extrabold text-foreground mt-2 tracking-tight">
            ₹{Number(stats?.todayReceived?.amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
          </div>
          <div className="mt-2 text-xs text-emerald-600 dark:text-emerald-400 flex items-center gap-1 font-medium">
            <IconCheck className="w-3.5 h-3.5" />
            <span>Verified collections</span>
            <span className="text-muted-foreground">· {stats?.todayReceived?.count || 0} txns</span>
          </div>
        </Card>

        <Card className="p-5 relative overflow-hidden">
          <CardDescription className="text-xs font-bold uppercase tracking-wider">
            Settlement Volume (Sent)
          </CardDescription>
          <div className="text-2xl sm:text-3xl font-extrabold text-foreground mt-2 tracking-tight">
            ₹{Number(stats?.todaySent?.amount || 0).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
          </div>
          <div className="mt-2 text-xs text-muted-foreground flex items-center gap-1 font-medium">
            <span>{stats?.todaySent?.count || 0} settlements / refunds</span>
          </div>
        </Card>

        <Card className="p-5 relative overflow-hidden">
          <CardDescription className="text-xs font-bold uppercase tracking-wider">
            Active UPI VPAs
          </CardDescription>
          <div className="text-2xl sm:text-3xl font-extrabold text-brand-600 dark:text-cyan-400 mt-2 tracking-tight">
            {stats?.activeUpiCount || 0}
          </div>
          <div className="mt-2 text-xs text-brand-600/80 dark:text-cyan-400/80 flex items-center gap-1 font-medium">
            <span>Active in NPCI directory</span>
          </div>
        </Card>

        <Card className="p-5 relative overflow-hidden">
          <CardDescription className="text-xs font-bold uppercase tracking-wider">
            Pending / Attention
          </CardDescription>
          <div className="text-2xl sm:text-3xl font-extrabold text-amber-500 dark:text-amber-400 mt-2 tracking-tight">
            {stats?.pendingCount || 0}
          </div>
          <div className="mt-2 text-xs text-destructive flex items-center gap-1 font-medium">
            <span>{stats?.failedCount || 0} failed attempts</span>
          </div>
        </Card>
      </div>

      {/* Live Feed and Quick Shortcuts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Recent Transactions List */}
        <Card className="lg:col-span-2 p-5">
          <div className="flex items-center justify-between mb-4">
            <CardTitle className="text-sm font-bold">Recent Transactions</CardTitle>
            <Button
              variant="link"
              size="sm"
              onClick={() => onNavigate("transactions")}
              className="text-xs p-0 h-auto gap-1">
              <span>View All</span>
              <IconArrowUpRight className="w-3.5 h-3.5" />
            </Button>
          </div>

          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>UTR / ID</TableHead>
                <TableHead>Payer</TableHead>
                <TableHead>Payee VPA</TableHead>
                <TableHead>Amount</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(stats?.recentTransactions || []).length === 0 ? (
                <TableRow>
                  <TableCell colSpan={5} className="py-6 text-center text-muted-foreground">
                    No recent transactions today.
                  </TableCell>
                </TableRow>
              ) : (
                stats.recentTransactions.map(txn => (
                  <TableRow 
                    key={txn.id} 
                    onClick={() => onInspectTxn(txn)}
                    className="cursor-pointer">
                    <TableCell className="font-mono">
                      <div className="font-bold">{txn.referenceNumber || txn.id.slice(0, 10)}</div>
                    </TableCell>
                    <TableCell className="font-medium">
                      {txn.payerName || txn.payerVpa || 'Customer'}
                    </TableCell>
                    <TableCell className="font-mono text-muted-foreground">{txn.payeeVpa}</TableCell>
                    <TableCell className="font-extrabold">
                      ₹{Number(txn.amount).toFixed(2)}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant={
                          txn.status === 'SUCCESS' ? 'success' :
                          txn.status === 'PENDING' ? 'warning' : 'destructive'
                        }>
                        {txn.status}
                      </Badge>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </Card>

        {/* Quick Actions Shortcuts */}
        <Card className="p-5 space-y-3">
          <CardTitle className="text-sm font-bold">Quick Shortcuts</CardTitle>
          <div className="space-y-2">
            <Button
              variant="outline"
              onClick={() => onNavigate("upi")}
              className="w-full justify-between h-auto py-3 px-4 rounded-xl">
              <div className="flex items-center gap-2.5">
                <IconQrCode className="w-4 h-4 text-brand-500" />
                <div className="text-left">
                  <div className="font-semibold text-xs">Generate Counter QR</div>
                  <div className="text-[10px] text-muted-foreground">Dynamic amount UPI codes</div>
                </div>
              </div>
              <IconArrowUpRight className="w-3.5 h-3.5 opacity-50" />
            </Button>

            <Button
              variant="outline"
              onClick={() => onNavigate("staff")}
              className="w-full justify-between h-auto py-3 px-4 rounded-xl">
              <div className="flex items-center gap-2.5">
                <IconUsers className="w-4 h-4 text-indigo-500" />
                <div className="text-left">
                  <div className="font-semibold text-xs">Manage Store Staff</div>
                  <div className="text-[10px] text-muted-foreground">Cashier & Manager access</div>
                </div>
              </div>
              <IconArrowUpRight className="w-3.5 h-3.5 opacity-50" />
            </Button>

            <Button
              variant="outline"
              onClick={() => onNavigate("accounts")}
              className="w-full justify-between h-auto py-3 px-4 rounded-xl">
              <div className="flex items-center gap-2.5">
                <IconWallet className="w-4 h-4 text-emerald-500" />
                <div className="text-left">
                  <div className="font-semibold text-xs">Settlement Accounts</div>
                  <div className="text-[10px] text-muted-foreground">Bank account routing</div>
                </div>
              </div>
              <IconArrowUpRight className="w-3.5 h-3.5 opacity-50" />
            </Button>

            <Button
              variant="outline"
              onClick={() => onNavigate("health")}
              className="w-full justify-between h-auto py-3 px-4 rounded-xl">
              <div className="flex items-center gap-2.5">
                <IconHeartPulse className="w-4 h-4 text-cyan-500" />
                <div className="text-left">
                  <div className="font-semibold text-xs">System Diagnostics</div>
                  <div className="text-[10px] text-muted-foreground">API latency & SQLite metrics</div>
                </div>
              </div>
              <IconArrowUpRight className="w-3.5 h-3.5 opacity-50" />
            </Button>
          </div>
        </Card>
      </div>
    </div>
  );
}

export { OverviewPage };
export default OverviewPage;
