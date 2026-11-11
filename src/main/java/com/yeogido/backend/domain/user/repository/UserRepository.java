package com.yeogido.backend.domain.user.repository;

import com.yeogido.backend.domain.user.entity.User;
import com.yeogido.backend.domain.user.enums.UserStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
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

  Optional<User> findByFriendCodeAndStatus(String friendCode, UserStatus status);

  // 전체 회원을 훑어볼 수 없도록 부분 일치 없이 이름 또는 친구코드가 정확히 같은 활성 사용자만 조회
  @Query("""
      select u from User u
      where u.status = com.yeogido.backend.domain.user.enums.UserStatus.ACTIVE
        and u.id <> :userId
        and (u.nickname = :nickname or u.friendCode = :friendCode)
        and (:cursorId is null or u.id > :cursorId)
      order by u.id asc
      """)
  List<User> findSearchableUsers(
      @Param("userId") Long userId,
      @Param("nickname") String nickname,
      @Param("friendCode") String friendCode,
      @Param("cursorId") Long cursorId,
      Pageable pageable
  );
}
