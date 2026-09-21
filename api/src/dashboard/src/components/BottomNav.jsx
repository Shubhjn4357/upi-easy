// Mobile Bottom Floating Navigation Tab Bar using shadcn tokens and Lucide icons
function BottomNav({ activeTab, onSelectTab, onOpenMoreSheet }) {
  const primaryTabs = [
    { id: "overview", label: "Overview", icon: <IconActivity className="w-5 h-5" /> },
    { id: "transactions", label: "Ledger", icon: <IconCreditCard className="w-5 h-5" /> },
    { id: "upi", label: "UPI/QR", icon: <IconQrCode className="w-5 h-5" /> },
    { id: "staff", label: "Staff", icon: <IconUsers className="w-5 h-5" /> },
  ];

  return (
    <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 h-16 bg-card/90 backdrop-blur border-t border-border px-3 flex items-center justify-around shadow-lg transition-colors">
      {primaryTabs.map(tab => (
        <button
          key={tab.id}
          onClick={() => onSelectTab(tab.id)}
          className={`flex flex-col items-center justify-center py-1 px-2.5 rounded-xl transition ${
            activeTab === tab.id
              ? "text-primary font-bold"
              : "text-muted-foreground hover:text-foreground"
          }`}>
          {tab.icon}
          <span className="text-[10px] mt-1">{tab.label}</span>
        </button>
      ))}

      {/* More Hub Button */}
      <button
        onClick={onOpenMoreSheet}
        className="flex flex-col items-center justify-center py-1 px-2.5 rounded-xl text-muted-foreground hover:text-foreground transition">
        <IconSettings className="w-5 h-5" />
        <span className="text-[10px] mt-1">More</span>
      </button>
    </nav>
  );
}

export { BottomNav };
export default BottomNav;
