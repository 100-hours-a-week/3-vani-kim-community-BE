package com.vani.week4.backend.auth.repository;

import com.vani.week4.backend.auth.entity.Auth;
import com.vani.week4.backend.auth.entity.ProviderType;
import com.vani.week4.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * @author vani
 * @since 10/10/25
 */
@Repository
public interface AuthRepository extends JpaRepository<Auth, String> {
    boolean existsByEmail(String email);

    Optional<Auth> findByEmail(String email);

    Optional<Auth> findByUserAndProvider(User user, ProviderType provider);

    Optional<Auth> findByUserId(String userId);
}

