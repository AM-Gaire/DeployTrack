package com.deploytrack.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Named LogEntry rather than Log to avoid colliding with org.slf4j.Logger
// conventions (a field or import named "Log"/"Logger" right next to this
// class would be confusing) and because "logs" is the table name anyway.
@Entity
@Table(name = "logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deployment_id", nullable = false)
    private Deployment deployment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LogLevel level;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false)
    private Instant timestamp;
}
