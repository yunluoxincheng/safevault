package com.ttt.safevault.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * SafeVault应用数据库
 */
@Database(entities = {EncryptedPasswordEntity.class, Contact.class, ShareRecord.class, FriendRequest.class, SyncOperationEntity.class}, version = 7, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static final String DATABASE_NAME = "safevault_db";
    private static volatile AppDatabase INSTANCE;

    public abstract PasswordDao passwordDao();
    public abstract ContactDao contactDao();
    public abstract ShareRecordDao shareRecordDao();
    public abstract FriendRequestDao friendRequestDao();
    public abstract SyncOperationDao syncOperationDao();

    // 数据库版本1到版本2的迁移：添加 encryptedTags 字段
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 添加 encryptedTags 列
            database.execSQL("ALTER TABLE passwords ADD COLUMN encryptedTags TEXT");
        }
    };

    // 数据库版本2到版本3的迁移：添加联系人表和分享记录表
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 创建联系人表
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS contacts (" +
                "contact_id TEXT PRIMARY KEY NOT NULL, " +
                "user_id TEXT NOT NULL, " +
                "username TEXT NOT NULL, " +
                "display_name TEXT NOT NULL, " +
                "public_key TEXT NOT NULL, " +
                "my_note TEXT, " +
                "added_at INTEGER NOT NULL, " +
                "last_used_at INTEGER NOT NULL)"
            );

            // 创建分享记录表
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS share_records (" +
                "share_id TEXT PRIMARY KEY NOT NULL, " +
                "password_id INTEGER NOT NULL, " +
                "type TEXT NOT NULL, " +
                "contact_id TEXT, " +
                "remote_user_id TEXT, " +
                "encrypted_data TEXT, " +
                "permission TEXT, " +
                "expire_at INTEGER NOT NULL, " +
                "status TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "accessed_at INTEGER NOT NULL)"
            );

            // 创建索引以提高查询性能
            database.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_user_id ON contacts(user_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_contacts_last_used_at ON contacts(last_used_at)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_share_records_type ON share_records(type)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_share_records_status ON share_records(status)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_share_records_password_id ON share_records(password_id)");
        }
    };

    // 数据库版本3到版本4的迁移：添加好友请求表和联系人表cloud_user_id字段
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 为contacts表添加cloud_user_id列
            database.execSQL("ALTER TABLE contacts ADD COLUMN cloud_user_id TEXT");

            // 创建好友请求表
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS friend_requests (" +
                "request_id TEXT PRIMARY KEY NOT NULL, " +
                "from_user_id TEXT NOT NULL, " +
                "from_username TEXT NOT NULL, " +
                "from_display_name TEXT NOT NULL, " +
                "from_public_key TEXT NOT NULL, " +
                "message TEXT, " +
                "status TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL, " +
                "responded_at INTEGER NOT NULL)"
            );

            // 创建索引以提高查询性能
            database.execSQL("CREATE INDEX IF NOT EXISTS index_friend_requests_from_user_id ON friend_requests(from_user_id)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_friend_requests_status ON friend_requests(status)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_friend_requests_created_at ON friend_requests(created_at)");
        }
    };

    // 数据库版本4到版本5的迁移：添加同步操作表
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 创建同步操作表
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS sync_operations (" +
                "operationId TEXT PRIMARY KEY NOT NULL, " +
                "operationType TEXT NOT NULL, " +
                "passwordId INTEGER, " +
                "timestamp INTEGER NOT NULL, " +
                "retryCount INTEGER NOT NULL, " +
                "status TEXT NOT NULL)"
            );

            // 创建索引
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_status ON sync_operations(status)");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_operations_timestamp ON sync_operations(timestamp)");
        }
    };

    // 数据库版本5到版本6的迁移：添加联系人表email字段
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 为contacts表添加email列
            database.execSQL("ALTER TABLE contacts ADD COLUMN email TEXT");
        }
    };

    // 数据库版本6到版本7的迁移：添加联系人表is_online字段
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // 为contacts表添加is_online列，默认值为0（false）
            database.execSQL("ALTER TABLE contacts ADD COLUMN is_online INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                     .build();
                }
            }
        }
        return INSTANCE;
    }

    public static void destroyInstance() {
        INSTANCE = null;
    }
}
