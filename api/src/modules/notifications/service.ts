import { db } from "../../db/index.js";
import * as schema from "../../db/schema/index.js";
import { eq, and } from "drizzle-orm";
import { generateId } from "../../lib/crypto.js";
import { logger } from "../../lib/logger.js";

export interface PaymentNotificationData {
  transactionId: string;
  amount: number;
  direction?: string;
  currency?: string;
  referenceNumber?: string | null;
  payerName?: string | null;
  payerVpa?: string | null;
  payeeName?: string;
  payeeVpa?: string;
  occurredAt?: number;
}

/**
 * Dispatches payment notifications to all connected contacts added as staff in the organization.
 * Persists unread notifications in database so when offline staff phones come online,
 * they immediately receive the sync events and notifications.
 */
export async function notifyOrganizationPayment(
  organizationId: string,
  payment: PaymentNotificationData
) {
  try {
    const now = new Date();
    // 1. Find all active members belonging to this organization
    const activeMembers = await db
      .select({ userId: schema.organizationMembers.userId })
      .from(schema.organizationMembers)
      .where(
        and(
          eq(schema.organizationMembers.organizationId, organizationId),
          eq(schema.organizationMembers.status, "ACTIVE")
        )
      )
      .all();

    // 2. Also ensure organization owner is included
    const org = await db
      .select({ ownerId: schema.organizations.ownerId })
      .from(schema.organizations)
      .where(eq(schema.organizations.id, organizationId))
      .get();

    const targetUserIds = new Set<string>();
    if (org?.ownerId) targetUserIds.add(org.ownerId);
    activeMembers.forEach((m: { userId: string }) => targetUserIds.add(m.userId));

    const formattedAmount = Number(payment.amount).toLocaleString("en-IN", {
      maximumFractionDigits: 2,
    });
    const title = `Payment of ₹${formattedAmount} received`;
    const message = `Received ₹${formattedAmount} from ${payment.payerName || "Customer"} via UPI`;

    // 3. Create persistent notification record for each staff member
    for (const userId of targetUserIds) {
      await db.insert(schema.notifications)
        .values({
          id: generateId("notif"),
          userId,
          organizationId,
          title,
          message,
          type: "payment.received",
          isRead: false,
          metadataJson: JSON.stringify({
            transactionId: payment.transactionId,
            amount: payment.amount,
            referenceNumber: payment.referenceNumber,
            payerName: payment.payerName,
            payerVpa: payment.payerVpa,
            payeeName: payment.payeeName,
            payeeVpa: payment.payeeVpa,
            occurredAt: payment.occurredAt || now.getTime(),
          }),
          createdAt: now,
        })
        .run();
    }

    logger.info(
      {
        organizationId,
        txnId: payment.transactionId,
        recipientsCount: targetUserIds.size,
      },
      "Dispatched payment notification to organization staff contacts"
    );
  } catch (error) {
    logger.error(
      { error, organizationId, txnId: payment.transactionId },
      "Failed to notify organization staff of payment"
    );
  }
}
