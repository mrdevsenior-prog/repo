package pe.com.scotiabank.tapp.audit.application.model;

import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;

/** The provider code is integration metadata; only adapters interpret its transport meaning. */
public record AuditSubmission(AuditResult result, int providerStatusCode) {}
