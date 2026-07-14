package com.example.demo.job;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface JobRepository extends JpaRepository<Job, UUID> {

    /**
     * Public job search. Only returns ACTIVE jobs. Each filter is optional:
     * when a param is null it is ignored (the OR :param IS NULL trick).
     */
    @Query("""
            SELECT j FROM Job j
            WHERE j.status = 'ACTIVE'
              AND (:keyword IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                                   OR LOWER(j.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:type IS NULL OR j.jobType = :type)
              AND (:location IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', :location, '%')))
            ORDER BY j.createdAt DESC
            """)
    Page<Job> search(@Param("keyword") String keyword,
                     @Param("type") String type,
                     @Param("location") String location,
                     Pageable pageable);

    /** All jobs posted by one company (HR's own listings), newest first. */
    List<Job> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
