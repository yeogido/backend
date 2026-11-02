package com.yeogido.backend.domain.user.repository;

import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  boolean existsByFriendCode(String friendCode);

  Optional<User> findByFriendCodeAndStatus(String friendCode, UserStatus status);
}
