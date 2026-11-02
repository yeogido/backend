package com.yeogido.backend.domain.user.repository;

import com.yeogido.backend.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select u from User u where u.id = :id")
  Optional<User> findByIdForTermsAgreement(@Param("id") Long id);

  boolean existsByEmail(String email);

  Optional<User> findByEmail(String email);

  boolean existsByFriendCode(String friendCode);
}
