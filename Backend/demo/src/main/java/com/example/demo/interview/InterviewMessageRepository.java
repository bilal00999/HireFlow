package com.example.demo.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, UUID> {

    /** The full transcript for a session, in conversation order. */
    List<InterviewMessage> findBySessionIdOrderByOrderIndexAsc(UUID sessionId);

    /** Count of turns so far — used to assign the next order index. */
    long countBySessionId(UUID sessionId);
}
