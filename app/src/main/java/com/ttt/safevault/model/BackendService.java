package com.ttt.safevault.model;

import java.util.List;

/**
 * 后端服务接口
 * 定义了前端与后端交互的所有方法
 * 实际实现由后端模块提供，前端只调用接口
 */
public interface BackendService {

    /**
     * 使用主密码解锁应用
     * @param masterPassword 用户输入的主密码
     * @return true表示解锁成功，false表示密码错误
     */
    boolean unlock(String masterPassword);

    /**
     * 锁定应用
     * 清除内存中的敏感数据
     */
    void lock();

    /**
     * 解密并获取单个密码条目
     * @param id 条目ID
     * @return 解密后的PasswordItem对象
     */
    PasswordItem decryptItem(int id);

    /**
     * 搜索密码条目
     * @param query 搜索关键词（标题、用户名、URL等）
     * @return 匹配的条目列表
     */
    List<PasswordItem> search(String query);

    /**
     * 保存或更新密码条目
     * @param item 要保存的条目
     * @return 保存成功后的条目ID（新增时返回新ID，更新时返回原ID）
     */
    int saveItem(PasswordItem item);

    /**
     * 删除密码条目
     * @param id 要删除的条目ID
     * @return true表示删除成功，false表示失败
     */
    boolean deleteItem(int id);

    /**
     * 生成随机密码
     * @param length 密码长度
     * @param symbols 是否包含特殊符号
     * @return 生成的密码字符串
     */
    String generatePassword(int length, boolean symbols);

    /**
     * 生成随机密码（带更多选项）
     * @param length 密码长度
     * @param useUppercase 是否包含大写字母
     * @param useLowercase 是否包含小写字母
     * @param useNumbers 是否包含数字
     * @param useSymbols 是否包含特殊符号
     * @return 生成的密码字符串
     */
    String generatePassword(int length, boolean useUppercase, boolean useLowercase,
                           boolean useNumbers, boolean useSymbols);

    /**
     * 获取所有密码条目
     * @return 所有条目列表
     */
    List<PasswordItem> getAllItems();

    /**
     * 检查应用是否已解锁
     * @return true表示已解锁，可以访问加密数据
     */
    boolean isUnlocked();

    /**
     * 获取当前会话的主密码
     * 注意：此方法仅在应用已解锁后有效
     * @return 主密码字符串，如果未解锁返回null
     */
    String getMasterPassword();

    /**
     * 检查应用是否已初始化（是否已设置主密码）
     * @return true表示已初始化，false表示需要设置主密码
     */
    boolean isInitialized();

    /**
     * 初始化应用，设置主密码
     * @param masterPassword 要设置的主密码
     * @return true表示设置成功
     */
    boolean initialize(String masterPassword);

    /**
     * 更改主密码
     * @param oldPassword 旧的主密码
     * @param newPassword 新的主密码
     * @return true表示更改成功
     */
    boolean changeMasterPassword(String oldPassword, String newPassword);

    /**
     * 导出数据（加密导出）
     * @param exportPath 导出文件路径
     * @return true表示导出成功
     */
    boolean exportData(String exportPath);

    /**
     * 导入数据
     * @param importPath 导入文件路径
     * @return true表示导入成功
     */
    boolean importData(String importPath);

    /**
     * 获取应用统计信息
     * @return 包含条目总数等信息
     */
    AppStats getStats();

    /**
     * 记录进入后台的时间
     */
    void recordBackgroundTime();

    /**
     * 清除后台时间记录
     * 登录成功后应调用此方法，避免刚登录就被自动锁定
     */
    void clearBackgroundTime();

    /**
     * 获取后台时间戳
     * @return 进入后台的时间戳
     */
    long getBackgroundTime();

    /**
     * 获取自动锁定超时时间
     * @return 超时时间（分钟）
     */
    int getAutoLockTimeout();

    /**
     * 应用统计信息内部类
     */
    class AppStats {
        public int totalItems;
        public int weakPasswords;
        public int duplicatePasswords;
        public int lastBackupDays;

        public AppStats(int totalItems, int weakPasswords, int duplicatePasswords, int lastBackupDays) {
            this.totalItems = totalItems;
            this.weakPasswords = weakPasswords;
            this.duplicatePasswords = duplicatePasswords;
            this.lastBackupDays = lastBackupDays;
        }
    }

