package pe.com.scotiabank.tapp.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogRequest;
import pe.com.scotiabank.tapp.audit.adapter.in.web.dto.AuditLogResponse;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiRequest;
import pe.com.scotiabank.tapp.audit.adapter.out.http.dto.AuditApiResponse;
import pe.com.scotiabank.tapp.audit.domain.model.AuditEvent;
import pe.com.scotiabank.tapp.audit.domain.model.AuditResult;

class AuditCollectionCopiesTest {

  private static final String ORIGINAL_VALUE = "original";
  private static final String DETAIL_KEY = "transaction";
  private static final String CHILD_MAP = "merchant";
  private static final String CHILD_LIST = "steps";
  private static final String OPTIONAL_VALUE = "optional";
  private static final String CASE_NAME = "{0}: {1}";

  @ParameterizedTest(name = CASE_NAME)
  @MethodSource("headerMutationCases")
  void copiesHeaderRecursivelyWhenConstructed(MessageKind kind, HeaderMutation mutation) {
    Map<String, Object> input = dynamicHeader();
    MessageSubject subject = kind.create(input, false);
    mutation.apply(input);

    assertThat(subject.headerReader().get()).isEqualTo(dynamicHeader());
  }

  @ParameterizedTest(name = CASE_NAME)
  @MethodSource("headerMutationCases")
  void copiesHeaderRecursivelyWhenRead(MessageKind kind, HeaderMutation mutation) {
    MessageSubject subject = kind.create(dynamicHeader(), false);
    mutation.apply(subject.headerReader().get());

    assertThat(subject.headerReader().get()).isEqualTo(dynamicHeader());
  }

