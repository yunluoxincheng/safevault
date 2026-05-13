package com.ttt.safevault.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 生成的密码记录数据模型
 */
@Entity(tableName = "generated_passwords")
public class GeneratedPassword {

    @PrimaryKey(autoGenerate = true)
    private long id;

    private String password;
    private int length;
    private boolean uppercase;
    private boolean lowercase;
    private boolean numbers;
    private boolean symbols;
    private long timestamp;

    // 无参构造函数（Room 需要）
    public GeneratedPassword() {
    }

    // 完整构造函数
    public GeneratedPassword(String password, int length, boolean uppercase, boolean lowercase,
                             boolean numbers, boolean symbols, long timestamp) {
        this.password = password;
        this.length = length;
        this.uppercase = uppercase;
        this.lowercase = lowercase;
        this.numbers = numbers;
        this.symbols = symbols;
        this.timestamp = timestamp;
    }

    // Getters
    public long getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }

    public int getLength() {
        return length;
    }

    public boolean isUppercase() {
        return uppercase;
    }

    public boolean isLowercase() {
        return lowercase;
    }

    public boolean isNumbers() {
        return numbers;
    }

    public boolean isSymbols() {
        return symbols;
    }

    public long getTimestamp() {
        return timestamp;
    }

    // Setters
    public void setId(long id) {
        this.id = id;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public void setUppercase(boolean uppercase) {
        this.uppercase = uppercase;
    }

    public void setLowercase(boolean lowercase) {
        this.lowercase = lowercase;
    }

    public void setNumbers(boolean numbers) {
        this.numbers = numbers;
    }

    public void setSymbols(boolean symbols) {
        this.symbols = symbols;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 获取配置描述
     */
    @NonNull
    public String getConfigDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(length).append("位");
        if (uppercase) sb.append(" 大写");
        if (lowercase) sb.append(" 小写");
        if (numbers) sb.append(" 数字");
        if (symbols) sb.append(" 符号");
        return sb.toString();
    }
}
