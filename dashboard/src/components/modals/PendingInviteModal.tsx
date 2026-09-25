import React, { useState } from 'react';
import Modal from '@/components/Modal';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { IconUsers, IconCheck, IconX } from '@/components/ui/icons';

export interface PendingInvite {
  id: string;
  organizationId: string;
  organizationName: string;
  role: string;
  status: string;
  invitedEmail?: string;
  invitedMobile?: string;
  inviterName?: string;
  expiresAt?: string;
}

export interface PendingInviteModalProps {
  invite: PendingInvite | null;
  onAccept: (invite: PendingInvite) => Promise<void>;
  onDecline: (invite: PendingInvite) => Promise<void>;
}

export function PendingInviteModal({
  invite,
  onAccept,
  onDecline,
}: PendingInviteModalProps) {
  const [accepting, setAccepting] = useState(false);
  const [declining, setDeclining] = useState(false);
  const isProcessing = accepting || declining;

  if (!invite) return null;

  const handleAccept = async () => {
    setAccepting(true);
    try {
      await onAccept(invite);
    } finally {
      setAccepting(false);
    }
  };

  const handleDecline = async () => {
    setDeclining(true);
    try {
      await onDecline(invite);
    } finally {
      setDeclining(false);
    }
  };

  return (
    <Modal
      isOpen={Boolean(invite)}
      onClose={() => {}}
      title="Team Invitation"
      subtitle="You have been invited to join an organization"
      icon={<IconUsers className="w-4 h-4" />}>
      <div className="space-y-4 text-xs">
        <div className="flex items-center gap-3.5 p-4 rounded-2xl bg-brand-500/10 border border-brand-500/20 text-foreground">
          <div className="w-11 h-11 rounded-2xl bg-brand-500/20 flex items-center justify-center shrink-0 text-brand-600 dark:text-cyan-400">
            <IconUsers className="w-5 h-5" />
          </div>
          <div>
            <div className="font-bold text-sm text-foreground tracking-tight">
              {invite.organizationName || 'Store Organization'}
            </div>
            <div className="text-muted-foreground mt-1 flex items-center gap-1.5">
              <span>Assigned Role:</span>
              <Badge variant="brand" size="sm" className="font-bold uppercase tracking-wider">
                {invite.role}
              </Badge>
            </div>
          </div>
        </div>

        <p className="text-muted-foreground text-xs leading-relaxed">
          Accept this invitation to join the team, gain instant access to live store operations, and bypass business onboarding completely.
        </p>

        {invite.inviterName && (
          <div className="text-[11px] text-muted-foreground bg-muted/20 p-2.5 rounded-xl border border-border/60">
            Invited by: <span className="font-semibold text-foreground">{invite.inviterName}</span>
          </div>
        )}

        <div className="flex items-center justify-end gap-2.5 pt-3 border-t border-border/50">
          <Button
            type="button"
            variant="outline"
            size="default"
            onClick={handleDecline}
            loading={declining}
            disabled={isProcessing}
            leftIcon={<IconX className="w-3.5 h-3.5" />}>
            Decline
          </Button>

          <Button
            type="button"
            variant="brand"
            size="default"
            onClick={handleAccept}
            loading={accepting}
            loadingText="Joining..."
            disabled={isProcessing}
            leftIcon={<IconCheck className="w-3.5 h-3.5" />}>
            Accept & Join Store
          </Button>
        </div>
      </div>
    </Modal>
  );
}

export default PendingInviteModal;
