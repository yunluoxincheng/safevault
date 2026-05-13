-- Email Verification Feature
-- Add email verification fields for unified email authentication system

-- Add email verification columns
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_expires_at TIMESTAMP;

-- Add devices column (JSON field for multi-device support)
ALTER TABLE users ADD COLUMN IF NOT EXISTS devices TEXT;

-- Create index on verification token for quick lookup
CREATE INDEX IF NOT EXISTS idx_users_verification_token ON users(verification_token) WHERE verification_token IS NOT NULL;

-- Create index on email verification status
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified);

-- Add comments
COMMENT ON COLUMN users.email_verified IS 'Whether the user email has been verified';
COMMENT ON COLUMN users.verification_token IS 'Token for email verification (256-bit secure random)';
COMMENT ON COLUMN users.verification_expires_at IS 'Expiration time for verification token (10 minutes)';
COMMENT ON COLUMN users.devices IS 'JSON array of registered devices for multi-device support';