  @ParameterizedTest
  @EnumSource(MessageKind.class)
  void copiesDetailListWhenConstructed(MessageKind kind) {
    MessageSubject subject = kind.create(dynamicHeader(), false);
    List<?> expected = new ArrayList<>(subject.inputDetails());
    subject.inputDetails().clear();

    assertThat(subject.detailReader().get()).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(MessageKind.class)
  void copiesDetailListWhenRead(MessageKind kind) {
    MessageSubject subject = kind.create(dynamicHeader(), false);
    List<?> expected = new ArrayList<>(subject.inputDetails());
    subject.detailReader().get().clear();

    assertThat(subject.detailReader().get()).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(MessageKind.class)
  void preservesNullableDetailElements(MessageKind kind) {
    MessageSubject subject = kind.create(dynamicHeader(), false);

    assertThat(subject.detailReader().get()).containsNull();
  }

  @ParameterizedTest
  @EnumSource(MessageKind.class)
  void preservesAbsentHeaderAndDetailCollections(MessageKind kind) {
    MessageSubject subject = kind.create(null, true);

    assertThat(tuple(subject.headerReader().get(), subject.detailReader().get()))
        .isEqualTo(tuple(null, null));
  }

  @ParameterizedTest
  @EnumSource(ResponseKind.class)
  void copiesResponseDataWhenConstructed(ResponseKind kind) {
    ResponseSubject subject = kind.create(false);
    List<?> expected = new ArrayList<>(subject.inputData());
    subject.inputData().clear();

    assertThat(subject.dataReader().get()).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(ResponseKind.class)
  void copiesResponseDataWhenRead(ResponseKind kind) {
    ResponseSubject subject = kind.create(false);
    List<?> expected = new ArrayList<>(subject.inputData());
    subject.dataReader().get().clear();

    assertThat(subject.dataReader().get()).isEqualTo(expected);
  }

  @ParameterizedTest
  @EnumSource(ResponseKind.class)
  void preservesNullableResponseElements(ResponseKind kind) {
    ResponseSubject subject = kind.create(false);

    assertThat(subject.dataReader().get()).containsNull();
  }

  @ParameterizedTest
  @EnumSource(ResponseKind.class)
  void preservesAbsentResponseData(ResponseKind kind) {
    assertThat(kind.create(true).dataReader().get()).isNull();
  }

  private static Stream<Arguments> headerMutationCases() {
    return Arrays.stream(MessageKind.values())
        .flatMap(
            kind ->
                Arrays.stream(HeaderMutation.values())
                    .map(mutation -> Arguments.of(kind, mutation)));
  }

  private static Map<String, Object> dynamicHeader() {
    Map<String, Object> merchant = new LinkedHashMap<>();
    merchant.put("name", ORIGINAL_VALUE);
    merchant.put(OPTIONAL_VALUE, null);
    Map<String, Object> step = new LinkedHashMap<>();
    step.put("code", ORIGINAL_VALUE);
    List<Object> nestedSteps = new ArrayList<>(Arrays.asList(ORIGINAL_VALUE, null));
    List<Object> steps = new ArrayList<>(Arrays.asList(ORIGINAL_VALUE, null, step, nestedSteps));
    Map<String, Object> header = new LinkedHashMap<>();
    header.put("header", ORIGINAL_VALUE);
    header.put("amount", 150);
    header.put("enabled", true);
    header.put(OPTIONAL_VALUE, null);
    header.put(CHILD_MAP, merchant);
    header.put(CHILD_LIST, steps);
    return header;
  }

  private static <T> List<T> nullableElements(T element, boolean absent) {
    return absent ? null : new ArrayList<>(Arrays.asList(element, null));
  }

  private enum HeaderMutation {
    ROOT(Map::clear),
    NESTED_MAP(header -> ((Map<?, ?>) header.get(CHILD_MAP)).clear()),
    NESTED_LIST(header -> ((List<?>) header.get(CHILD_LIST)).clear()),
    MAP_INSIDE_LIST(header -> ((Map<?, ?>) ((List<?>) header.get(CHILD_LIST)).get(2)).clear()),
    LIST_INSIDE_LIST(header -> ((List<?>) ((List<?>) header.get(CHILD_LIST)).get(3)).clear());

    private final Consumer<Map<String, Object>> mutation;

    HeaderMutation(Consumer<Map<String, Object>> mutation) {
      this.mutation = mutation;
    }

    void apply(Map<String, Object> header) {
      mutation.accept(header);
    }
  }

  private enum MessageKind {
    INBOUND,
    OUTBOUND,
    DOMAIN;

    MessageSubject create(Map<String, Object> header, boolean absentDetails) {
      return switch (this) {
        case INBOUND -> {
          List<AuditLogRequest.PayloadDetail> details =
              nullableElements(
                  new AuditLogRequest.PayloadDetail(DETAIL_KEY, ORIGINAL_VALUE), absentDetails);
          AuditLogRequest.EventMessage message =
              new AuditLogRequest.EventMessage(
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  header,
                  details);
          yield new MessageSubject(
              details, message::channelPayloadHeader, message::channelPayloadDetail);
        }
        case OUTBOUND -> {
          List<AuditApiRequest.PayloadDetail> details =
              nullableElements(
                  new AuditApiRequest.PayloadDetail(DETAIL_KEY, ORIGINAL_VALUE), absentDetails);
          AuditApiRequest.EventMessage message =
              new AuditApiRequest.EventMessage(
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  header,
                  details);
          yield new MessageSubject(
              details, message::channelPayloadHeader, message::channelPayloadDetail);
        }
        case DOMAIN -> {
          List<AuditEvent.PayloadDetail> details =
              nullableElements(
                  new AuditEvent.PayloadDetail(DETAIL_KEY, ORIGINAL_VALUE), absentDetails);
          AuditEvent.EventMessage message =
              new AuditEvent.EventMessage(
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  ORIGINAL_VALUE,
                  header,
                  details);
          yield new MessageSubject(
              details, message::channelPayloadHeader, message::channelPayloadDetail);
        }
      };
    }
  }

  private enum ResponseKind {
    INBOUND,
    OUTBOUND,
    DOMAIN;

    ResponseSubject create(boolean absentData) {
      return switch (this) {
        case INBOUND -> {
          List<AuditLogResponse.AuditStatus> statuses =
              nullableElements(new AuditLogResponse.AuditStatus(ORIGINAL_VALUE), absentData);
          AuditLogResponse response = new AuditLogResponse(statuses);
          yield new ResponseSubject(statuses, response::data);
        }
        case OUTBOUND -> {
          List<AuditApiResponse.AuditStatus> statuses =
              nullableElements(new AuditApiResponse.AuditStatus(ORIGINAL_VALUE), absentData);
          AuditApiResponse response = new AuditApiResponse(statuses);
          yield new ResponseSubject(statuses, response::data);
        }
        case DOMAIN -> {
          List<AuditResult.AuditStatus> statuses =
              nullableElements(new AuditResult.AuditStatus(ORIGINAL_VALUE), absentData);
          AuditResult response = new AuditResult(statuses);
          yield new ResponseSubject(statuses, response::data);
        }
      };
    }
  }

  private record MessageSubject(
      List<?> inputDetails,
      Supplier<Map<String, Object>> headerReader,
      Supplier<List<?>> detailReader) {}

  private record ResponseSubject(List<?> inputData, Supplier<List<?>> dataReader) {}
}
