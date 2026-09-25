// Domain and Application TypeScript Definitions

export type UserRole = 'OWNER' | 'MANAGER' | 'CASHIER' | 'MEMBER';

export interface User {
  id: string;
  email: string;
  name?: string;
  fullName?: string;
  role?: UserRole | string;
  avatarUrl?: string;
  picture?: string;
  createdAt?: string;
}

export interface Organization {
  id: string;
  name: string;
  legalBusinessName?: string;
  slug?: string;
  category?: string;
  gstin?: string | null;
  panNumber?: string | null;
  role?: UserRole | string;
  permissions?: string[];
  createdAt?: string;
}

export type TransactionStatus = 'SUCCESS' | 'COMPLETED' | 'PENDING' | 'FAILED' | 'REFUNDED';
export type TransactionDirection = 'RECEIVED' | 'SENT';

export interface Transaction {
  id: string;
  amount: number;
  currency?: string;
  status: TransactionStatus | string;
  direction?: TransactionDirection | string;
  payerName?: string;
  payerVpa?: string;
  payeeName?: string;
  payeeVpa?: string;
  vpa?: string;
  upiId?: string;
  utr?: string;
  referenceNumber?: string;
  orderId?: string;
  customerPhone?: string;
  description?: string;
  note?: string;
  paymentMode?: string;
  source?: string;
  occurredAt?: string;
  createdAt: string;
}

export interface UpiAccount {
  id: string;
  vpa?: string;
  upiId?: string;
  payeeName?: string;
  accountHolderName?: string;
  bankAccountId?: string | null;
  bankName?: string | null;
  accountNumberMasked?: string | null;
  merchantCategoryCode?: string;
  transactionCount?: number;
  isActive?: boolean;
  status?: string;
  isDefault?: boolean;
  isPrimary?: boolean;
  qrCodeUrl?: string;
  createdAt?: string;
}

export interface StaffMember {
  id: string;
  name?: string;
  fullName?: string;
  mobileNumber?: string;
  email?: string;
  role: UserRole | string;
  permissions?: string[];
  status: 'ACTIVE' | 'INVITED' | 'INACTIVE' | string;
  invitedAt?: string;
  joinedAt?: string;
}

export interface StaffInvite {
  id: string;
  email: string;
  invitedEmail?: string;
  invitedMobile?: string;
  invitedName?: string;
  role: UserRole | string;
  expiresAt: string;
  token?: string;
}

export interface BankAccount {
  id: string;
  accountHolderName: string;
  accountNumber?: string;
  accountNumberMasked?: string;
  isDefault: boolean;
  ifscCode: string;
  bankName: string;
  accountType?: string;
  isVerified?: boolean;
}

export interface AmountCountSummary {
  amount: number;
  count: number;
}

export interface DashboardStats {
  todayReceived?: AmountCountSummary;
  todaySent?: AmountCountSummary;
  activeUpiCount?: number;
  pendingCount?: number;
  failedCount?: number;
  recentTransactions?: Transaction[];
  totalVolume?: number;
  totalCount?: number;
  successRate?: number;
  activeDevices?: number;
}

export interface TableColumnDef {
  name: string;
  type?: string;
  isPrimary?: boolean;
}

export type ColumnDef = TableColumnDef;

export interface TableSchemaItem {
  name: string;
  rowCount?: number;
}

export interface TableData {
  rows: Record<string, unknown>[];
  columns: TableColumnDef[];
  total: number;
}

export interface HealthMemory {
  rssMb?: number | string;
  heapUsedMb?: number | string;
  heapTotalMb?: number | string;
}

export interface HealthData {
  status?: string;
  uptimeSeconds?: number;
  timestamp?: string;
  service?: string;
  version?: string;
  nodeVersion?: string;
  platform?: string;
  memory?: HealthMemory;
  tables?: Record<string, number>;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken?: string;
}

export interface AuthResponse {
  tokens: AuthTokens;
  user: User;
  isSetupComplete?: boolean;
  defaultOrg?: Organization | null;
}

