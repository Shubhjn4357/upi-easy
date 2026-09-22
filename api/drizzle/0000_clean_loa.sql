CREATE TABLE `devices` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`device_id` text NOT NULL,
	`platform` text DEFAULT 'ANDROID',
	`device_model` text,
	`os_version` text,
	`app_version` text,
	`fcm_token` text,
	`is_active` integer DEFAULT true NOT NULL,
	`last_seen_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `device_user_dev_idx` ON `devices` (`user_id`,`device_id`);--> statement-breakpoint
CREATE TABLE `otps` (
	`id` text PRIMARY KEY NOT NULL,
	`mobile_number` text NOT NULL,
	`otp_hash` text NOT NULL,
	`attempts` integer DEFAULT 0 NOT NULL,
	`expires_at` integer NOT NULL,
	`is_verified` integer DEFAULT false NOT NULL,
	`created_at` integer NOT NULL
);
--> statement-breakpoint
CREATE TABLE `sessions` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`device_id` text,
	`refresh_token_hash` text,
	`expires_at` integer NOT NULL,
	`is_revoked` integer DEFAULT false NOT NULL,
	`ip_address` text,
	`user_agent` text,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`device_id`) REFERENCES `devices`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE TABLE `users` (
	`id` text PRIMARY KEY NOT NULL,
	`google_id` text,
	`mobile_number` text,
	`full_name` text,
	`email` text,
	`avatar_url` text,
	`status` text DEFAULT 'ACTIVE' NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `users_google_id_unique` ON `users` (`google_id`);--> statement-breakpoint
CREATE TABLE `organizations` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`legal_business_name` text,
	`category` text,
	`pan_number` text,
	`gstin` text,
	`status` text DEFAULT 'ACTIVE' NOT NULL,
	`owner_id` text NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`owner_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `roles` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`description` text,
	`is_system` integer DEFAULT false NOT NULL
);
--> statement-breakpoint
CREATE TABLE `permissions` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`description` text,
	`category` text NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `permissions_name_unique` ON `permissions` (`name`);--> statement-breakpoint
