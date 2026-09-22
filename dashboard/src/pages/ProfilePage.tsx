import React from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle, CardDescription } from '@/components/ui/card';
import { Input, Label } from '@/components/ui/input';
import { IconTrash2, IconShield } from '@/components/ui/icons';
import type { Organization } from '@/types';

export interface ProfilePageProps {
  activeOrg: Organization | null;
  isSaving?: boolean;
  isOwner?: boolean;
  canManageOrg?: boolean;
  onSaveProfile: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
  onDeleteOrg?: () => void | Promise<void>;
}

// Business Profile & Organization Settings Page with Role-Based Danger Zone
export function ProfilePage({
  activeOrg,
  isSaving = false,
  isOwner = false,
  canManageOrg = true,
  onSaveProfile,
  onDeleteOrg,
}: ProfilePageProps) {
  return (
    <div className="space-y-6 animate-fade-in max-w-4xl mx-auto pb-12">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-foreground">
            Business Profile & Access
          </h2>
          <p className="text-xs text-muted-foreground mt-0.5">
            Manage legal entity registration, store metadata, and role permissions
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-xs text-muted-foreground">Your Role:</span>
          <span className="px-2.5 py-1 rounded-lg bg-brand-500/10 border border-brand-500/20 text-brand-600 dark:text-cyan-400 font-bold text-xs uppercase">
            {activeOrg?.role || 'MEMBER'}
          </span>
        </div>
      </div>

      <Card className="p-6 space-y-5">
        <form onSubmit={onSaveProfile} className="space-y-4 text-xs">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-1.5">
              <Label>Trading Store Name</Label>
              <Input
                type="text"
                name="name"
                defaultValue={activeOrg?.name || ''}
                required
                disabled={!canManageOrg || isSaving}
              />
            </div>
            <div className="space-y-1.5">
              <Label>Legal Registered Entity</Label>
              <Input
                type="text"
                name="legalBusinessName"
                defaultValue={activeOrg?.legalBusinessName || ''}
                disabled={!canManageOrg || isSaving}
              />
            </div>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <div className="space-y-1.5">
              <Label>Category</Label>
              <select
                name="category"
                defaultValue={activeOrg?.category || 'RETAIL'}
                disabled={!canManageOrg || isSaving}
                className="w-full h-9 bg-background border border-input rounded-xl px-3 text-xs text-foreground focus:outline-none focus:ring-1 focus:ring-ring disabled:opacity-60">
                <option value="RETAIL" className="bg-card text-card-foreground">Retail / Kirana</option>
                <option value="FOOD" className="bg-card text-card-foreground">Restaurant / Food</option>
                <option value="SERVICES" className="bg-card text-card-foreground">Services</option>
                <option value="TECH" className="bg-card text-card-foreground">Tech / SaaS</option>
                <option value="HEALTHCARE" className="bg-card text-card-foreground">Healthcare</option>
              </select>
            </div>
            <div className="space-y-1.5">
              <Label>GSTIN (Optional)</Label>
              <Input
                type="text"
                name="gstin"
                defaultValue={activeOrg?.gstin || ''}
                placeholder="22AAAAA0000A1Z5"
                disabled={!canManageOrg || isSaving}
                className="uppercase font-mono"
              />
            </div>
            <div className="space-y-1.5">
              <Label>PAN (Optional)</Label>
              <Input
                type="text"
                name="panNumber"
                defaultValue={activeOrg?.panNumber || ''}
                placeholder="ABCDE1234F"
                disabled={!canManageOrg || isSaving}
                className="uppercase font-mono"
              />
            </div>
          </div>

          {canManageOrg && (
            <div className="pt-4 border-t border-border flex justify-end">
              <Button
                type="submit"
                variant="brand"
                size="lg"
                disabled={isSaving}
                className="rounded-xl font-bold gap-2 min-w-[140px]">
                {isSaving ? (
                  <>
                    <div className="w-3.5 h-3.5 rounded-full border-2 border-white border-t-transparent animate-spin" />
                    <span>Saving...</span>
                  </>
                ) : (
                  'Save Profile'
                )}
              </Button>
            </div>
          )}
        </form>
      </Card>

      {/* Role-Gated Danger Zone: Only OWNER can delete company / organization */}
      {isOwner ? (
        <Card className="p-6 border-red-500/20 bg-red-500/5 space-y-4">
          <div className="flex items-center justify-between">
            <div>
              <CardTitle className="text-sm font-bold text-red-600 dark:text-red-400 flex items-center gap-1.5">
                <IconTrash2 className="w-4 h-4" />
                <span>Delete Organization (Danger Zone)</span>
              </CardTitle>
              <CardDescription className="text-xs text-muted-foreground mt-1">
                Permanently deletes this business profile, linked UPI accounts, and historical transaction records.
              </CardDescription>
            </div>
            <Button
              variant="destructive"
              size="sm"
              onClick={onDeleteOrg}
              className="rounded-xl font-semibold gap-1.5">
              <IconTrash2 className="w-3.5 h-3.5" />
              <span>Delete Business</span>
            </Button>
          </div>
        </Card>
      ) : (
        <div className="p-4 rounded-xl border border-border/60 bg-muted/20 text-muted-foreground flex items-center gap-2.5 text-xs">
          <IconShield className="w-4 h-4 text-brand-500 flex-shrink-0" />
          <span>
            Critical business controls like deleting this organization or transferring ownership are restricted to the <strong>OWNER</strong>.
          </span>
        </div>
      )}
    </div>
  );
}

export default ProfilePage;
