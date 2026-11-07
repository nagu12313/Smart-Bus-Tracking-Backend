-- ============================================================
-- Smart School & College Bus Tracking System
-- Flyway Migration V6: Support Ticket System
-- Database: Aiven Cloud MySQL (defaultdb)
-- Author: EduTrack Platform
-- ============================================================

-- ============================================================
-- TABLE 1: support_tickets
-- Core helpdesk tickets created by PARENT, STUDENT, DRIVER or ADMIN
-- ============================================================
CREATE TABLE IF NOT EXISTS support_tickets (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_number   VARCHAR(30)     NOT NULL UNIQUE,
    user_id         BIGINT          NOT NULL,
    category        VARCHAR(50)     NOT NULL,
    subject         VARCHAR(200)    NOT NULL,
    description     TEXT            NOT NULL,
    status          VARCHAR(30)     NOT NULL DEFAULT 'OPEN',
    priority        VARCHAR(20)     NOT NULL DEFAULT 'MEDIUM',
    bus_id          BIGINT          NULL,
    trip_id         BIGINT          NULL,
    assigned_to     BIGINT          NULL,
    resolved_at     DATETIME        NULL,
    closed_at       DATETIME        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id)     REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (bus_id)      REFERENCES buses(id) ON DELETE SET NULL,
    FOREIGN KEY (trip_id)     REFERENCES trips(id) ON DELETE SET NULL,
    FOREIGN KEY (assigned_to) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_st_user     (user_id),
    INDEX idx_st_status   (status),
    INDEX idx_st_priority (priority),
    INDEX idx_st_category (category),
    INDEX idx_st_assigned (assigned_to),
    INDEX idx_st_created  (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 2: support_messages
-- Threaded conversation messages between user and administrator
-- ============================================================
CREATE TABLE IF NOT EXISTS support_messages (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT          NOT NULL,
    sender_id       BIGINT          NOT NULL,
    message         TEXT            NOT NULL,
    is_admin_reply  BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES support_tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id)           ON DELETE CASCADE,
    INDEX idx_sm_ticket (ticket_id),
    INDEX idx_sm_sender (sender_id),
    INDEX idx_sm_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 3: support_attachments
-- Screenshots and documents attached to tickets or messages
-- ============================================================
CREATE TABLE IF NOT EXISTS support_attachments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT          NOT NULL,
    message_id      BIGINT          NULL,
    file_name       VARCHAR(255)    NOT NULL,
    file_type       VARCHAR(100)    NOT NULL,
    file_size       BIGINT          NOT NULL,
    file_data       LONGTEXT        NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id)  REFERENCES support_tickets(id)  ON DELETE CASCADE,
    FOREIGN KEY (message_id) REFERENCES support_messages(id) ON DELETE SET NULL,
    INDEX idx_sa_ticket (ticket_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================================
-- TABLE 4: support_ticket_history
-- Full audit log of status, priority, and assignment changes
-- ============================================================
CREATE TABLE IF NOT EXISTS support_ticket_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id       BIGINT          NOT NULL,
    changed_by      BIGINT          NOT NULL,
    action          VARCHAR(50)     NOT NULL,
    old_value       VARCHAR(100)    NULL,
    new_value       VARCHAR(100)    NULL,
    notes           VARCHAR(255)    NULL,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id)  REFERENCES support_tickets(id) ON DELETE CASCADE,
    FOREIGN KEY (changed_by) REFERENCES users(id)           ON DELETE CASCADE,
    INDEX idx_sth_ticket (ticket_id),
    INDEX idx_sth_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
