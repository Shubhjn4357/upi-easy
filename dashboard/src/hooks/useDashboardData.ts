import { useState, useEffect, useCallback } from 'react';
import type {
  Organization,
  DashboardStats,
  Transaction,
  UpiAccount,
  StaffMember,
  StaffInvite,
  BankAccount,
  TableSchemaItem,
  TableData,
  HealthData,
} from '../types';

interface DashboardOverviewResponse {
  success: boolean;
  dashboard: DashboardStats;
}

interface TransactionsApiResponse {
  success: boolean;
  transactions?: Transaction[];
  data?: Transaction[];
}

interface UpiApiResponse {
  success: boolean;
  upiAccounts?: UpiAccount[];
}

interface StaffApiResponse {
  success: boolean;
  staff?: StaffMember[];
  invites?: StaffInvite[];
}

interface AccountsApiResponse {
  success: boolean;
  accounts?: BankAccount[];
}

interface AdminTablesApiResponse {
  success: boolean;
  tables?: TableSchemaItem[];
}

interface AdminTableDataApiResponse {
  success: boolean;
  rows?: Record<string, unknown>[];
  columns?: TableData['columns'];
  total?: number;
}

export interface UseDashboardDataProps {
  token: string;
  activeOrg: Organization | null;
  activeTab: string;
  apiFetch: <T = unknown>(endpoint: string, options?: RequestInit) => Promise<T>;
  showToast: (msg: string, type?: 'success' | 'error' | 'info') => void;
  pingMs: number | null;
  setPingMs: (ms: number | null) => void;
}

