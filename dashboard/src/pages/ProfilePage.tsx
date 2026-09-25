import React, { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardTitle, CardDescription } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { ConfirmDialog } from '@/components/ui/confirm-dialog';
import { IconTrash2, IconShield, IconBuilding } from '@/components/ui/icons';
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
  const [category, setCategory] = useState(activeOrg?.category || 'RETAIL');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

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

      <Card className="p-6">
        <form onSubmit={onSaveProfile} className="space-y-5 text-xs">
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <Input
              label="Store / Business Display Name"
              type="text"
              name="name"
              defaultValue={activeOrg?.name || ''}
              required
              disabled={!canManageOrg || isSaving}
              leftIcon={<IconBuilding className="w-3.5 h-3.5 text-muted-foreground" />}
            />
            <Input
              label="Legal Registered Entity"
              type="text"
              name="legalBusinessName"
              defaultValue={activeOrg?.legalBusinessName || ''}
              disabled={!canManageOrg || isSaving}
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Select
              label="Business Category"
              name="category"
              value={category}
              onChange={setCategory}
              disabled={!canManageOrg || isSaving}
              options={[
                { value: 'RETAIL', label: 'Retail / Kirana', description: 'General stores and supermarkets' },
                { value: 'FOOD', label: 'Restaurant / Food', description: 'Cafes, dining, and bakeries' },
                { value: 'SERVICES', label: 'Services', description: 'Consulting, salon, repair' },
                { value: 'TECH', label: 'Tech / SaaS', description: 'Software and digital services' },
                { value: 'HEALTHCARE', label: 'Healthcare', description: 'Clinics, pharmacies, and labs' },
              ]}
            />

            <Input
              label="GSTIN (Optional)"
              type="text"
              name="gstin"
              defaultValue={activeOrg?.gstin || ''}
              placeholder="22AAAAA0000A1Z5"
              disabled={!canManageOrg || isSaving}
              className="uppercase font-mono"
            />

            <Input
              label="PAN (Optional)"
              type="text"
              name="panNumber"
              defaultValue={activeOrg?.panNumber || ''}
              placeholder="ABCDE1234F"
              disabled={!canManageOrg || isSaving}
              className="uppercase font-mono"
            />
          </div>

          <div className="space-y-1.5">
            <Input
              label="Physical Business Address"
              type="text"
              name="address"
              defaultValue={activeOrg?.address || ''}
              placeholder="Shop #12, Market Complex, MG Road"
              disabled={!canManageOrg || isSaving}
            />
          </div>

          {canManageOrg && (
            <div className="pt-3 flex justify-end border-t border-border/50">
              <Button
                variant="brand"
                type="submit"
                loading={isSaving}
                loadingText="Saving Profile..."
                className="min-w-[130px]">
                Save Profile
              </Button>
            </div>
          )}
        </form>
      </Card>

      {/* Role-Gated Danger Zone: Only OWNER can delete company / organization */}
      {isOwner ? (
        <Card className="p-6 border-red-500/20 bg-red-500/5 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
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
              onClick={() => setShowDeleteConfirm(true)}
              leftIcon={<IconTrash2 className="w-3.5 h-3.5" />}
              className="shrink-0 font-semibold">
              Delete Business
            </Button>
          </div>
        </Card>
      ) : (
        <div className="p-4 rounded-2xl border border-border/60 bg-muted/20 text-muted-foreground flex items-center gap-2.5 text-xs">
          <IconShield className="w-4 h-4 text-brand-500 flex-shrink-0" />
          <span>
            Critical business controls like deleting this organization or transferring ownership are restricted to the <strong>OWNER</strong>.
          </span>
        </div>
      )}

      {/* Confirm Delete Dialog */}
      <ConfirmDialog
        open={showDeleteConfirm}
        onOpenChange={setShowDeleteConfirm}
        title="Delete Organization"
        description={
          <span>
            Are you sure you want to delete <strong>{activeOrg?.name}</strong>? This action cannot be undone and all associated ledger records and settlement accounts will be permanently erased.
          </span>
        }
        confirmText="Delete Permanently"
        variant="danger"
        onConfirm={async () => {
          if (onDeleteOrg) await onDeleteOrg();
        }}
      />
    </div>
  );
}

export default ProfilePage;
