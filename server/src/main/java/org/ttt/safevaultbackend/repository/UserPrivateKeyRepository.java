package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.UserPrivateKey;

import java.util.Optional;

/**
 * 用户加密私钥 Repository
 */
@Repository
public interface UserPrivateKeyRepository extends JpaRepository<UserPrivateKey, String> {

    /**
     * 根据用户 ID 查找私钥
     */
    Optional<UserPrivateKey> findByUserId(String userId);

    /**
     * 检查用户是否存在私钥记录
     */
    boolean existsByUserId(String userId);
}
