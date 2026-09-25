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
  const [isProcessing, setIsProcessing] = useState(false);

  if (!invite) return null;

  const handleAccept = async () => {
    setIsProcessing(true);
    try {
      await onAccept(invite);
    } finally {
      setIsProcessing(false);
    }
  };

  const handleDecline = async () => {
    setIsProcessing(true);
    try {
      await onDecline(invite);
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <Modal
      isOpen={Boolean(invite)}
      onClose={() => {}}
      title="Team Invitation"
      subtitle="You have been invited to join an organization">
      <div className="space-y-4 text-xs">
        <div className="flex items-center gap-3 p-3.5 rounded-2xl bg-brand-500/10 border border-brand-500/20 text-foreground">
          <div className="w-10 h-10 rounded-xl bg-brand-500/20 flex items-center justify-center shrink-0 text-brand-600 dark:text-cyan-400">
            <IconUsers className="w-5 h-5" />
          </div>
          <div>
            <div className="font-bold text-sm text-foreground">
              {invite.organizationName || 'Store Organization'}
            </div>
            <div className="text-muted-foreground mt-0.5">
              Assigned Role:{' '}
              <Badge variant="secondary" className="font-bold text-[10px] ml-1 uppercase">
                {invite.role}
              </Badge>
            </div>
          </div>
        </div>

        <p className="text-muted-foreground text-xs leading-relaxed">
          Accept this invitation to join the team, gain instant access to live store operations, and bypass business onboarding completely.
        </p>

        {invite.inviterName && (
          <div className="text-[11px] text-muted-foreground">
            Invited by: <span className="font-semibold text-foreground">{invite.inviterName}</span>
          </div>
        )}

        <div className="flex items-center justify-end gap-2 pt-2">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleDecline}
            disabled={isProcessing}
            className="rounded-xl gap-1">
            <IconX className="w-3.5 h-3.5" />
            <span>Decline</span>
          </Button>

          <Button
            type="button"
            variant="brand"
            size="sm"
            onClick={handleAccept}
            disabled={isProcessing}
            className="rounded-xl gap-1 font-bold">
            <IconCheck className="w-3.5 h-3.5" />
            <span>{isProcessing ? 'Accepting...' : 'Accept & Join'}</span>
          </Button>
        </div>
      </div>
    </Modal>
  );
}

export default PendingInviteModal;
