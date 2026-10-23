package com.chainlance.repository;

import com.chainlance.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** The email column uses a case-insensitive collation, so lookups ignore case. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
