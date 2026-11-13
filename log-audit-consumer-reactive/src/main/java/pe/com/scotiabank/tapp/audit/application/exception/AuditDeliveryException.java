package pe.com.scotiabank.tapp.audit.application.exception;

public final class AuditDeliveryException extends RuntimeException {

  public enum FailureKind {
    REJECTED,
    PROVIDER_FAILURE,
    INVALID_RESPONSE,
    UNAVAILABLE,
    TIMEOUT
  }

  private final FailureKind kind;
  private final Integer providerStatusCode;

  public AuditDeliveryException(
      FailureKind kind, String message, Integer providerStatusCode, Throwable cause) {
    super(message, cause);
    this.kind = kind;
    this.providerStatusCode = providerStatusCode;
  }

  public FailureKind kind() {
    return kind;
  }

  public Integer providerStatusCode() {
    return providerStatusCode;
  }
}
