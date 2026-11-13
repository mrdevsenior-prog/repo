package pe.com.scotiabank.tapp.audit.domain;

import java.time.Instant;
import java.util.List;

public record AuditAnalytics(AuditSummary summary, List<Bucket> daily, List<Bucket> applications,
                             List<Bucket> priorities, List<Bucket> actions, List<Bucket> hours,
                             long undated, String rangeStart, String rangeEnd, String timezone,
                             Instant generatedAt) {
  public AuditAnalytics {
    daily = List.copyOf(daily);
    applications = List.copyOf(applications);
    priorities = List.copyOf(priorities);
    actions = List.copyOf(actions);
    hours = List.copyOf(hours);
  }

  public record Bucket(String key, long count) {}
}
