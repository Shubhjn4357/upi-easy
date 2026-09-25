import React, { useState } from 'react';
import RecordPaymentModal from './RecordPaymentModal';
import UpiAccountModal from './UpiAccountModal';
import QrCodeModal from './QrCodeModal';
import StaffModal from './StaffModal';
import BankAccountModal from './BankAccountModal';
import TableRowModal from './TableRowModal';
import TransactionDetailModal from './TransactionDetailModal';

import type {
  Organization,
  Transaction,
  UpiAccount,
  StaffMember,
  TableData,
  BankAccount,
} from '../../types';

export interface AppModalsProps {
  activeOrg: Organization | null;
  upiAccounts: UpiAccount[];
  selectedTable: string;
  tableData: TableData;
  apiFetch: <T = any>(endpoint: string, options?: any) => Promise<T>;
  showToast: (msg: string, type?: 'success' | 'error' | 'info') => void;

  // Modal display states
  showNewTxnModal: boolean;
  setShowNewTxnModal: (open: boolean) => void;

  showUpiModal: UpiAccount | Record<string, never> | null;
  setShowUpiModal: (upi: UpiAccount | Record<string, never> | null) => void;

  showQrModal: UpiAccount | null;
  setShowQrModal: (upi: UpiAccount | null) => void;

  showStaffModal: StaffMember | Record<string, never> | null;
  setShowStaffModal: (staff: StaffMember | Record<string, never> | null) => void;

  showNewBankModal: boolean;
  setShowNewBankModal: (open: boolean) => void;

  editingBank?: BankAccount | null;
  setEditingBank?: (b: BankAccount | null) => void;

  showTableRowModal: {
    mode: 'insert' | 'edit';
    row?: Record<string, unknown>;
  } | null;
  setShowTableRowModal: (modal: { mode: 'insert' | 'edit'; row?: Record<string, unknown> } | null) => void;

  inspectTxn: Transaction | null;
  setInspectTxn: (txn: Transaction | null) => void;

  // Refresh callbacks
  onPaymentRecorded: () => void;
  onUpiSaved: () => void;
  onStaffSaved: () => void;
  onBankSaved: () => void;
  onTableSaved: () => void;
}

