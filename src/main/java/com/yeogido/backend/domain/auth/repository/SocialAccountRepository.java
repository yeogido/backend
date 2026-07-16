package com.yeogido.backend.domain.auth.repository;

import com.yeogido.backend.domain.auth.entity.SocialAccount;
import com.yeogido.backend.domain.auth.enums.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

  Optional<SocialAccount> findByProviderAndProviderId(SocialProvider provider, String providerId);
}
