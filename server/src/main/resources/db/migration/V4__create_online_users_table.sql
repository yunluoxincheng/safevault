-- Create online_users table
CREATE TABLE IF NOT EXISTS online_users (
    user_id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    session_id VARCHAR(255) NOT NULL,
    CONSTRAINT fk_online_user
        FOREIGN KEY(user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_online_users_session_id ON online_users(session_id);
CREATE INDEX IF NOT EXISTS idx_online_users_last_seen ON online_users(last_seen);

-- Create index for nearby user queries (using PostgreSQL's geodetic distance)
CREATE INDEX IF NOT EXISTS idx_online_users_location
    ON online_users USING GIST(
        point(longitude, latitude)
    );
