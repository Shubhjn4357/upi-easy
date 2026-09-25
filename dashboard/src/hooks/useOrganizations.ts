import { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { ENV } from '../services/env.service';
import type { Organization } from '../types';

export interface UseOrganizationsProps {
  token: string;
  onDeleted?: () => void;
  showToast: (msg: string, type?: 'success' | 'error' | 'info') => void;
  apiFetch: <T = unknown>(endpoint: string, options?: RequestInit) => Promise<T>;
}

export function useOrganizations({ token, onDeleted, showToast, apiFetch }: UseOrganizationsProps) {
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [activeOrg, setActiveOrg] = useState<Organization | null>(null);
  const [isDeletingOrg, setIsDeletingOrg] = useState<boolean>(false);

  // Active Org Ref to avoid stale closures in API calls
  const activeOrgRef = useRef<Organization | null>(activeOrg);
  useEffect(() => {
    activeOrgRef.current = activeOrg;
  }, [activeOrg]);

  const loadOrganizations = useCallback(
    async (authToken = token) => {
      if (!authToken) return;
      try {
        const res = await fetch(`${ENV.API_BASE_URL}/api/v1/organizations`, {
          headers: {
            Authorization: `Bearer ${authToken}`,
            Accept: 'application/json',
          },
        });
        const contentType = res.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) return;
        const data = await res.json();
        if (data.success && data.organizations?.length > 0) {
          setOrganizations(data.organizations);
          const savedOrgId = localStorage.getItem('upieasy_active_org_id');
          setActiveOrg((prev) => {
            if (prev && data.organizations.some((o: Organization) => o.id === prev.id)) {
              return data.organizations.find((o: Organization) => o.id === prev.id) || prev;
            }
            const def =
              (savedOrgId && data.organizations.find((o: Organization) => o.id === savedOrgId)) ||
              data.organizations[0];
            return def;
          });
        }
      } catch (err) {
        console.error('[Organizations] Failed to load organizations:', err);
      }
    },
    [token]
  );

  const handleSelectOrg = useCallback((org: Organization) => {
    setActiveOrg(org);
    localStorage.setItem('upieasy_active_org_id', org.id);
  }, []);

  const handleDeleteOrg = useCallback(async () => {
    if (!activeOrg) return;
    setIsDeletingOrg(true);
    try {
      await apiFetch(`/api/v1/organizations/${activeOrg.id}`, { method: 'DELETE' });
      showToast('Organization deleted successfully');
      localStorage.removeItem('upieasy_active_org_id');
      await loadOrganizations();
      onDeleted?.();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error deleting organization';
      showToast(msg, 'error');
    } finally {
      setIsDeletingOrg(false);
    }
  }, [activeOrg, apiFetch, showToast, loadOrganizations, onDeleted]);

  // Role and Permission Matrix
  const role = activeOrg?.role || 'OWNER';
  const permissions = useMemo(
    () => activeOrg?.permissions || (role === 'OWNER' ? ['*'] : []),
    [activeOrg?.permissions, role]
  );
  const isOwner = role === 'OWNER' || permissions.includes('*');
  const canManageOrg = isOwner || permissions.includes('organization.manage') || role === 'MANAGER';
  const canManageStaff = isOwner || permissions.includes('staff.manage') || role === 'MANAGER';
  const canManageUpi = isOwner || permissions.includes('upi.manage') || role === 'MANAGER';
  const canManageAccounts = isOwner || permissions.includes('accounts.manage') || role === 'MANAGER';

  return {
    organizations,
    setOrganizations,
    activeOrg,
    setActiveOrg,
    activeOrgRef,
    isDeletingOrg,
    loadOrganizations,
    handleSelectOrg,
    handleDeleteOrg,
    role,
    permissions,
    isOwner,
    canManageOrg,
    canManageStaff,
    canManageUpi,
    canManageAccounts,
  };
}

export default useOrganizations;
