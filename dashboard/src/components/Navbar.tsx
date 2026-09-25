import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Select } from '@/components/ui/select';
import { Avatar, AvatarFallback, AvatarImage, Separator } from '@/components/ui/components';
import { IconSun, IconMoon, IconLogOut, IconMenu, IconX } from '@/components/ui/icons';
import type { NavbarProps } from '@/types';

// Global Navigation Bar with Theme Switcher, Real-time Sync & Organization Selector
export function Navbar({
  user,
  organizations,
  activeOrg,
  onSelectOrg,
  onLogout,
  theme,
  onToggleTheme,
  pingMs,
  isSyncing = false,
}: NavbarProps) {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  return (
    <header className="border-b border-border bg-card/90 backdrop-blur sticky top-0 z-40 transition-colors">
      <div className="h-16 px-4 sm:px-6 flex items-center justify-between">
        {/* Left Side: Brand & Org Switcher (Desktop) */}
        <div className="flex items-center gap-3 sm:gap-4">
          {/* Brand Logo */}
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-gradient-to-tr from-brand-600 to-indigo-500 flex items-center justify-center font-black text-white text-xs shadow-md shadow-brand-500/20">
              UPI
            </div>
            <span className="font-extrabold text-foreground text-base tracking-tight">
              UPI-Easy
            </span>
          </div>

          <Separator orientation="vertical" className="h-5 hidden md:block" />

          {/* Desktop Multi-Tenant Organization Switcher */}
          {organizations && organizations.length > 0 && (
            <div className="hidden md:block w-56">
              <Select
                value={activeOrg?.id || ''}
                onChange={(val) => {
                  const selected = organizations.find((o) => o.id === val);
                  if (selected) onSelectOrg(selected);
                }}
                triggerClassName="bg-secondary/70 h-8 text-xs font-semibold rounded-xl"
                options={organizations.map((org) => ({
                  value: org.id,
                  label: org.name,
                  description: `Role: ${org.role || 'OWNER'}`,
                }))}
              />
            </div>
          )}

          {/* Mobile Active Org Badge */}
          {activeOrg && (
            <div className="md:hidden max-w-[130px] truncate text-xs font-semibold px-2 py-0.5 rounded-lg bg-secondary text-secondary-foreground border border-border">
              {activeOrg.name}
            </div>
          )}
        </div>

        {/* Desktop Controls (hidden on mobile) */}
        <div className="hidden md:flex items-center gap-2 sm:gap-3">
          {/* Real-time Live Sync Indicator */}
          <div className="flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-[11px] font-mono">
            <span
              className={`w-1.5 h-1.5 rounded-full bg-emerald-500 ${isSyncing ? 'animate-ping' : 'animate-pulse'}`}></span>
            <span>{pingMs ? `${pingMs}ms` : 'Ready'}</span>
          </div>

          {/* Theme Toggle Button (Light / Dark) */}
          <Button
            variant="outline"
            size="sm"
            onClick={onToggleTheme}
            title={`Switch to ${theme === 'dark' ? 'Light' : 'Dark'} Mode`}
            className="rounded-full px-2.5 gap-1.5 text-xs">
            {theme === 'dark' ? (
              <>
                <IconSun className="w-3.5 h-3.5 text-amber-400" />
                
              </>
            ) : (
              <>
                <IconMoon className="w-3.5 h-3.5 text-indigo-500" />
                
              </>
            )}
          </Button>

          {/* User Profile Pill & Logout */}
          {user && (
            <div className="flex items-center gap-2 pl-1">
              <Avatar className="w-7 h-7">
                <AvatarImage
                  src={
                    user.avatarUrl ||
                    user.picture ||
                    `https://ui-avatars.com/api/?name=${encodeURIComponent(user.fullName || user.name || 'User')}&background=4F46E5&color=fff`
                  }
                  alt={user.fullName || user.name}
                />
                <AvatarFallback>{(user.fullName || user.name || 'U')[0]}</AvatarFallback>
              </Avatar>
              <div className="hidden lg:block text-left">
                <div className="text-xs font-bold text-foreground leading-tight">
                  {user.fullName || user.name}
                </div>
                <div className="text-[10px] text-muted-foreground leading-tight truncate max-w-[120px]">
                  {user.email}
                </div>
              </div>
              <Button
                variant="destructive"
                size="sm"
                onClick={onLogout}
                title="Sign out"
                className="h-7 px-2.5 rounded-full text-xs gap-1 font-semibold">
                <IconLogOut className="w-3 h-3" />
                
              </Button>
            </div>
          )}
        </div>

        {/* Mobile Menu Hamburger Button */}
        <div className="flex md:hidden items-center gap-2">
          {/* Quick theme toggle on mobile */}
          <Button
            variant="ghost"
            size="sm"
            onClick={onToggleTheme}
            className="w-8 h-8 p-0 rounded-lg"
            title="Toggle theme">
            {theme === 'dark' ? (
              <IconSun className="w-4 h-4 text-amber-400" />
            ) : (
              <IconMoon className="w-4 h-4 text-indigo-500" />
            )}
          </Button>

          <Button
            variant="outline"
            size="sm"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label="Toggle menu"
            className="w-9 h-9 p-0 rounded-xl">
            {mobileMenuOpen ? (
              <IconX className="w-5 h-5 text-foreground" />
            ) : (
              <IconMenu className="w-5 h-5 text-foreground" />
            )}
          </Button>
        </div>
      </div>

      {/* Mobile Dropdown Menu Drawer */}
      {mobileMenuOpen && (
        <div className="md:hidden border-t border-border bg-card/95 backdrop-blur px-4 py-4 space-y-4 shadow-xl animate-in slide-in-from-top-2 duration-150">
          {/* User profile card */}
          {user && (
            <div className="flex items-center gap-3 p-3 rounded-xl bg-secondary/50 border border-border">
              <Avatar className="w-9 h-9">
                <AvatarImage
                  src={
                    user.avatarUrl ||
                    user.picture ||
                    `https://ui-avatars.com/api/?name=${encodeURIComponent(user.fullName || user.name || 'User')}&background=4F46E5&color=fff`
                  }
                  alt={user.fullName || user.name}
                />
                <AvatarFallback>{(user.fullName || user.name || 'U')[0]}</AvatarFallback>
              </Avatar>
              <div className="flex-1 min-w-0">
                <div className="text-sm font-bold text-foreground truncate">
                  {user.fullName || user.name}
                </div>
                <div className="text-xs text-muted-foreground truncate">{user.email}</div>
              </div>
            </div>
          )}

          {/* Org Selector on Mobile */}
          {organizations && organizations.length > 0 && (
            <div className="space-y-1.5">
              <label className="text-xs font-semibold text-muted-foreground uppercase tracking-wider block">
                Active Organization
              </label>
              <Select
                value={activeOrg?.id || ''}
                onChange={(val) => {
                  const selected = organizations.find((o) => o.id === val);
                  if (selected) {
                    onSelectOrg(selected);
                    setMobileMenuOpen(false);
                  }
                }}
                triggerClassName="bg-secondary/70 h-10 text-xs font-semibold rounded-xl"
                options={organizations.map((org) => ({
                  value: org.id,
                  label: org.name,
                  description: `Role: ${org.role || 'OWNER'}`,
                }))}
              />
            </div>
          )}

          {/* Telemetry & Actions Row */}
          <div className="flex items-center justify-between pt-1">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20 text-emerald-600 dark:text-emerald-400 text-xs font-mono">
              <span
                className={`w-2 h-2 rounded-full bg-emerald-500 ${isSyncing ? 'animate-ping' : 'animate-pulse'}`}></span>
              <span>{pingMs ? `${pingMs}ms` : 'Ready'}</span>
            </div>

            <Button
              variant="outline"
              size="sm"
              onClick={onToggleTheme}
              className="rounded-xl px-3 text-xs gap-1.5">
              {theme === 'dark' ? (
                <>
                  <IconSun className="w-3.5 h-3.5 text-amber-400" />
                  
                </>
              ) : (
                <>
                  <IconMoon className="w-3.5 h-3.5 text-indigo-500" />
                  
                </>
              )}
            </Button>
            <div className="flex items-center gap-2">
          {/* Logout Button */}
          {user && (
            <Button
              variant="destructive"
              className="w-full rounded-xl gap-2 font-semibold text-xs py-2 mt-2"
              onClick={() => {
                setMobileMenuOpen(false);
                onLogout();
              }}>
              <IconLogOut className="w-4 h-4" />
              Sign Out
            </Button>
          )}
          </div>
          </div>

        </div>
      )}
    </header>
  );
}

export default Navbar;
