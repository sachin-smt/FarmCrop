package com.farmcrop.user.repository;

import com.farmcrop.common.enums.UserRole;
import com.farmcrop.user.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByRoleName(UserRole roleName);
}
