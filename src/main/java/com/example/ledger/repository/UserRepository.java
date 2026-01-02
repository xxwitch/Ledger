package com.example.ledger.repository;

/**
 * @author 霜月
 * @create 2025/12/9 23:13
 */

import com.example.ledger.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameAndDeletedFalse(String username);

    boolean existsByUsername(String username);

    @Query("SELECT u FROM User u WHERE u.username = :username AND u.status = 1 AND u.deleted = false")
    Optional<User> findActiveUserByUsername(@Param("username") String username);

    /**
     * 根据ID查找未删除的用户
     */
    @Query("SELECT u FROM User u WHERE u.id = :id AND u.deleted = false")
    Optional<User> findByIdAndDeletedFalse(@Param("id") Long id);

    /**
     * 查找所有未删除的用户（分页）
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false")
    Page<User> findAllActiveUsers(Pageable pageable);

    /**
     * 根据条件查询用户（分页）
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false " +
            "AND (:username IS NULL OR u.username LIKE CONCAT('%', :username, '%')) " +
            "AND (:userType IS NULL OR u.userType = :userType) " +
            "AND (:status IS NULL OR u.status = :status)")
    Page<User> findUsersByConditions(@Param("username") String username,
                                     @Param("userType") String userType,
                                     @Param("status") Integer status,
                                     Pageable pageable);

    /**
     * 搜索用户（优化版）- 支持OR逻辑搜索用户名和昵称
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false " +
            "AND (:keyword IS NULL OR " +
            "     (u.username LIKE CONCAT('%', :keyword, '%') OR " +
            "      u.nickname LIKE CONCAT('%', :keyword, '%'))) " +
            "AND (:userType IS NULL OR u.userType = :userType) " +
            "AND (:status IS NULL OR u.status = :status) " +
            "ORDER BY u.createTime DESC")
    Page<User> searchUsers(
            @Param("keyword") String keyword,
            @Param("userType") String userType,
            @Param("status") Integer status,
            Pageable pageable);

    /**
     * 搜索用户列表（不分页）- 支持OR逻辑
     */
    @Query("SELECT u FROM User u WHERE u.deleted = false " +
            "AND (:keyword IS NULL OR " +
            "     (u.username LIKE CONCAT('%', :keyword, '%') OR " +
            "      u.nickname LIKE CONCAT('%', :keyword, '%'))) " +
            "AND (:userType IS NULL OR u.userType = :userType) " +
            "AND (:status IS NULL OR u.status = :status) " +
            "ORDER BY u.createTime DESC")
    List<User> searchUsersList(
            @Param("keyword") String keyword,
            @Param("userType") String userType,
            @Param("status") Integer status);
}