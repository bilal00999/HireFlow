package com.example.demo.ats;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AtsResultRepository extends JpaRepository<AtsResult, UUID> {

    /** One ATS result per application; used by the HR score view. */
    Optional<AtsResult> findByApplicationId(UUID applicationId);

    boolean existsByApplicationId(UUID applicationId);
}
