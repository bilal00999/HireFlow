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
            AND (COALESCE(:keyword, '') = ''
              OR LOWER(COALESCE(j.title, '')) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%'))
              OR LOWER(COALESCE(j.description, '')) LIKE LOWER(CONCAT('%', COALESCE(:keyword, ''), '%')))
            AND (COALESCE(:type, '') = '' OR j.jobType = :type)
            AND (COALESCE(:location, '') = ''
              OR LOWER(COALESCE(j.location, '')) LIKE LOWER(CONCAT('%', COALESCE(:location, ''), '%')))
            ORDER BY j.createdAt DESC
            """)
    Page<Job> search(@Param("keyword") String keyword,
                     @Param("type") String type,
                     @Param("location") String location,
                     Pageable pageable);

    /** All jobs posted by one company (HR's own listings), newest first. */
    List<Job> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
}