export function AppModals({
  activeOrg,
  upiAccounts,
  selectedTable,
  tableData,
  apiFetch,
  showToast,
  showNewTxnModal,
  setShowNewTxnModal,
  showUpiModal,
  setShowUpiModal,
  showQrModal,
  setShowQrModal,
  showStaffModal,
  setShowStaffModal,
  showNewBankModal,
  setShowNewBankModal,
  editingBank,
  setEditingBank,
  showTableRowModal,
  setShowTableRowModal,
  inspectTxn,
  setInspectTxn,
  onPaymentRecorded,
  onUpiSaved,
  onStaffSaved,
  onBankSaved,
  onTableSaved,
}: AppModalsProps) {
  const [isSaving, setIsSaving] = useState<boolean>(false);

  return (
    <>
      {/* 1. Record Counter Payment Modal */}
      <RecordPaymentModal
        isOpen={showNewTxnModal}
        onClose={() => setShowNewTxnModal(false)}
        activeOrg={activeOrg}
        defaultVpa={upiAccounts[0]?.vpa || `${activeOrg?.id}@upieasy`}
        isSaving={isSaving}
        onSubmitPayment={async (e) => {
          e.preventDefault();
          if (!activeOrg) return;
          const form = e.target as HTMLFormElement & {
            amount: HTMLInputElement;
            payerName: HTMLInputElement;
            payerVpa: HTMLInputElement;
            note: HTMLInputElement;
            referenceNumber?: HTMLInputElement;
            status?: HTMLSelectElement;
          };
          setIsSaving(true);
          try {
            await apiFetch(`/api/v1/organizations/${activeOrg.id}/transactions`, {
              method: 'POST',
              body: JSON.stringify({
                amount: parseFloat(form.amount.value),
                payerName: form.payerName.value || 'Customer',
                payerVpa: form.payerVpa.value || 'customer@upi',
                payeeName: activeOrg.name,
                payeeVpa: upiAccounts[0]?.vpa || `${activeOrg?.id}@upieasy`,
                note: form.note.value || 'Counter Sale',
                referenceNumber: form.referenceNumber?.value.trim() || undefined,
                status: form.status?.value || 'SUCCESS',
                direction: 'RECEIVED',
                source: 'UPI_INTENT',
              }),
            });
            showToast('Payment recorded successfully!');
            setShowNewTxnModal(false);
            onPaymentRecorded();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error recording payment';
            showToast(msg, 'error');
          } finally {
            setIsSaving(false);
          }
        }}
      />

      {/* 2. Configure UPI Account Modal */}
      <UpiAccountModal
        isOpen={Boolean(showUpiModal)}
        onClose={() => setShowUpiModal(null)}
        initialData={(showUpiModal as UpiAccount)?.id ? (showUpiModal as UpiAccount) : null}
        activeOrg={activeOrg}
        isSaving={isSaving}
        onSubmitUpi={async (e) => {
          e.preventDefault();
          if (!activeOrg) return;
          const form = e.target as HTMLFormElement & {
            vpa: HTMLInputElement;
            payeeName: HTMLInputElement;
            mcc: HTMLInputElement;
            isDefault: HTMLInputElement;
          };
          const upiAccountItem = showUpiModal as UpiAccount | null;
          const isEditing = Boolean(upiAccountItem?.id);
          const payload = {
            vpa: form.vpa.value.trim().toLowerCase(),
            payeeName: form.payeeName.value.trim(),
            merchantCategoryCode: form.mcc.value || '5411',
            isDefault: form.isDefault.checked,
          };
          setIsSaving(true);
          try {
            if (isEditing && upiAccountItem) {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi/${upiAccountItem.id}`, {
                method: 'PATCH',
                body: JSON.stringify(payload),
              });
              showToast('UPI details updated!');
            } else {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/upi`, {
                method: 'POST',
                body: JSON.stringify(payload),
              });
              showToast('UPI Account added!');
            }
            setShowUpiModal(null);
            onUpiSaved();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error saving UPI';
            showToast(msg, 'error');
          } finally {
            setIsSaving(false);
          }
        }}
      />

      {/* 3. Counter QR Code Modal */}
      <QrCodeModal
        upiAccount={showQrModal}
        onClose={() => setShowQrModal(null)}
        onCopyLink={(link) => {
          navigator.clipboard.writeText(link);
          showToast('Copied payment link to clipboard');
        }}
      />

      {/* 4. Staff Invite & Role Modal */}
      <StaffModal
        isOpen={Boolean(showStaffModal)}
        onClose={() => setShowStaffModal(null)}
        initialData={(showStaffModal as StaffMember)?.id ? (showStaffModal as StaffMember) : null}
        isSaving={isSaving}
        onSubmitStaff={async (e) => {
          e.preventDefault();
          if (!activeOrg) return;
          const form = e.target as HTMLFormElement & {
            mobile: HTMLInputElement;
            name: HTMLInputElement;
            email: HTMLInputElement;
            role: HTMLSelectElement;
            status?: HTMLSelectElement;
          };
          const staffMemberItem = showStaffModal as StaffMember | null;
          const isEditing = Boolean(staffMemberItem?.id);
          setIsSaving(true);
          try {
            if (isEditing && staffMemberItem) {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/staff/${staffMemberItem.id}`, {
                method: 'PATCH',
                body: JSON.stringify({ role: form.role.value, status: form.status?.value || 'ACTIVE' }),
              });
              showToast('Staff updated successfully!');
            } else {
              const res = await apiFetch<any>(`/api/v1/organizations/${activeOrg.id}/invites`, {
                method: 'POST',
                body: JSON.stringify({
                  email: form.email.value.trim().toLowerCase(),
                  mobileNumber: form.mobile?.value?.trim() || undefined,
                  name: form.name?.value?.trim() || undefined,
                  role: form.role.value,
                }),
              });
              if (res?.invite?.inviteUrl) {
                try {
                  await navigator.clipboard.writeText(res.invite.inviteUrl);
                  showToast(`Invite created! Link copied to clipboard.`);
                } catch {
                  showToast('Invitation created successfully!');
                }
              } else {
                showToast('Invitation created successfully!');
              }
            }
            setShowStaffModal(null);
            onStaffSaved();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error saving staff';
            showToast(msg, 'error');
          } finally {
            setIsSaving(false);
          }
        }}
      />

      {/* 5. Link & Edit Bank Account Modal */}
      <BankAccountModal
        isOpen={showNewBankModal || Boolean(editingBank)}
        onClose={() => {
          setShowNewBankModal(false);
          setEditingBank?.(null);
        }}
        accountToEdit={editingBank}
        activeOrg={activeOrg}
        isSaving={isSaving}
        onSubmitBank={async (e) => {
          e.preventDefault();
          if (!activeOrg) return;
          const form = e.target as HTMLFormElement & {
            bankName: HTMLInputElement;
            holderName: HTMLInputElement;
            accountNumber: HTMLInputElement;
            ifsc: HTMLInputElement;
            type: HTMLSelectElement;
          };
          setIsSaving(true);
          try {
            if (editingBank) {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/accounts/${editingBank.id}`, {
                method: 'PATCH',
                body: JSON.stringify({
                  bankName: form.bankName.value.trim(),
                  accountHolderName: form.holderName.value.trim(),
                  ...(form.accountNumber.value.trim() ? { accountNumber: form.accountNumber.value.trim() } : {}),
                  ifscCode: form.ifsc.value.trim().toUpperCase(),
                  accountType: form.type.value,
                }),
              });
              showToast('Bank account updated successfully!');
            } else {
              await apiFetch(`/api/v1/organizations/${activeOrg.id}/accounts`, {
                method: 'POST',
                body: JSON.stringify({
                  bankName: form.bankName.value.trim(),
                  accountHolderName: form.holderName.value.trim(),
                  accountNumber: form.accountNumber.value.trim(),
                  ifscCode: form.ifsc.value.trim().toUpperCase(),
                  accountType: form.type.value,
                }),
              });
              showToast('Bank account linked!');
            }
            setShowNewBankModal(false);
            setEditingBank?.(null);
            onBankSaved();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error saving bank account';
            showToast(msg, 'error');
          } finally {
            setIsSaving(false);
          }
        }}
      />

      {/* 6. Admin Database Table Row Modal */}
      <TableRowModal
        isOpen={Boolean(showTableRowModal)}
        onClose={() => setShowTableRowModal(null)}
        selectedTable={selectedTable}
        columns={tableData?.columns || []}
        initialRow={showTableRowModal?.mode === 'edit' ? showTableRowModal.row : null}
        onSubmitRow={async (e) => {
          e.preventDefault();
          const form = e.target as HTMLFormElement;
          const isEditing = showTableRowModal?.mode === 'edit';
          try {
            if (isEditing && showTableRowModal?.row) {
              const updates: Record<string, string> = {};
              tableData.columns.forEach((col) => {
                const element = form.elements.namedItem(col.name) as HTMLInputElement | null;
                if (!col.isPrimary && element) updates[col.name] = element.value;
              });
              const pkCol = tableData.columns.find((c) => c.isPrimary)?.name || 'id';
              const pkVal = showTableRowModal.row[pkCol];
              await apiFetch(`/api/v1/admin/tables/${selectedTable}/${pkVal}`, {
                method: 'PATCH',
                body: JSON.stringify(updates),
              });
              showToast('Record updated successfully!');
            } else {
              const newRow: Record<string, string> = {};
              tableData.columns.forEach((col) => {
                const element = form.elements.namedItem(col.name) as HTMLInputElement | null;
                if (element && element.value !== '') newRow[col.name] = element.value;
              });
              await apiFetch(`/api/v1/admin/tables/${selectedTable}`, {
                method: 'POST',
                body: JSON.stringify(newRow),
              });
              showToast('Record inserted successfully!');
            }
            setShowTableRowModal(null);
            onTableSaved();
          } catch (err: unknown) {
            const msg = err instanceof Error ? err.message : 'Error saving record';
            showToast(msg, 'error');
          }
        }}
      />

      {/* 7. Full Audit Transaction Detail Modal */}
      <TransactionDetailModal
        transaction={inspectTxn}
        onClose={() => setInspectTxn(null)}
      />
    </>
  );
}

export default AppModals;
