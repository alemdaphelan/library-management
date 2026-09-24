package com.huit.library.modules.auth.repository;

import com.huit.library.modules.auth.entity.StaffRoleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StaffRoleRepository extends JpaRepository<StaffRoleEntity, String> {
}
