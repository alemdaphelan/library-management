package com.huit.library.modules.auth.repository;

import java.util.UUID;

import com.huit.library.modules.auth.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    java.util.Optional<UserEntity> findByEmail(String email);
    java.util.Optional<UserEntity> findByMssv(String mssv);
    java.util.Optional<UserEntity> findFirstByEmailOrMssv(String email, String mssv);
}
