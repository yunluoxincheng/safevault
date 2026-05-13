-- Make device-related fields nullable for email-based registration
-- Email-based users don't have device_id and public_key until they set up their master password

ALTER TABLE users ALTER COLUMN device_id DROP NOT NULL;
ALTER TABLE users ALTER COLUMN public_key DROP NOT NULL;