    // ========== 新增：账户操作接口 ==========

    /**
     * 设置PIN码
     * @param pinCode PIN码（4-20位数字）
     * @return true表示设置成功
     */
    boolean setPinCode(String pinCode);

    /**
     * 验证PIN码
     * @param pinCode 要验证的PIN码
     * @return true表示PIN码正确
     */
    boolean verifyPinCode(String pinCode);

    /**
     * 清除PIN码
     * @return true表示清除成功
     */
    boolean clearPinCode();

    /**
     * 检查PIN码是否已启用
     * @return true表示PIN码已启用
     */
    boolean isPinCodeEnabled();

    /**
     * 注销登录
     * 清除内存中的敏感数据，返回登录状态
     */
    void logout();

    /**
     * 删除账户
     * 永久删除所有本地和云端数据
     * @return true表示删除成功
     */
    boolean deleteAccount();
    
    /**
     * 使用生物识别解锁应用
     * 用于生物识别认证成功后的解锁操作
     * @return true表示解锁成功，false表示失败
     */
    boolean unlockWithBiometric();
    
    /**
     * 检查是否可以使用生物识别认证
     * @return true表示生物识别认证已启用且可用
     */
    boolean canUseBiometricAuthentication();

    // ========== 新增：分享管理接口 ==========
    // 旧的本地分享方法已移除，现在使用云端分享接口

    // ========== 新增：云端分享接口（后端API集成）==========

    /**
     * 用户注册
     * @param username 用户名
     * @param password 密码
     * @param displayName 显示名称
     * @return AuthResponse 包含token和用户信息
     */
    com.ttt.safevault.dto.response.AuthResponse register(String username, String password, String displayName);

    /**
     * 用户登录
     * @param username 用户名
     * @param password 密码
     * @return AuthResponse 包含token和用户信息
     */
    com.ttt.safevault.dto.response.AuthResponse login(String username, String password);

    /**
     * 刷新Token
     * @param refreshToken 刷新Token
     * @return AuthResponse 新的token信息
     */
    com.ttt.safevault.dto.response.AuthResponse refreshToken(String refreshToken);

    /**
     * 创建云端分享（通过后端API）
     * @param passwordId 密码ID
     * @param toUserId 接收方用户ID（null表示直接分享）
     * @param expireInMinutes 过期时间（分钟）
     * @param permission 分享权限
     * @param shareType 分享类型：DIRECT, USER_TO_USER, NEARBY
     * @return ShareResponse 包含shareId和shareToken
     */
    com.ttt.safevault.dto.response.ShareResponse createCloudShare(int passwordId, String toUserId,
                                                                   int expireInMinutes, SharePermission permission,
                                                                   String shareType);

    /**
     * 接收云端分享
     * @param shareId 分享ID或Token
     * @return ReceivedShareResponse 分享详情和密码数据
     */
    com.ttt.safevault.dto.response.ReceivedShareResponse receiveCloudShare(String shareId);

    /**
     * 撤销云端分享
     * @param shareId 分享ID
     */
    void revokeCloudShare(String shareId);

    /**
     * 保存云端分享到本地
     * @param shareId 分享ID
     */
    void saveCloudShare(String shareId);

    /**
     * 获取我创建的云端分享列表
     * @return 分享列表
     */
    java.util.List<com.ttt.safevault.dto.response.ReceivedShareResponse> getMyCloudShares();

    /**
     * 获取我接收的云端分享列表
     * @return 分享列表
     */
    java.util.List<com.ttt.safevault.dto.response.ReceivedShareResponse> getReceivedCloudShares();

    /**
     * 注册位置信息（附近发现）
     * @param latitude 纬度
     * @param longitude 经度
     * @param radius 发现范围（米）
     */
    void registerLocation(double latitude, double longitude, double radius);

    /**
     * 获取附近用户
     * @param latitude 纬度
     * @param longitude 经度
     * @param radius 搜索半径（米）
     * @return 附近用户列表
     */
    java.util.List<com.ttt.safevault.dto.response.NearbyUserResponse> getNearbyUsers(double latitude, double longitude, double radius);

    /**
     * 发送心跳保持在线状态
     */
    void sendHeartbeat();

    /**
     * 检查是否已登录云端服务
     * @return true表示已登录
     */
    boolean isCloudLoggedIn();