export interface AuthState {
  token: string;
  user: User | null;
}

export type Theme = 'light' | 'dark';

// Page Props Interfaces
export interface LoginPageProps {
  onLoginSuccess: (token: string, user: User, defaultOrg?: Organization | null, refreshToken?: string) => void;
  theme: Theme;
  onToggleTheme: () => void;
  pingMs: number | null;
}

export interface OverviewPageProps {
  stats: DashboardStats | null;
  loading?: boolean;
  onOpenNewTxn: () => void;
  onOpenNewUpi: () => void;
  onNavigate: (tab: string) => void;
  onInspectTxn: (action: string, txn: Transaction) => void;
}

export interface TransactionsPageProps {
  transactions: Transaction[];
  loading: boolean;
  onRefresh: () => void;
  search: string;
  onSearchChange: (search: string) => void;
  status: string;
  onStatusChange: (status: string) => void;
  onOpenNewTxn: () => void;
  onSelectTxnAction: (action: string, txn: Transaction) => void;
  onBulkDelete?: (ids: string[]) => Promise<void> | void;
}

export interface UpiPageProps {
  upiAccounts: UpiAccount[];
  canManageUpi?: boolean;
  loading?: boolean;
  onOpenNewUpi: () => void;
  onSelectUpiAction: (upi: UpiAccount, action: string) => void;
  onBulkDelete?: (ids: string[]) => Promise<void> | void;
}

export interface StaffPageProps {
  staffList: StaffMember[];
  invitesList: StaffInvite[];
  canManageStaff?: boolean;
  loading?: boolean;
  onOpenInviteStaff: () => void;
  onSelectStaffAction: (action: string, staff: StaffMember | StaffInvite) => void;
  onBulkDeleteStaff?: (ids: string[]) => Promise<void> | void;
  onBulkDeleteInvites?: (ids: string[]) => Promise<void> | void;
}

export interface AccountsPageProps {
  bankAccounts: BankAccount[];
  canManageAccounts?: boolean;
  loading?: boolean;
  onOpenNewBank: () => void;
  onSelectAccountAction?: (action: string, account: BankAccount) => void;
  onBulkDelete?: (ids: string[]) => Promise<void> | void;
}

export interface ProfilePageProps {
  activeOrg: Organization | null;
  onSaveProfile: (e: React.FormEvent<HTMLFormElement>) => void | Promise<void>;
}

export interface TablesPageProps {
  tables: TableSchemaItem[];
  selectedTable: string;
  onSelectTable: (tableName: string) => void;
  tableData: TableData;
  loading: boolean;
  search: string;
  onSearchChange: (search: string) => void;
  offset?: number;
  onOffsetChange?: (offset: number) => void;
  limit?: number;
  onOpenInsertModal: () => void;
  onSelectRowAction: (row: Record<string, unknown>, tableName: string, columns: TableColumnDef[]) => void;
}

export interface HealthPageProps {
  healthData: HealthData | null;
  pingMs: number | null;
  onRefresh: () => void;
  onResetSeed: () => void | Promise<void>;
}

// Layout & Modal Props Interfaces
export interface NavbarProps {
  activeOrg: Organization | null;
  organizations: Organization[];
  onSelectOrg: (org: Organization) => void;
  pingMs: number | null;
  theme: Theme;
  onToggleTheme: () => void;
  user: User | null;
  onLogout: () => void;
  isSyncing?: boolean;
}

export interface SidebarProps {
  activeTab: string;
  onSelectTab: (tabId: string) => void;
  activeOrg: Organization | null;
}

export interface BottomNavProps {
  activeTab: string;
  onSelectTab: (tabId: string) => void;
  onOpenMoreSheet: () => void;
  activeOrg?: Organization | null;
}

export interface BottomSheetAction {
  label: string;
  onClick: () => void;
  variant?: 'default' | 'destructive';
}

export interface BottomSheetConfig {
  title: string;
  subtitle?: string;
  actions: BottomSheetAction[];
}

export interface BottomSheetProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  subtitle?: string;
  actions?: BottomSheetAction[];
}
