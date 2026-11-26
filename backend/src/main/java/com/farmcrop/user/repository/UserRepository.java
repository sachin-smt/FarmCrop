package com.farmcrop.user.repository;

import com.farmcrop.common.enums.UserRole;
import com.farmcrop.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Login lookup by normalized (lower-case) username or email, with roles loaded. */
    @Query("select u from User u left join fetch u.roles where u.username = :identifier or u.email = :identifier")
    Optional<User> findForLogin(@Param("identifier") String identifier);

    @Query("select u from User u left join fetch u.roles where u.id = :id")
    Optional<User> findWithRolesById(@Param("id") Long id);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    @Query("select count(u) > 0 from User u join u.roles r where r.roleName = :role")
    boolean existsWithRole(@Param("role") UserRole role);
}
