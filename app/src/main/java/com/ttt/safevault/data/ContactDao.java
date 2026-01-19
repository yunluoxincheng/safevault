package com.ttt.safevault.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 联系人数据访问对象
 */
@Dao
public interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY last_used_at DESC")
    List<Contact> getAllContacts();

    @Query("SELECT * FROM contacts WHERE contact_id = :contactId")
    Contact getContact(String contactId);

    @Query("SELECT * FROM contacts WHERE user_id = :userId")
    Contact getContactByUserId(String userId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertContact(Contact contact);

    @Update
    int updateContact(Contact contact);

    @Delete
    int deleteContact(Contact contact);

    @Query("DELETE FROM contacts WHERE contact_id = :contactId")
    int deleteContactById(String contactId);

    @Query("SELECT * FROM contacts WHERE display_name LIKE '%' || :query || '%' OR my_note LIKE '%' || :query || '%'")
    List<Contact> searchContacts(String query);

    @Query("UPDATE contacts SET last_used_at = :timestamp WHERE contact_id = :contactId")
    int updateLastUsed(String contactId, long timestamp);

    @Query("SELECT COUNT(*) FROM contacts")
    int getContactCount();
}
