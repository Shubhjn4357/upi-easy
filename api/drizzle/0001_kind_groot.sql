CREATE TABLE `organization_invites` (
	`id` text PRIMARY KEY NOT NULL,
	`token` text,
	`organization_id` text NOT NULL,
	`invited_user_id` text,
	`invited_mobile` text,
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
CREATE UNIQUE INDEX `organization_invites_token_unique` ON `organization_invites` (`token`);--> statement-breakpoint
CREATE TABLE `payment_accounts` (
	`id` text PRIMARY KEY NOT NULL,
	`organization_id` text NOT NULL,
	`label` text NOT NULL,
	`upi_id` text NOT NULL,
	`payment_app_id` text NOT NULL,
	`payment_app_package` text NOT NULL,
	`status` text DEFAULT 'ACTIVE' NOT NULL,
	`detection_enabled` integer DEFAULT true NOT NULL,
	`notification_access_required` integer DEFAULT true NOT NULL,
	`last_notification_detected_at` integer,
	`created_at` integer NOT NULL,
	`updated_at` integer NOT NULL,
	FOREIGN KEY (`organization_id`) REFERENCES `organizations`(`id`) ON UPDATE no action ON DELETE cascade
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
	FOREIGN KEY (`payment_account_id`) REFERENCES `payment_accounts`(`id`) ON UPDATE no action ON DELETE set null,
	FOREIGN KEY (`qr_id`) REFERENCES `qr_codes`(`id`) ON UPDATE no action ON DELETE set null
);
--> statement-breakpoint
CREATE INDEX `observed_evt_org_fp_idx` ON `observed_payment_events` (`organization_id`,`event_fingerprint`);--> statement-breakpoint
CREATE INDEX `observed_evt_org_time_idx` ON `observed_payment_events` (`organization_id`,`observed_at`);--> statement-breakpoint
DROP TABLE `payment_requests`;--> statement-breakpoint
DROP TABLE `reconciliation_records`;--> statement-breakpoint
DROP TABLE `provider_connections`;--> statement-breakpoint
DROP TABLE `provider_webhooks`;--> statement-breakpoint
ALTER TABLE `devices` ADD `platform` text DEFAULT 'ANDROID';--> statement-breakpoint
ALTER TABLE `devices` ADD `app_version` text;--> statement-breakpoint
ALTER TABLE `devices` ADD `updated_at` integer;--> statement-breakpoint
CREATE UNIQUE INDEX `device_user_dev_idx` ON `devices` (`user_id`,`device_id`);--> statement-breakpoint
ALTER TABLE `bank_accounts` ADD `is_default` integer DEFAULT false NOT NULL;--> statement-breakpoint
ALTER TABLE `qr_codes` ADD `payment_account_id` text REFERENCES payment_accounts(id);--> statement-breakpoint
ALTER TABLE `transactions` ADD `payment_account_id` text REFERENCES payment_accounts(id);--> statement-breakpoint
ALTER TABLE `transactions` ADD `verification_status` text DEFAULT 'UNVERIFIED' NOT NULL;--> statement-breakpoint
ALTER TABLE `transactions` ADD `event_source` text DEFAULT 'UPI_INTENT' NOT NULL;--> statement-breakpoint
ALTER TABLE `notification_preferences` ADD `payment_received` integer DEFAULT true NOT NULL;--> statement-breakpoint
ALTER TABLE `notification_preferences` ADD `payment_sent` integer DEFAULT true NOT NULL;--> statement-breakpoint
ALTER TABLE `notification_preferences` ADD `payment_failed` integer DEFAULT true NOT NULL;--> statement-breakpoint
ALTER TABLE `notification_preferences` ADD `payment_reversed` integer DEFAULT true NOT NULL;--> statement-breakpoint
ALTER TABLE `notification_preferences` ADD `staff_activity` integer DEFAULT true NOT NULL;--> statement-breakpoint
ALTER TABLE `notification_preferences` ADD `sync_status` integer DEFAULT false NOT NULL;--> statement-breakpoint
ALTER TABLE `notification_preferences` ADD `voice_enabled` integer DEFAULT false NOT NULL;