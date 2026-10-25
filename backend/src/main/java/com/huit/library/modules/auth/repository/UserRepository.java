package com.huit.library.modules.auth.repository;

import java.util.UUID;

import com.huit.library.modules.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    java.util.Optional<UserEntity> findByEmail(String email);
    java.util.Optional<UserEntity> findByMssv(String mssv);
    java.util.Optional<UserEntity> findFirstByEmailOrMssv(String email, String mssv);
    java.util.List<UserEntity> findByUserTypeOrderByFullNameAsc(String userType);
    @Query("select user from UserEntity user where upper(user.roleId) in :roleIds")
    java.util.List<UserEntity> findStaffByRoleIds(@Param("roleIds") java.util.Collection<String> roleIds);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByMssvIgnoreCase(String mssv);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select user from UserEntity user where user.userId = :userId")
    java.util.Optional<UserEntity> findByIdForUpdate(@Param("userId") UUID userId);
}