export function useDashboardData({
  token,
  activeOrg,
  activeTab,
  apiFetch,
  showToast,
  pingMs,
  setPingMs,
}: UseDashboardDataProps) {
  // Telemetry & Live Sync Status
  const [isSyncing, setIsSyncing] = useState<boolean>(false);

  // Overview
  const [dashboardStats, setDashboardStats] = useState<DashboardStats | null>(null);
  const [overviewLoading, setOverviewLoading] = useState<boolean>(false);

  // Transactions
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [txnLoading, setTxnLoading] = useState<boolean>(false);
  const [txnSearch, setTxnSearch] = useState<string>('');
  const [txnStatus, setTxnStatus] = useState<string>('');

  // UPI Accounts
  const [upiAccounts, setUpiAccounts] = useState<UpiAccount[]>([]);
  const [upiLoading, setUpiLoading] = useState<boolean>(false);

  // Staff & Invites
  const [staffList, setStaffList] = useState<StaffMember[]>([]);
  const [invitesList, setInvitesList] = useState<StaffInvite[]>([]);
  const [staffLoading, setStaffLoading] = useState<boolean>(false);

  // Bank Accounts
  const [bankAccounts, setBankAccounts] = useState<BankAccount[]>([]);
  const [accountsLoading, setAccountsLoading] = useState<boolean>(false);

  // Admin Tables
  const [adminTables, setAdminTables] = useState<TableSchemaItem[]>([]);
  const [selectedTable, setSelectedTable] = useState<string>('transactions');
  const [tableData, setTableData] = useState<TableData>({ rows: [], columns: [], total: 0 });
  const [tableLoading, setTableLoading] = useState<boolean>(false);
  const [tableSearch, setTableSearch] = useState<string>('');
  const [tableOffset, setTableOffset] = useState<number>(0);

  // System Health
  const [healthData, setHealthData] = useState<HealthData | null>(null);

  // Loaders
  const loadOverview = useCallback(
    async (silent = false) => {
      if (!activeOrg) return;
      if (!silent) setOverviewLoading(true);
      try {
        const data = await apiFetch<DashboardOverviewResponse>(`/api/v1/organizations/${activeOrg.id}/dashboard`);
        if (data.success) setDashboardStats(data.dashboard);
      } catch {
      } finally {
        if (!silent) setOverviewLoading(false);
      }
    },
    [activeOrg, apiFetch]
  );

  const loadTransactions = useCallback(
    async (silent = false) => {
      if (!activeOrg) return;
      if (!silent) setTxnLoading(true);
      try {
        let url = `/api/v1/organizations/${activeOrg.id}/transactions?limit=50`;
        if (txnStatus) url += `&status=${txnStatus}`;
        if (txnSearch) url += `&search=${encodeURIComponent(txnSearch)}`;
        const data = await apiFetch<TransactionsApiResponse>(url);
        if (data.success) {
          const list = data.transactions || data.data || [];
          setTransactions(list);
        }
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Error loading ledger';
        if (!silent) showToast(msg, 'error');
      } finally {
        if (!silent) setTxnLoading(false);
      }
    },
    [activeOrg, apiFetch, txnStatus, txnSearch, showToast]
  );

  const loadUpi = useCallback(
    async (silent = false) => {
      if (!activeOrg) return;
      if (!silent) setUpiLoading(true);
      try {
        const data = await apiFetch<UpiApiResponse>(`/api/v1/organizations/${activeOrg.id}/upi`);
        if (data.success) setUpiAccounts(data.upiAccounts || []);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Error loading UPI';
        showToast(msg, 'error');
      } finally {
        if (!silent) setUpiLoading(false);
      }
    },
    [activeOrg, apiFetch, showToast]
  );

  const loadStaff = useCallback(
    async (silent = false) => {
      if (!activeOrg) return;
      if (!silent) setStaffLoading(true);
      try {
        const [staffRes, invitesRes] = await Promise.all([
          apiFetch<StaffApiResponse>(`/api/v1/organizations/${activeOrg.id}/staff`),
          apiFetch<StaffApiResponse>(`/api/v1/organizations/${activeOrg.id}/invites`),
        ]);
        if (staffRes.success) setStaffList(staffRes.staff || []);
        if (invitesRes.success) setInvitesList(invitesRes.invites || []);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Error loading staff';
        showToast(msg, 'error');
      } finally {
        if (!silent) setStaffLoading(false);
      }
    },
    [activeOrg, apiFetch, showToast]
  );

  const loadAccounts = useCallback(
    async (silent = false) => {
      if (!activeOrg) return;
      if (!silent) setAccountsLoading(true);
      try {
        const data = await apiFetch<AccountsApiResponse>(`/api/v1/organizations/${activeOrg.id}/accounts`);
        if (data.success) setBankAccounts(data.accounts || []);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : 'Error loading accounts';
        showToast(msg, 'error');
      } finally {
        if (!silent) setAccountsLoading(false);
      }
    },
    [activeOrg, apiFetch, showToast]
  );

  const loadTables = useCallback(async () => {
    try {
      const data = await apiFetch<AdminTablesApiResponse>('/api/v1/admin/tables');
      if (data.success) {
        const fetchedTables = data.tables || [];
        setAdminTables(fetchedTables);
        if (fetchedTables.length > 0) {
          if (!selectedTable || !fetchedTables.some((t: TableSchemaItem) => t.name === selectedTable)) {
            setSelectedTable(fetchedTables[0].name);
          }
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading tables';
      showToast(msg, 'error');
    }
  }, [apiFetch, selectedTable, showToast]);

  const loadTableData = useCallback(async () => {
    if (!selectedTable) return;
    setTableLoading(true);
    try {
      let url = `/api/v1/admin/tables/${selectedTable}?limit=25&offset=${tableOffset}`;
      if (tableSearch) url += `&search=${encodeURIComponent(tableSearch)}`;
      const data = await apiFetch<AdminTableDataApiResponse>(url);
      if (data.success) {
        setTableData({
          rows: data.rows || [],
          columns: data.columns || [],
          total: data.total || 0,
        });
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading table data';
      showToast(msg, 'error');
    } finally {
      setTableLoading(false);
    }
  }, [apiFetch, selectedTable, tableOffset, tableSearch, showToast]);

  const loadHealth = useCallback(async () => {
    try {
      const data = await apiFetch<HealthData>('/api/v1/admin/health');
      setHealthData(data);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Error loading health';
      showToast(msg, 'error');
    }
  }, [apiFetch, showToast]);

  // Tab change sync
  useEffect(() => {
    if (!token || !activeOrg) return;
    if (activeTab === 'overview') loadOverview();
    else if (activeTab === 'transactions') loadTransactions();
    else if (activeTab === 'upi') loadUpi();
    else if (activeTab === 'staff') loadStaff();
    else if (activeTab === 'accounts') loadAccounts();
    else if (activeTab === 'tables') loadTables();
    else if (activeTab === 'health') loadHealth();
  }, [token, activeOrg, activeTab, loadOverview, loadTransactions, loadUpi, loadStaff, loadAccounts, loadTables, loadHealth]);

  // Admin Table query changes
  useEffect(() => {
    if (activeTab === 'tables' && selectedTable) {
      loadTableData();
    }
  }, [activeTab, selectedTable, tableSearch, tableOffset, loadTableData]);

  // Real-Time Periodic Live Sync (every 5 seconds when visible)
  useEffect(() => {
    if (!token || !activeOrg) return;
    let isMounted = true;
    const interval = setInterval(async () => {
      if (document.visibilityState !== 'visible') return;
      try {
        setIsSyncing(true);
        if (activeTab === 'overview') await loadOverview(true);
        else if (activeTab === 'transactions') await loadTransactions(true);
        else if (activeTab === 'health') await loadHealth();
      } catch {
      } finally {
        if (isMounted) setIsSyncing(false);
      }
    }, 5000);

    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, [token, activeOrg, activeTab, loadOverview, loadTransactions, loadHealth]);

  // Window Focus Auto-Sync
  useEffect(() => {
    const onFocus = () => {
      if (token && activeOrg) {
        if (activeTab === 'overview') loadOverview(true);
        else if (activeTab === 'transactions') loadTransactions(true);
        else if (activeTab === 'health') loadHealth();
      }
    };
    window.addEventListener('focus', onFocus);
    return () => window.removeEventListener('focus', onFocus);
  }, [token, activeOrg, activeTab, loadOverview, loadTransactions, loadHealth]);

  return {
    isSyncing,
    pingMs,
    setPingMs,
    // Overview
    dashboardStats,
    overviewLoading,
    loadOverview,
    // Transactions
    transactions,
    txnLoading,
    txnSearch,
    setTxnSearch,
    txnStatus,
    setTxnStatus,
    loadTransactions,
    // UPI
    upiAccounts,
    upiLoading,
    loadUpi,
    // Staff
    staffList,
    invitesList,
    staffLoading,
    loadStaff,
    // Accounts
    bankAccounts,
    accountsLoading,
    loadAccounts,
    // Tables
    adminTables,
    selectedTable,
    setSelectedTable,
    tableData,
    tableLoading,
    tableSearch,
    setTableSearch,
    tableOffset,
    setTableOffset,
    loadTables,
    loadTableData,
    // Health
    healthData,
    loadHealth,
  };
}

export default useDashboardData;
