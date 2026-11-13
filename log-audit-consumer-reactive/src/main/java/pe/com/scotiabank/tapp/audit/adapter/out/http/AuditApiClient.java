package pe.com.scotiabank.tapp.audit.adapter.out.http;

import io.netty.channel.ConnectTimeoutException;
import jakarta.validation.Validator;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import pe.com.scotiabank.tapp.audit.adapter.common.http.AuditHttpHeaderConstants;
import pe.com.scotiabank.tapp.audit.adapter.out.http.config.AuditApiProperties;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiResponse;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException;
import pe.com.scotiabank.tapp.audit.application.exception.AuditDeliveryException.FailureKind;
import pe.com.scotiabank.tapp.audit.application.model.AuditSubmission;
import pe.com.scotiabank.tapp.audit.application.port.out.SendAuditPort;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import reactor.core.publisher.Mono;

@Component
public class AuditApiClient implements SendAuditPort {

  private static final byte[] EMPTY_BODY = new byte[0];

  private final WebClient webClient;
  private final AuditApiProperties properties;
  private final Validator validator;
  private final AuditApiMapper mapper;

  public AuditApiClient(
      WebClient webClient,
      AuditApiProperties properties,
      Validator validator,
      AuditApiMapper mapper) {
    this.webClient = webClient.mutate().build();
    this.properties = properties;
    this.validator = validator;
    this.mapper = mapper;
  }

  @Override
  public Mono<AuditSubmission> send(AuditEvent event, AuditContext context) {
    return Mono.defer(
            () ->
                webClient
                    .post()
                    .uri(properties.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(AuditHttpHeaderConstants.API_VERSION, context.apiVersion())
                    .header(AuditHttpHeaderConstants.CHANNEL_ID, context.channelId())
                    .header(
                        AuditHttpHeaderConstants.ORIGINATING_APPL_CODE,
                        context.originatingApplCode())
                    .header(AuditHttpHeaderConstants.COUNTRY_CODE, context.countryCode())
                    .bodyValue(mapper.toRequest(event))
                    .exchangeToMono(this::handleResponse))
        .timeout(properties.connectTimeout().plus(properties.readTimeout()))
        .onErrorMap(WebClientRequestException.class, this::requestFailure)
        .onErrorMap(TimeoutException.class, AuditApiClient::timeoutFailure);
  }

  private Mono<AuditSubmission> handleResponse(ClientResponse response) {
    int status = response.statusCode().value();
    if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
      return response
          .bodyToMono(byte[].class)
          .defaultIfEmpty(EMPTY_BODY)
          .map(body -> providerFailure(response, body, null))
          .onErrorResume(
              error ->
                  isTimeout(error)
                      ? Mono.error(timeoutFailure(error))
                      : Mono.just(providerFailure(response, EMPTY_BODY, error)))
          .flatMap(Mono::error);
    }
    if (!response.statusCode().is2xxSuccessful()) {
      return response.releaseBody().then(Mono.error(invalidResponse(status, null)));
    }

    return response
        .bodyToMono(AuditApiResponse.class)
        .switchIfEmpty(Mono.error(invalidResponse(status, null)))
        .map(
            body -> {
              if (!validator.validate(body).isEmpty()) {
                throw invalidResponse(status, null);
              }
              return new AuditSubmission(mapper.toDomain(body), status);
            })
        .onErrorMap(
            error -> !(error instanceof AuditDeliveryException),
            error -> isTimeout(error) ? timeoutFailure(error) : invalidResponse(status, error));
  }

  private static AuditDeliveryException providerFailure(
      ClientResponse response, byte[] body, Throwable readFailure) {
    int status = response.statusCode().value();
    HttpStatus knownStatus = HttpStatus.resolve(status);
    WebClientResponseException cause =
        WebClientResponseException.create(
            status,
            knownStatus == null ? "Upstream HTTP error" : knownStatus.getReasonPhrase(),
            response.headers().asHttpHeaders(),
            body,
            StandardCharsets.UTF_8,
            response.request());
    if (readFailure != null) {
      // Oversized or unreadable error bodies are discarded; retain their cause for local debugging.
      cause.initCause(readFailure);
    }
    FailureKind kind =
        response.statusCode().is4xxClientError()
            ? FailureKind.REJECTED
            : FailureKind.PROVIDER_FAILURE;
    String message =
        kind == FailureKind.REJECTED
            ? "La API de auditoría rechazó la solicitud."
            : "La API de auditoría presentó un error al procesar la solicitud.";
    return new AuditDeliveryException(kind, message, status, cause);
  }

  private AuditDeliveryException requestFailure(WebClientRequestException exception) {
    if (isTimeout(exception)) {
      return timeoutFailure(exception);
    }
    return new AuditDeliveryException(
        FailureKind.UNAVAILABLE, "No se pudo conectar con la API de auditoría.", null, exception);
  }

  private static AuditDeliveryException invalidResponse(int status, Throwable cause) {
    return new AuditDeliveryException(
        FailureKind.INVALID_RESPONSE,
        "La API de auditoría devolvió una respuesta incompatible con el contrato.",
        status,
        cause);
  }

  private static AuditDeliveryException timeoutFailure(Throwable cause) {
    return new AuditDeliveryException(
        FailureKind.TIMEOUT, "La API de auditoría excedió el tiempo de espera.", null, cause);
  }

  private static boolean isTimeout(Throwable exception) {
    for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
      if (cause instanceof TimeoutException
          || cause instanceof SocketTimeoutException
          || cause instanceof ConnectTimeoutException
          || cause instanceof io.netty.handler.timeout.TimeoutException) {
        return true;
      }
    }
    return false;
  }
}
