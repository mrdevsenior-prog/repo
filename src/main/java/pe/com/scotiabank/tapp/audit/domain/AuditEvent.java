package pe.com.scotiabank.tapp.audit.domain;

import java.time.Instant;

public record AuditEvent(String uid, String accountName, String applicationCode,
                         String operationNumber, String action, String category, String priority,
                         String eventType, Instant eventTimestamp, String description) {}