CREATE TABLE `role_permissions` (
	`id` text PRIMARY KEY NOT NULL,
	`role_id` text NOT NULL,
	`permission_id` text NOT NULL,
	FOREIGN KEY (`role_id`) REFERENCES `roles`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`permission_id`) REFERENCES `permissions`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `organization_members` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`user_id` text NOT NULL,
	`role_id` text NOT NULL,
	`status` text DEFAULT 'ACTIVE' NOT NULL,
	`invited_by` text,
	`joined_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`role_id`) REFERENCES `roles`(`id`) ON UPDATE no action ON DELETE no action,
	FOREIGN KEY (`invited_by`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE UNIQUE INDEX `org_member_unique` ON `organization_members` (`organization_id`,`user_id`);--> statement-breakpoint
CREATE TABLE `organization_invites` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`invited_user_id` text,
	`invited_mobile` text NOT NULL,
	`invited_email` text,
	`invited_name` text,
	`role` text NOT NULL,
	`invited_by` text NOT NULL,
	`status` text DEFAULT 'PENDING' NOT NULL,
	`expires_at` integer NOT NULL,
	`accepted_at` integer,
	`rejected_at` integer,
	`cancelled_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`invited_user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`invited_by`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE no action
);
--> statement-breakpoint
CREATE TABLE `bank_accounts` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`bank_name` text NOT NULL,
	`account_holder_name` text NOT NULL,
	`account_number_masked` text NOT NULL,
	`ifsc_code` text NOT NULL,
	`account_type` text DEFAULT 'CURRENT' NOT NULL,
	`is_default` integer DEFAULT 0 NOT NULL,
	`status` text DEFAULT 'ACTIVE' NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `upi_accounts` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`bank_account_id` text,
	`vpa` text NOT NULL,
	`payee_name` text NOT NULL,
	`merchant_category_code` text DEFAULT '5411' NOT NULL,
	`is_default` integer DEFAULT false NOT NULL,
	`status` text DEFAULT 'ACTIVE' NOT NULL,
	`transaction_count` integer DEFAULT 0 NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`bank_account_id`) REFERENCES `bank_accounts`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE TABLE `payment_accounts` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`label` text NOT NULL,
	`upi_id` text NOT NULL,
	`payment_app_id` text NOT NULL,
	`payment_app_package` text NOT NULL,
	`status` text DEFAULT 'ACTIVE' NOT NULL,
	`detection_enabled` integer DEFAULT 1 NOT NULL,
	`notification_access_required` integer DEFAULT 1 NOT NULL,
	`last_notification_detected_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `qr_codes` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`upi_account_id` text NOT NULL,
	`payment_account_id` text,
	`title` text NOT NULL,
	`qr_payload` text NOT NULL,
	`type` text DEFAULT 'STATIC' NOT NULL,
	`amount` real,
	`note` text,
	`usage_count` integer DEFAULT 0 NOT NULL,
	`is_active` integer DEFAULT true NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`upi_account_id`) REFERENCES `upi_accounts`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`payment_account_id`) REFERENCES `payment_accounts`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE TABLE `transactions` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`bank_account_id` text,
	`upi_account_id` text,
	`payment_account_id` text,
	`type` text DEFAULT 'PAYMENT' NOT NULL,
	`direction` text DEFAULT 'RECEIVED' NOT NULL,
	`amount` real NOT NULL,
	`currency` text DEFAULT 'INR' NOT NULL,
	`status` text DEFAULT 'PENDING' NOT NULL,
	`verification_status` text DEFAULT 'UNVERIFIED' NOT NULL,
	`event_source` text DEFAULT 'UPI_INTENT' NOT NULL,
	`payment_method` text DEFAULT 'UPI' NOT NULL,
	`provider` text DEFAULT 'NPCI' NOT NULL,
	`provider_transaction_id` text,
	`upi_transaction_id` text,
	`reference_number` text,
	`payer_name` text,
	`payer_vpa` text,
	`payee_name` text NOT NULL,
	`payee_vpa` text NOT NULL,
	`note` text,
	`invoice_id` text,
	`staff_id` text,
	`source` text DEFAULT 'UPI_INTENT' NOT NULL,
	`occurred_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`bank_account_id`) REFERENCES `bank_accounts`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`upi_account_id`) REFERENCES `upi_accounts`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`payment_account_id`) REFERENCES `payment_accounts`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`staff_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE INDEX `txn_org_occurred_idx` ON `transactions` (`organization_id`,`occurred_at`);--> statement-breakpoint
