package edu.cit.hopista.budgetmate.features.auth.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import edu.cit.hopista.budgetmate.features.auth.entity.RevokedToken;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, Long> {

    boolean existsByToken(String token);

    void deleteByExpiresAtBefore(LocalDateTime threshold);
}
