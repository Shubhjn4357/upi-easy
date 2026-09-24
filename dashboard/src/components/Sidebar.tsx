import React, { useState, useEffect, useRef } from 'react';
import { Badge } from '@/components/ui/badge';
import {
  IconActivity,
  IconWallet,
  IconUsers,
  IconCreditCard,
  IconQrCode,
  IconSettings,
  IconHeartPulse,
  IconDatabase,
  IconChevronRight,
  IconChevronLeft,
} from '@/components/ui/icons';
import type { SidebarProps } from '@/types';

interface NavItem {
  id: string;
  label: string;
  icon: React.ReactNode;
  badge?: string;
}

// Collapsible & Draggable Desktop Side Drawer matching Mobile App UI aesthetics
export function Sidebar({ activeTab, onSelectTab, activeOrg }: SidebarProps) {
  const [isCollapsed, setIsCollapsed] = useState<boolean>(() => {
    return localStorage.getItem('upieasy_sidebar_collapsed') === 'true';
  });

  const [width, setWidth] = useState<number>(() => {
    const saved = localStorage.getItem('upieasy_sidebar_width');
    return saved ? Math.max(180, Math.min(380, Number(saved))) : 240;
  });

  const [isDragging, setIsDragging] = useState<boolean>(false);
  const sidebarRef = useRef<HTMLElement>(null);

  const toggleCollapse = () => {
    setIsCollapsed((prev) => {
      const next = !prev;
      localStorage.setItem('upieasy_sidebar_collapsed', String(next));
      return next;
    });
  };

  // Draggable Drawer Handle logic
  useEffect(() => {
    const handleMouseMove = (e: MouseEvent) => {
      if (!isDragging) return;
      const newWidth = Math.max(180, Math.min(400, e.clientX));
      setWidth(newWidth);
      localStorage.setItem('upieasy_sidebar_width', String(newWidth));
    };

    const handleMouseUp = () => {
      if (isDragging) {
        setIsDragging(false);
      }
    };

    if (isDragging) {
      window.addEventListener('mousemove', handleMouseMove);
      window.addEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
    }

    return () => {
      window.removeEventListener('mousemove', handleMouseMove);
      window.removeEventListener('mouseup', handleMouseUp);
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, [isDragging]);

  const role = activeOrg?.role || 'OWNER';
  const permissions = activeOrg?.permissions || (role === 'OWNER' ? ['*'] : []);
  const isOwner = role === 'OWNER' || permissions.includes('*');
  const canReadStaff = isOwner || permissions.includes('staff.read') || role === 'MANAGER';
  const canReadAccounts = isOwner || permissions.includes('accounts.read') || role === 'MANAGER' || role === 'ACCOUNTANT';
  const canReadUpi = isOwner || permissions.includes('upi.read') || role === 'MANAGER' || role === 'CASHIER';
  const canReadTransactions = isOwner || permissions.includes('transactions.read') || role === 'MANAGER' || role === 'CASHIER' || role === 'ACCOUNTANT';

  const allNavItems: (NavItem & { visible: boolean })[] = [
    { id: 'overview', label: 'Overview', icon: <IconActivity className="w-4 h-4 shrink-0" />, visible: true },
    { id: 'transactions', label: 'Transactions', icon: <IconCreditCard className="w-4 h-4 shrink-0" />, visible: canReadTransactions },
    { id: 'upi', label: 'UPI & QR Codes', icon: <IconQrCode className="w-4 h-4 shrink-0" />, visible: canReadUpi },
    { id: 'staff', label: 'Staff & Roles', icon: <IconUsers className="w-4 h-4 shrink-0" />, visible: canReadStaff },
    { id: 'accounts', label: 'Bank Accounts', icon: <IconWallet className="w-4 h-4 shrink-0" />, visible: canReadAccounts },
    { id: 'orgs', label: 'Business Profile', icon: <IconSettings className="w-4 h-4 shrink-0" />, visible: true },
    { id: 'tables', label: 'Table Explorer', icon: <IconDatabase className="w-4 h-4 shrink-0" />, badge: 'Admin', visible: isOwner },
    { id: 'health', label: 'System Health', icon: <IconHeartPulse className="w-4 h-4 shrink-0" />, badge: 'Live', visible: isOwner },
  ];

  const visibleNavItems = allNavItems.filter((i) => i.visible);

  return (
    <aside
      ref={sidebarRef}
      style={{ width: isCollapsed ? 68 : width }}
      className={`hidden md:flex flex-col relative border-r border-border/70 backdrop-blur-xl bg-card/80 p-3 justify-between shrink-0 transition-all duration-200 select-none ${
        isDragging ? 'transition-none shadow-2xl' : ''
      }`}>
      {/* Top Header & Collapse Toggle */}
      <div>
        <div className="flex items-center justify-between pb-3 mb-2 border-b border-border/50">
          {!isCollapsed && (
            <div className="flex items-center gap-2 pl-1.5 overflow-hidden">
              <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse" />
              <span className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground truncate">
                Navigation
              </span>
            </div>
          )}
          <button
            onClick={toggleCollapse}
            title={isCollapsed ? 'Expand sidebar' : 'Collapse sidebar'}
            className={`p-1.5 rounded-lg text-muted-foreground hover:text-foreground hover:bg-muted/80 transition ${
              isCollapsed ? 'mx-auto' : ''
            }`}>
            {isCollapsed ? <IconChevronRight className="w-4 h-4" /> : <IconChevronLeft className="w-4 h-4" />}
          </button>
        </div>

        {/* Nav Links */}
        <nav className="space-y-1">
          {visibleNavItems.map((item) => {
            const isActive = activeTab === item.id;
            return (
              <button
                key={item.id}
                onClick={() => onSelectTab(item.id)}
                title={isCollapsed ? item.label : undefined}
                className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-xl text-xs font-semibold transition-all relative ${
                  isActive
                    ? 'bg-primary text-primary-foreground shadow-md shadow-primary/25 scale-[1.01]'
                    : 'text-muted-foreground hover:text-foreground hover:bg-muted/60'
                } ${isCollapsed ? 'justify-center px-0' : ''}`}>
                <span className={isActive ? 'text-primary-foreground' : 'text-muted-foreground'}>
                  {item.icon}
                </span>
                {!isCollapsed && (
                  <>
                    <span className="flex-1 text-left whitespace-nowrap truncate">{item.label}</span>
                    {item.badge && (
                      <Badge
                        variant={item.badge === 'Admin' ? 'warning' : 'success'}
                        className="text-[10px] px-1.5 py-0 uppercase">
                        {item.badge}
                      </Badge>
                    )}
                  </>
                )}
                {isCollapsed && isActive && (
                  <span className="absolute right-1 w-1.5 h-1.5 rounded-full bg-primary-foreground" />
                )}
              </button>
            );
          })}
        </nav>
      </div>

      {/* Tenant Context Footer */}
      <div className="mt-4 pt-3 border-t border-border/50">
        {!isCollapsed ? (
          <div className="p-3 rounded-2xl bg-muted/40 border border-border/60 text-[11px] text-muted-foreground space-y-1">
            <div className="flex items-center justify-between">
              <span className="font-bold text-foreground truncate">{activeOrg?.name || 'Store'}</span>
              <span className="text-[9px] font-bold px-1.5 py-0.5 rounded-md bg-primary/10 text-primary uppercase">
                {role}
              </span>
            </div>
            <div className="truncate font-mono text-[10px] text-muted-foreground/80">{activeOrg?.id || '—'}</div>
          </div>
        ) : (
          <div
            title={`${activeOrg?.name || 'Store'} (${role})`}
            className="w-10 h-10 mx-auto rounded-xl bg-muted/50 border border-border/60 flex items-center justify-center text-xs font-bold text-foreground">
            {(activeOrg?.name?.[0] || 'M').toUpperCase()}
          </div>
        )}
      </div>

      {/* Draggable Resizer Handle on Right Edge (when not collapsed) */}
      {!isCollapsed && (
        <div
          onMouseDown={(e) => {
            e.preventDefault();
            setIsDragging(true);
          }}
          className="absolute top-0 right-0 w-2 h-full cursor-col-resize hover:bg-primary/30 active:bg-primary/50 transition-colors flex items-center justify-center group"
          title="Drag to resize sidebar width">
          <div className="w-0.5 h-8 rounded-full bg-border group-hover:bg-primary transition-colors" />
        </div>
      )}
    </aside>
  );
}

export default Sidebar;