    /**
     * 登出云端服务
     */
    void logoutCloud();

    /**
     * 完成注册
     * 邮箱验证后设置主密码并完成注册
     * @param email 邮箱
     * @param username 用户名
     * @param masterPassword 主密码
     * @return CompleteRegistrationResponse 完成注册响应
     */
    com.ttt.safevault.dto.response.CompleteRegistrationResponse completeRegistration(
            String email, String username, String masterPassword);

    // ========== 新增：本地分享接口（离线分享）==========

    /**
     * 创建直接密码分享（离线/本地）
     * @param passwordId 密码ID
     * @param expireInMinutes 过期时间（分钟）
     * @param permission 分享权限
     * @return 分享Token或QR码内容
     */
    String createDirectPasswordShare(int passwordId, int expireInMinutes, SharePermission permission);

    /**
     * 创建离线分享（版本2：密钥已嵌入，无需密码）
     * @param passwordId 密码ID
     * @param expireInMinutes 过期时间（分钟）
     * @param permission 分享权限
     * @return QR码内容（包含加密数据）
     */
    String createOfflineShare(int passwordId, int expireInMinutes, SharePermission permission);

    /**
     * 接收密码分享（云端）
     * @param shareId 分享ID或Token
     * @return PasswordItem 解密后的密码条目
     */
    PasswordItem receivePasswordShare(String shareId);

    /**
     * 接收离线分享
     * @param encryptedData 加密数据
     * @return PasswordItem 解密后的密码条目
     */
    PasswordItem receiveOfflineShare(String encryptedData);

    /**
     * 保存分享的密码到本地
     * @param shareId 分享ID
     * @return 新密码ID，失败返回-1
     */
    int saveSharedPassword(String shareId);

    /**
     * 撤销密码分享（本地）
     * @param shareId 分享ID
     * @return true表示撤销成功
     */
    boolean revokePasswordShare(String shareId);

    /**
     * 获取我创建的分享列表（本地）
     * @return 分享列表
     */
    List<PasswordShare> getMyShares();

    /**
     * 获取我接收的分享列表（本地）
     * @return 分享列表
     */
    List<PasswordShare> getReceivedShares();

    /**
     * 获取分享详情（本地）
     * @param shareId 分享ID
     * @return 分享详情
     */
    PasswordShare getShareDetails(String shareId);

    /**
     * 添加密码（用于保存分享的密码）
     * @param title 标题
     * @param username 用户名
     * @param password 密码
     * @param url URL
     * @param notes 备注
     * @return 新密码ID，失败返回-1
     */
    int addPassword(String title, String username, String password, String url, String notes);

    // ========== 新增：统一邮箱认证加密数据接口 ==========

    /**
     * 上传加密的设备私钥到云端
     * 用于新设备注册或更新设备信息
     * @param encryptedPrivateKey 加密的私钥数据（Base64）
     * @param iv 初始化向量（Base64）
     * @param salt 盐值（Base64）
     * @return true表示上传成功
     */
    boolean uploadEncryptedPrivateKey(String encryptedPrivateKey, String iv, String salt);

    /**
     * 从云端下载加密的设备私钥
     * 用于登录时获取私钥数据
     * @return 加密的私钥数据（包含 encryptedData, iv, salt），失败返回 null
     */
    com.ttt.safevault.security.KeyManager.EncryptedPrivateKey downloadEncryptedPrivateKey();

    /**
     * 上传加密的密码库数据到云端
     * 用于同步密码数据到云端
     * @param encryptedVaultData 加密的密码库数据（Base64）
     * @param iv 初始化向量（Base64）
     * @return true表示上传成功
     */
    boolean uploadEncryptedVaultData(String encryptedVaultData, String iv);

    /**
     * 从云端下载加密的密码库数据
     * 用于同步密码数据到本地
     * @return 加密的密码库数据（包含 encryptedData, iv），失败返回 null
     */
    EncryptedVaultData downloadEncryptedVaultData();

    /**
     * 加密后的密码库数据封装类
     */
    class EncryptedVaultData {
        public String encryptedData;
        public String iv;
        public String version;

        public EncryptedVaultData(String encryptedData, String iv, String version) {
            this.encryptedData = encryptedData;
            this.iv = iv;
            this.version = version;
        }
    }
}