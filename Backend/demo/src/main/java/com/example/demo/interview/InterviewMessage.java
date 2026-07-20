package com.example.demo.interview;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A single turn in an interview's conversation log. Mirrors the
 * `interview_messages` table in 03-DATABASE-DESIGN.md.
 *
 * <p>{@link #role} is either {@code "ai"} or {@code "candidate"}, and
 * {@link #orderIndex} preserves conversation order for replay in the HR
 * transcript view.
 */
@Entity
@Table(name = "interview_messages")
@Getter
@Setter
public class InterviewMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    /** "ai" or "candidate". */
    @Column(nullable = false, length = 20)
    private String role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt = LocalDateTime.now();

    @Column(name = "order_index", nullable = false)
    private int orderIndex;
}
