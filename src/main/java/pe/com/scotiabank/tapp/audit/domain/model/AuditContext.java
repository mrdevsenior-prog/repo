package pe.com.scotiabank.tapp.audit.domain.model;

public record AuditContext(
    String apiVersion, String channelId, String originatingApplCode, String countryCode) {}
