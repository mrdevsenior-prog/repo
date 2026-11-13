package pe.com.scotiabank.tapp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogResponse;
import pe.com.scotiabank.tapp.audit.adapter.in.web.mapper.AuditWebMapper;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiRequest;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiResponse;
import pe.com.scotiabank.tapp.audit.adapter.out.http.mapper.AuditApiMapper;
import pe.com.scotiabank.tapp.audit.domain.model.AuditContext;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class AuditMappersTest {

  private static final String APPLICATION_CODE = "TAPP";
  private static final String CHANNEL_ID = "channel";
  private static final String REPEATED_HEADER = "same";

  private final AuditWebMapper webMapper = Mappers.getMapper(AuditWebMapper.class);
  private final AuditApiMapper apiMapper = Mappers.getMapper(AuditApiMapper.class);
  private final JsonMapper json =
      JsonMapper.builder().disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE).build();

  @Test
  void mapsAllFourHeadersWithoutChangingTheirValuesOrPositions() {
    assertThat(webMapper.toContext(" v1 ", "mobile", APPLICATION_CODE, "PE"))
        .isEqualTo(new AuditContext(" v1 ", "mobile", APPLICATION_CODE, "PE"));
  }

  @Test
  void preservesRepeatedAndNullableHeaderValuesForCallerValidation() {
    assertThat(
            Arrays.asList(
                webMapper.toContext(
                    REPEATED_HEADER, REPEATED_HEADER, REPEATED_HEADER, REPEATED_HEADER),
                webMapper.toContext(null, CHANNEL_ID, APPLICATION_CODE, "PE"),
                webMapper.toContext("v1", null, APPLICATION_CODE, "PE"),
                webMapper.toContext("v1", CHANNEL_ID, null, "PE"),
                webMapper.toContext("v1", CHANNEL_ID, APPLICATION_CODE, null),
                webMapper.toContext(null, null, null, null)))
        .containsExactly(
            new AuditContext(REPEATED_HEADER, REPEATED_HEADER, REPEATED_HEADER, REPEATED_HEADER),
            new AuditContext(null, CHANNEL_ID, APPLICATION_CODE, "PE"),
            new AuditContext("v1", null, APPLICATION_CODE, "PE"),
            new AuditContext("v1", CHANNEL_ID, null, "PE"),
            new AuditContext("v1", CHANNEL_ID, APPLICATION_CODE, null),
            null);
  }

  @Test
  void mapsTheCompleteAuditContractWithTwentyDetailsAndOriginalOffsets() throws IOException {
    String fixture = fixture();
    AuditLogRequest request = json.readValue(fixture, AuditLogRequest.class);
    AuditEvent event = webMapper.toDomain(request);
    AuditApiRequest forwarded = apiMapper.toRequest(event);

    assertThat(
            tuple(
                event,
                forwarded.eventMessage().channelPayloadDetail().size(),
                forwarded.logTimestamp(),
                forwarded.eventTimestamp(),
                json.readTree(json.writeValueAsString(forwarded)),
                json.readTree(json.writeValueAsString(request))))
        .isEqualTo(
            tuple(
                expectedFixtureEvent(),
                20,
                OffsetDateTime.parse("2026-07-09T15:19:59-05:00"),
                OffsetDateTime.parse("2026-07-09T15:19:58-05:00"),
                json.readTree(fixture),
                json.readTree(fixture)));
  }

  @Test
  void preservesDynamicHeaderObjectsArraysNumbersBooleansAndNullWithoutMutatingInput()
      throws IOException {
    ObjectNode input = (ObjectNode) json.readTree(fixture());
    ObjectNode message = (ObjectNode) input.get("event_message");
    JsonNode dynamicHeader =
        json.readTree(
            """
            {"header":"Pago dinámico","amount":150.75,"enabled":true,"optional":null,
             "merchant":{"name":"Comercio Perú","labels":["P2M","TAPP"]},
             "steps":[{"code":"CREDIT","ok":true},7,null,["nested","array"]]}
            """);
    message.set("channel_payload_header", dynamicHeader);
    String originalJson = input.toString();
    AuditLogRequest request = json.readValue(originalJson, AuditLogRequest.class);
    AuditEvent event = webMapper.toDomain(request);
    AuditApiRequest forwarded = apiMapper.toRequest(event);

    assertThat(
            tuple(
                json.readTree(json.writeValueAsString(forwarded)),
                json.readTree(json.writeValueAsString(request)),
                json.valueToTree(forwarded.eventMessage().channelPayloadHeader())))
        .isEqualTo(tuple(json.readTree(originalJson), json.readTree(originalJson), dynamicHeader));
  }

  @Test
  void givesDomainItsOwnHeaderMap() throws IOException {
    MappingStages stages = mappingStages();

    assertThat(stages.event().eventMessage().channelPayloadHeader())
        .isNotSameAs(stages.request().eventMessage().channelPayloadHeader());
  }

  @Test
  void givesOutboundRequestItsOwnHeaderMap() throws IOException {
    MappingStages stages = mappingStages();

    assertThat(stages.forwarded().eventMessage().channelPayloadHeader())
        .isNotSameAs(stages.event().eventMessage().channelPayloadHeader());
  }

  @Test
  void givesDomainItsOwnDetailList() throws IOException {
    MappingStages stages = mappingStages();

    assertThat(stages.event().eventMessage().channelPayloadDetail())
        .isNotSameAs(stages.request().eventMessage().channelPayloadDetail());
  }

  @Test
  void givesOutboundRequestItsOwnDetailList() throws IOException {
    MappingStages stages = mappingStages();

    assertThat(stages.forwarded().eventMessage().channelPayloadDetail())
        .isNotSameAs(stages.event().eventMessage().channelPayloadDetail());
  }

  @Test
  void domainMutationsDoNotChangeEitherRequest() throws IOException {
    MappingStages stages = mappingStages();
    stages.event().eventMessage().channelPayloadHeader().put("domain_only", "value");
    stages.event().eventMessage().channelPayloadDetail().clear();

    assertThat(
            tuple(
                json.valueToTree(stages.request()),
                json.valueToTree(stages.forwarded()),
                stages.request().eventMessage().channelPayloadDetail().size(),
                stages.forwarded().eventMessage().channelPayloadDetail().size()))
        .isEqualTo(tuple(json.readTree(fixture()), json.readTree(fixture()), 20, 20));
  }

  @Test
  void returnsNullForAbsentRootMessagesDetailsAndResponses() {
    assertThat(
            Arrays.asList(
                webMapper.toDomain((AuditLogRequest) null),
                webMapper.toDomain((AuditLogRequest.EventMessage) null),
                webMapper.toDomain((AuditLogRequest.PayloadDetail) null),
                apiMapper.toRequest((AuditEvent) null),
                apiMapper.toRequest((AuditEvent.EventMessage) null),
                apiMapper.toRequest((AuditEvent.PayloadDetail) null),
                apiMapper.toDomain((AuditApiResponse) null),
                apiMapper.toDomain((AuditApiResponse.AuditStatus) null),
                webMapper.toResponse((AuditResult) null),
                webMapper.toResponse((AuditResult.AuditStatus) null)))
        .containsOnlyNulls();
  }

  @Test
  void preservesAbsentHeaderAndDetailCollectionsInsteadOfInventingContent() {
    AuditEvent.EventMessage eventMessage = webMapper.toDomain(message(null, null));
    AuditApiRequest.EventMessage forwarded = apiMapper.toRequest(eventMessage);

    assertThat(
            tuple(
                eventMessage.channelPayloadHeader(),
                eventMessage.channelPayloadDetail(),
                forwarded.channelPayloadHeader(),
                forwarded.channelPayloadDetail()))
        .isEqualTo(tuple(null, null, null, null));
  }

  @Test
  void preservesExplicitlyEmptyHeaderAndDetailCollections() {
    AuditEvent.EventMessage eventMessage =
        webMapper.toDomain(message(new LinkedHashMap<>(), new ArrayList<>()));
    AuditApiRequest.EventMessage forwarded = apiMapper.toRequest(eventMessage);

    assertThat(
            tuple(
                eventMessage.channelPayloadHeader(),
                eventMessage.channelPayloadDetail(),
                forwarded.channelPayloadHeader(),
                forwarded.channelPayloadDetail()))
        .isEqualTo(tuple(Map.of(), List.of(), Map.of(), List.of()));
  }

  @Test
  void preservesDetailOrderIncludingNullEntriesSoValidationCanRejectThem() {
    AuditEvent.EventMessage eventMessage =
        webMapper.toDomain(
            message(
                Map.of("header", "Pago"),
                Arrays.asList(
                    new AuditLogRequest.PayloadDetail("amount", "150.00"),
                    null,
                    new AuditLogRequest.PayloadDetail("currency", "PEN"))));

    assertThat(
            tuple(
                eventMessage.channelPayloadDetail(),
                apiMapper.toRequest(eventMessage).channelPayloadDetail()))
        .isEqualTo(
            tuple(
                Arrays.asList(
                    new AuditEvent.PayloadDetail("amount", "150.00"),
                    null,
                    new AuditEvent.PayloadDetail("currency", "PEN")),
                Arrays.asList(
                    new AuditApiRequest.PayloadDetail("amount", "150.00"),
                    null,
                    new AuditApiRequest.PayloadDetail("currency", "PEN"))));
  }

  @Test
  void mapsAllProviderStatusesInOrderThroughBothBoundaries() {
    AuditApiResponse provider =
        new AuditApiResponse(
            List.of(
                new AuditApiResponse.AuditStatus("OK"),
                new AuditApiResponse.AuditStatus("ACCEPTED"),
                new AuditApiResponse.AuditStatus("QUEUED")));
    AuditResult result = apiMapper.toDomain(provider);
    AuditLogResponse response = webMapper.toResponse(result);

    assertThat(tuple(result, response, json.readTree(json.writeValueAsString(response))))
        .isEqualTo(
            tuple(
                new AuditResult(
                    List.of(
                        new AuditResult.AuditStatus("OK"),
                        new AuditResult.AuditStatus("ACCEPTED"),
                        new AuditResult.AuditStatus("QUEUED"))),
                new AuditLogResponse(
                    List.of(
                        new AuditLogResponse.AuditStatus("OK"),
                        new AuditLogResponse.AuditStatus("ACCEPTED"),
                        new AuditLogResponse.AuditStatus("QUEUED"))),
                json.readTree(
                    """
                    {"data":[{"status":"OK"},{"status":"ACCEPTED"},{"status":"QUEUED"}]}
                    """)));
  }

  @Test
  void preservesAbsentStatusListsForProviderContractValidation() {
    AuditResult result = apiMapper.toDomain(new AuditApiResponse(null));

    assertThat(tuple(result, webMapper.toResponse(result)))
        .isEqualTo(tuple(new AuditResult(null), new AuditLogResponse(null)));
  }

  @Test
  void preservesEmptyStatusListsWithoutInventingSuccessfulResults() {
    AuditResult result = apiMapper.toDomain(new AuditApiResponse(List.of()));

    assertThat(tuple(result, webMapper.toResponse(result)))
        .isEqualTo(tuple(new AuditResult(List.of()), new AuditLogResponse(List.of())));
  }

  @Test
  void preservesNullStatusEntriesAndValuesForProviderContractValidation() {
    AuditResult result =
        apiMapper.toDomain(
            new AuditApiResponse(
                Arrays.asList(
                    new AuditApiResponse.AuditStatus("OK"),
                    null,
                    new AuditApiResponse.AuditStatus(null))));

    assertThat(tuple(result.data(), webMapper.toResponse(result).data()))
        .isEqualTo(
            tuple(
                Arrays.asList(
                    new AuditResult.AuditStatus("OK"), null, new AuditResult.AuditStatus(null)),
                Arrays.asList(
                    new AuditLogResponse.AuditStatus("OK"),
                    null,
                    new AuditLogResponse.AuditStatus(null))));
  }

  private MappingStages mappingStages() throws IOException {
    AuditLogRequest request = json.readValue(fixture(), AuditLogRequest.class);
    AuditEvent event = webMapper.toDomain(request);
    return new MappingStages(request, event, apiMapper.toRequest(event));
  }

  private static AuditLogRequest.EventMessage message(
      Map<String, Object> header, List<AuditLogRequest.PayloadDetail> details) {
    return new AuditLogRequest.EventMessage(
        APPLICATION_CODE,
        "TAPP Mobile",
        "CREDIT",
        "Crédito",
        "DNI",
        "12345678",
        "device",
        "operation",
        "cams",
        "account",
        header,
        details);
  }

  private static AuditEvent expectedFixtureEvent() {
    List<AuditEvent.PayloadDetail> details =
        IntStream.rangeClosed(1, 20)
            .mapToObj(
                index ->
                    new AuditEvent.PayloadDetail(
                        "Campo Key " + index + " Campo Key " + index + " Campo Key " + index,
                        "Campo Value " + index + " Campo Value " + index + " Campo Value " + index))
            .toList();
    AuditEvent.EventMessage message =
        new AuditEvent.EventMessage(
            APPLICATION_CODE,
            "Telebanking 504",
            "B3QXRCR",
            "Registro de credenciales exitosamente :D",
            "DNI",
            "33445566",
            "Campo device id",
            "Campo operation number",
            "campo-cams-id-7dec-11d0-a765-00a0c91e6bf6",
            "Campo bt account",
            Map.of(
                "header",
                "Prueba con Miguel",
                "description",
                "Campo de descripción del detalle de la operacion - description - "
                    + "Description aún mas larga"),
            details);
    return new AuditEvent(
        OffsetDateTime.parse("2026-07-09T15:19:59-05:00"),
        "AUDIT_LOG",
        OffsetDateTime.parse("2026-07-09T15:19:58-05:00"),
        "FATAL",
        "MEDIUM",
        "SUSPENDED",
        "Campo nombre de la cuenta",
        "Campo source host name",
        "888.888.888.888",
        "Campo destination host name",
        "999.999.999.999",
        "Campo session identifier",
        "ENUMERATE",
        message);
  }

  private static String fixture() throws IOException {
    try (InputStream input =
        Objects.requireNonNull(
            AuditMappersTest.class.getResourceAsStream("/audit-request.json"),
            "Missing audit-request.json test fixture")) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private record MappingStages(
      AuditLogRequest request, AuditEvent event, AuditApiRequest forwarded) {}
}
