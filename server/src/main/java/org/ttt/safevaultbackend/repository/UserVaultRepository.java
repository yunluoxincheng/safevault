package org.ttt.safevaultbackend.repository;

import org.ttt.safevaultbackend.entity.UserVault;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户密码库仓库
 */
@Repository
public interface UserVaultRepository extends JpaRepository<UserVault, String> {

    /**
     * 根据用户 ID 查找密码库
     * @param userId 用户 ID
     * @return 密码库（如果存在）
     */
    Optional<UserVault> findByUserId(String userId);

    /**
     * 检查用户是否有密码库
     * @param userId 用户 ID
     * @return 是否存在
     */
    boolean existsByUserId(String userId);

    /**
     * 删除用户的密码库
     * @param userId 用户 ID
     */
    void deleteByUserId(String userId);
}