CREATE INDEX `txn_org_status_idx` ON `transactions` (`organization_id`,`status`);--> statement-breakpoint
CREATE INDEX `txn_org_upi_idx` ON `transactions` (`organization_id`,`upi_account_id`);--> statement-breakpoint
CREATE INDEX `txn_provider_id_idx` ON `transactions` (`provider_transaction_id`);--> statement-breakpoint
CREATE INDEX `txn_ref_num_idx` ON `transactions` (`reference_number`);--> statement-breakpoint
CREATE TABLE `transaction_events` (
	`id` text PRIMARY KEY NOT NULL,
	`transaction_id` text NOT NULL,
	`organization_id` text NOT NULL,
	`event_type` text NOT NULL,
	`previous_status` text,
	`new_status` text NOT NULL,
	`payload_json` text,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `transaction_references` (
	`id` text PRIMARY KEY NOT NULL,
	`transaction_id` text NOT NULL,
	`reference_type` text NOT NULL,
	`reference_number` text NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `observed_payment_events` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`payment_account_id` text,
	`qr_id` text,
	`source_type` text NOT NULL,
	`source_package` text NOT NULL,
	`amount_minor` integer,
	`currency` text DEFAULT 'INR' NOT NULL,
	`direction` text DEFAULT 'RECEIVED' NOT NULL,
	`payer_name` text,
	`payer_vpa` text,
	`reference` text,
	`event_fingerprint` text NOT NULL,
	`match_status` text DEFAULT 'MATCHED' NOT NULL,
	`verification_status` text DEFAULT 'OBSERVED' NOT NULL,
	`observed_at` integer NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`payment_account_id`) REFERENCES `payment_accounts`(`id`) ON DELETE set null,
	FOREIGN KEY (`qr_id`) REFERENCES `qr_codes`(`id`) ON DELETE set null
);
--> statement-breakpoint
CREATE INDEX `observed_evt_org_fp_idx` ON `observed_payment_events` (`organization_id`,`event_fingerprint`);--> statement-breakpoint
CREATE INDEX `observed_evt_org_time_idx` ON `observed_payment_events` (`organization_id`,`observed_at`);--> statement-breakpoint
CREATE TABLE `notifications` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`organization_id` text NOT NULL,
	`title` text NOT NULL,
	`message` text NOT NULL,
	`type` text NOT NULL,
	`is_read` integer DEFAULT false NOT NULL,
	`metadata_json` text,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE INDEX `notif_user_created_idx` ON `notifications` (`user_id`,`created_at`);--> statement-breakpoint
CREATE TABLE `notification_preferences` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`organization_id` text NOT NULL,
	`payment_alerts` integer DEFAULT true NOT NULL,
	`payment_received` integer DEFAULT true NOT NULL,
	`payment_sent` integer DEFAULT true NOT NULL,
	`payment_failed` integer DEFAULT true NOT NULL,
	`payment_reversed` integer DEFAULT true NOT NULL,
	`staff_alerts` integer DEFAULT true NOT NULL,
	`staff_activity` integer DEFAULT true NOT NULL,
	`security_alerts` integer DEFAULT true NOT NULL,
	`sync_status` integer DEFAULT false NOT NULL,
	`voice_announcements` integer DEFAULT false NOT NULL,
	`voice_enabled` integer DEFAULT false NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE TABLE `audit_logs` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text,
	`actor_id` text,
	`action` text NOT NULL,
	`resource_type` text NOT NULL,
	`resource_id` text,
	`metadata_json` text,
	`ip_address` text,
	`device_id` text,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`actor_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE INDEX `audit_org_created_idx` ON `audit_logs` (`organization_id`,`created_at`);--> statement-breakpoint
CREATE TABLE `idempotency_keys` (
	`id` text PRIMARY KEY NOT NULL,
	`idempotency_key` text NOT NULL,
	`organization_id` text,
	`operation` text NOT NULL,
	`request_hash` text NOT NULL,
	`response_status` integer NOT NULL,
	`response_body` text NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `idempotency_keys_idempotency_key_unique` ON `idempotency_keys` (`idempotency_key`);--> statement-breakpoint
CREATE TABLE `outbox_events` (
	`sequence` integer PRIMARY KEY AUTOINCREMENT NOT NULL,
	`id` text NOT NULL,
	`organization_id` text NOT NULL,
	`event_type` text NOT NULL,
	`payload_json` text NOT NULL,
	`status` text DEFAULT 'PENDING' NOT NULL,
	`created_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
);
--> statement-breakpoint
CREATE UNIQUE INDEX `outbox_events_id_unique` ON `outbox_events` (`id`);--> statement-breakpoint
CREATE INDEX `outbox_org_seq_idx` ON `outbox_events` (`organization_id`,`sequence`);--> statement-breakpoint
CREATE TABLE `sync_cursors` (
	`id` text PRIMARY KEY NOT NULL,
	`user_id` text NOT NULL,
	`device_id` text NOT NULL,
	`organization_id` text NOT NULL,
	`last_sequence` integer DEFAULT 0 NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`user_id`) REFERENCES `users`(`id`) ON UPDATE no action ON DELETE cascade,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
);
