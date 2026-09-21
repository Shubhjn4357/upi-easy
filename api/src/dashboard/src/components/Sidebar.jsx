// Desktop Left Vertical Navigation Sidebar using shadcn tokens and Lucide icons
function Sidebar({ activeTab, onSelectTab, activeOrg }) {
  const navItems = [
    { id: "overview", label: "Overview", icon: <IconActivity className="w-4 h-4" /> },
    { id: "transactions", label: "Transactions", icon: <IconCreditCard className="w-4 h-4" /> },
    { id: "upi", label: "UPI & QR Codes", icon: <IconQrCode className="w-4 h-4" /> },
    { id: "staff", label: "Staff & Roles", icon: <IconUsers className="w-4 h-4" /> },
    { id: "accounts", label: "Bank Accounts", icon: <IconWallet className="w-4 h-4" /> },
    { id: "orgs", label: "Business Profile", icon: <IconSettings className="w-4 h-4" /> },
    { id: "tables", label: "Table Explorer", icon: <IconDatabase className="w-4 h-4" />, badge: "Admin" },
    { id: "health", label: "System Health", icon: <IconHeartPulse className="w-4 h-4" />, badge: "Live" },
  ];

  return (
    <aside className="hidden md:flex flex-col w-56 border-r border-border bg-card/50 p-3 justify-between shrink-0 transition-colors">
      <nav className="space-y-1">
        {navItems.map(item => (
          <button
            key={item.id}
            onClick={() => onSelectTab(item.id)}
            className={`w-full flex items-center gap-2.5 px-3 py-2 rounded-xl text-xs font-semibold transition ${
              activeTab === item.id
                ? "bg-primary text-primary-foreground shadow-sm"
                : "text-muted-foreground hover:text-foreground hover:bg-muted/60"
            }`}>
            <span>{item.icon}</span>
            <span className="flex-1 text-left whitespace-nowrap">{item.label}</span>
            {item.badge && (
              <Badge
                variant={item.badge === "Admin" ? "warning" : "success"}
                className="text-[10px] px-1.5 py-0 uppercase">
                {item.badge}
              </Badge>
            )}
          </button>
        ))}
      </nav>

      {/* Tenant Context Footer */}
      <div className="p-3 rounded-xl bg-muted/40 border border-border text-[11px] text-muted-foreground">
        <div className="font-bold text-foreground truncate">{activeOrg?.name || 'Store'}</div>
        <div className="truncate font-mono text-[10px] mt-0.5">{activeOrg?.id || '—'}</div>
      </div>
    </aside>
  );
}

export { Sidebar };
export default Sidebar;
