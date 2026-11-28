package com.farmcrop.auth.repository;

import com.farmcrop.auth.entity.UserAuthIdentity;
import com.farmcrop.auth.entity.UserAuthIdentity.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAuthIdentityRepository extends JpaRepository<UserAuthIdentity, Long> {

    Optional<UserAuthIdentity> findByProviderAndProviderSubject(Provider provider, String providerSubject);

    boolean existsByUserIdAndProvider(Long userId, Provider provider);
}
