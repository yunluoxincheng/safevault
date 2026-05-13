package org.ttt.safevaultbackend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.ttt.safevaultbackend.entity.Friendship;

import java.util.List;
import java.util.Optional;

/**
 * 好友关系数据访问层
 */
@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, String> {

    /**
     * 查找用户的所有好友关系（无论用户在关系中的位置）
     *
     * @param userIdA 用户ID
     * @param userIdB 用户ID
     * @return 好友关系列表
     */
    List<Friendship> findByUserIdAOrUserIdB(String userIdA, String userIdB);

    /**
     * 检查两个用户是否已经是好友
     *
     * @param userIdA 第一个用户ID
     * @param userIdB 第二个用户ID
     * @return 如果存在好友关系返回true，否则返回false
     */
    boolean existsByUserIdAAndUserIdB(String userIdA, String userIdB);

    /**
     * 根据双方ID查找好友关系
     *
     * @param userIdA 第一个用户ID
     * @param userIdB 第二个用户ID
     * @return 好友关系对象（如果存在）
     */
    Optional<Friendship> findByUserIdAAndUserIdB(String userIdA, String userIdB);

    /**
     * 删除两个用户之间的好友关系
     *
     * @param userIdA 第一个用户ID
     * @param userIdB 第二个用户ID
     */
    void deleteByUserIdAAndUserIdB(String userIdA, String userIdB);
}
