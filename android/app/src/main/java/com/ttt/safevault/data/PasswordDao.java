package com.ttt.safevault.data;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

/**
 * 密码数据访问对象
 */
@Dao
public interface PasswordDao {

    @Query("SELECT * FROM passwords ORDER BY updatedAt DESC")
    List<EncryptedPasswordEntity> getAll();

    @Query("SELECT * FROM passwords WHERE id = :id")
    EncryptedPasswordEntity getById(int id);

    @Insert
    long insert(EncryptedPasswordEntity entity);

    @Update
    void update(EncryptedPasswordEntity entity);

    @Delete
    void delete(EncryptedPasswordEntity entity);

    @Query("DELETE FROM passwords WHERE id = :id")
    int deleteById(int id);

    @Query("SELECT COUNT(*) FROM passwords")
    int getCount();

    @Query("DELETE FROM passwords")
    void deleteAll();
}
