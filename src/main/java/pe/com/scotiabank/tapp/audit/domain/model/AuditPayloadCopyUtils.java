package pe.com.scotiabank.tapp.audit.domain.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AuditPayloadCopyUtils {

  private AuditPayloadCopyUtils() {}

  public static Map<String, Object> copyHeader(Map<String, Object> source) {
    return source == null ? null : copyMap(source);
  }

  private static <K> Map<K, Object> copyMap(Map<K, ?> source) {
    Map<K, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> copy.put(key, copyValue(value)));
    return copy;
  }

  private static Object copyValue(Object value) {
    if (value instanceof Map<?, ?> map) {
      return copyMap(map);
    }
    if (value instanceof List<?> list) {
      List<Object> copy = new ArrayList<>(list.size());
      list.forEach(item -> copy.add(copyValue(item)));
      return copy;
    }
    return value;
  }
}
