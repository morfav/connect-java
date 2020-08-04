package cd.connect.opentracing;

import cd.connect.context.ConnectContext;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static cd.connect.opentracing.OpenTracingLogger.WELL_KNOWN_ORIGIN_APP;
import static cd.connect.opentracing.OpenTracingLogger.WELL_KNOWN_REQUEST_ID;
import static cd.connect.opentracing.OpenTracingLogger.WELL_KNOWN_SCENARIO_ID;

/**
 * This is designed to extract the baggage items of the active trace
 * into the logging context, and on activation, the tags go into the logs as well
 *
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class LoggerSpan implements Span, SpanContext {
  private static final Logger log = LoggerFactory.getLogger(LoggerSpan.class);
  private Span wrappedSpan;
  private Map<String, String> baggage = new HashMap<>();
  private Map<String, Object> tags = new HashMap<>();
  private Map<String, Object> logs = new HashMap<>();
  private List<String> events = new ArrayList<>();
  private LoggerSpan priorSpan;
  private boolean priorSpanSetting = false;
  private final String id;
  private AtomicInteger garbageCounter = new AtomicInteger(1);
  private SpanContext wrappedSpanContext;

  public LoggerSpan(LoggerScope priorActiveSpan) {
    this.id = UUID.randomUUID().toString();
    if (priorActiveSpan != null) {
      baggage.putAll(((LoggerSpan)priorActiveSpan.span()).baggage);
    }
  }

  public boolean isFinished() {
    return garbageCounter.get() == 0;
  }

  // if we are doing an extract from a header, the returned spancontext may
  // not be a span, so we may need to set it later once the span builder has
  // created it. in Jaeger for example a spancontext from an extract is just that, it is not a span.
  public LoggerSpan setWrappedSpan(Span wrappedSpan) {
    this.wrappedSpan = wrappedSpan;

    // the wrapped span can be set quite late and from a span context, so
    // we should check if we have no baggage and copy it if we need to
    if (baggage.size() == 0 && wrappedSpan != null) {
      wrappedSpan.context().baggageItems().forEach(e -> this.baggage.put(e.getKey(), e.getValue()));
      updateLoggedBaggage();
    }

    return this;
  }

  public void setWrappedSpanContext(SpanContext ctx) {
    this.wrappedSpanContext = ctx;

    if (baggage.size() == 0 && wrappedSpanContext != null) {
      wrappedSpanContext.baggageItems().forEach(e -> this.baggage.put(e.getKey(), e.getValue()));
      updateLoggedBaggage();
    }
  }

  public SpanContext getWrappedSpanContext() {
    if (this.wrappedSpan != null) {
      return this.wrappedSpan.context();
    } else {
      return this.wrappedSpanContext;
    }
  }

  @Override
  public SpanContext context() {
    return this;
  }

  public LoggerSpan getPriorSpan() {
    return priorSpan;
  }

  void setPriorSpan(LoggerSpan newPriorSpan) {
    LoggerSpan priorSpan = newPriorSpan;

    if (priorSpanSetting) {
      this.priorSpan = newPriorSpan;
    } else {
      priorSpanSetting = true; // detect loops
      if (this.priorSpan != newPriorSpan && newPriorSpan != null) {
        baggage.putAll(priorSpan.baggage);
      }
      if (this.priorSpan != null && this.priorSpan != newPriorSpan && newPriorSpan != null) {
        // this could loop around and around, but the idea is to push the prior span further back
        priorSpan.setPriorSpan(this.priorSpan);
      }
      this.priorSpan = newPriorSpan;
      priorSpanSetting = false; // detect loops
    }
  }

  @Override
  public Span setTag(String key, String value) {
    if (value == null) {
      tags.remove(key);
    } else {
      tags.put(key, value);
    }
    if (wrappedSpan != null) {
      wrappedSpan.setTag(key, value);
    }
    updateLoggedTags();
    return this;
  }

  @Override
  public Span setTag(String key, boolean value) {
    tags.put(key, value);
    if (wrappedSpan != null) {
      wrappedSpan.setTag(key, value);
    }
    updateLoggedTags();
    return this;
  }

  @Override
  public Span setTag(String key, Number value) {
    if (value == null) {
      tags.remove(key);
    } else {
      tags.put(key, value);
    }
    if (wrappedSpan != null) {
      wrappedSpan.setTag(key, value);
    }
    updateLoggedTags();
    return null;
  }

  @Override
  public <T> Span setTag(Tag<T> tag, T t) {
    if (wrappedSpan != null) {
      return wrappedSpan.setTag(tag, t);
    }

    return this;
  }

  @Override
  public Span log(Map<String, ?> fields) {
    fields.forEach((key, value) -> logs.put(key, value));
    if (wrappedSpan != null) {
      wrappedSpan.log(fields);
    }
    updateLoggedMessages();
    return this;
  }

  @Override
  public Span log(long timestampMicroseconds, Map<String, ?> fields) {
    log(fields);
    if (wrappedSpan != null) {
      wrappedSpan.log(timestampMicroseconds, fields);
    }
    updateLoggedMessages();
    return this;
  }

  @Override
  public Span log(String event) {
    this.events.add(event);
    if (wrappedSpan != null) {
      wrappedSpan.log(event);
    }
    return this;
  }

  // 0.3.0 standard, prevents older library usage from barfing
  public Span log(String event, String message) {
    this.events.add(event + ":" + message);
    if (wrappedSpan != null) {
      wrappedSpan.log(event);
    }
    return this;
  }

  @Override
  public Span log(long timestampMicroseconds, String event) {
    this.events.add(event + "@" + timestampMicroseconds);
    if (wrappedSpan != null) {
      wrappedSpan.log(timestampMicroseconds, event);
    }
    return this;
  }

  @Override
  public Span setBaggageItem(String key, String value) {
    // we keep it as well because we cannot ensure the wrapped span is also a spancontext
    baggage.put(key, value);
    if (wrappedSpan != null) {
      wrappedSpan.setBaggageItem(key, value);
    }
    updateLoggedBaggage();
    return this;
  }

  private String tryMany(Map<String, String> source, String ...names) {
    List<String> acutalNames = Arrays.asList(names);

    Map.Entry<String, String> found = source.entrySet().stream()
      .filter(e -> acutalNames.contains(e.getKey()))
      .findFirst()
      .orElse(null);

    if (found != null) {
      return source.remove(found.getKey());
    } else {
      return null;
    }
  }

  // there is no way to set baggage on an item until after you have started it and made it active, so a typical
  // trace will not log its own baggage
  private void updateLoggedBaggage() {
    if (id.equals(MDC.get(OPENTRACING_ID))) {
      Map<String, String> clonedBaggage = new HashMap<>(baggage);

      String requestId = tryMany(clonedBaggage, WELL_KNOWN_REQUEST_ID, "requestid", "request-id");
      if (requestId != null) {
        ConnectContext.requestId.set(requestId);
      }
      String scenarioId = tryMany(clonedBaggage, WELL_KNOWN_SCENARIO_ID, "scenarioId", "scenario-id");
      if (scenarioId != null) {
        ConnectContext.scenarioId.set(scenarioId);
      }

      clonedBaggage.remove(WELL_KNOWN_ORIGIN_APP);

      MDC.put(OPENTRACING_BAGGAGE, ObjectMapperProvider.wrapObject(clonedBaggage));
    }
  }

  private void updateLoggedMessages() {
    if (id.equals(MDC.get(OPENTRACING_ID))) {
      MDC.put(OPENTRACING_LOG_MESSAGES, ObjectMapperProvider.wrapObject(logs));
    }
  }

  private void updateLoggedTags() {
    if (id.equals(MDC.get(OPENTRACING_ID))) {
      MDC.put(OPENTRACING_TAGS, ObjectMapperProvider.wrapObject(tags));
    }
  }

  static final String OPENTRACING_BAGGAGE = "opentracing.baggage";
  static final String OPENTRACING_LOG_MESSAGES = "opentracing.logs";
  static final String OPENTRACING_TAGS = "opentracing.tags";
  static final String OPENTRACING_ID = "opentracing.id";
  static final String OPENTRACING_APPNAME = "opentracing.appName";
  static final String OPENTRACING_ORIGIN_APPNAME = "opentracing.orginApp";


  void setActive(String appName) {
    MDC.put(OPENTRACING_ID, this.id);
    updateLoggedBaggage();
    updateLoggedMessages();
    updateLoggedTags();
    MDC.put(OPENTRACING_APPNAME, appName);

    String originApp = getBaggageItem(WELL_KNOWN_ORIGIN_APP);

    if (originApp != null) {
      MDC.put(OPENTRACING_ORIGIN_APPNAME, originApp);
    }
  }

  void removeActive() {
    // don't remove if not ours
    if (id.equals(MDC.get(OPENTRACING_ID))) {
      ConnectContext.requestId.remove();
      ConnectContext.scenarioId.remove();
      MDC.getCopyOfContextMap().keySet().forEach(k -> {
        if (k.startsWith("opentracing.")) {
          MDC.remove(k);
        }
      });
    }
  }

  String getId() {
    return id;
  }

  Span getWrappedSpan() {
    return wrappedSpan;
  }

  @Override
  public String getBaggageItem(String key) {
    if (wrappedSpan != null) {
      return wrappedSpan.getBaggageItem(key);
    } else {
      return baggage.get(key);
    }
  }

  @Override
  public Span setOperationName(String operationName) {
    return this;
  }

  public void finish(boolean callFinishOnWrappedSpan) {
    if (garbageCounter.decrementAndGet() <= 0) {
//      log.debug("logger finish ok");
      removeActive();
    } else {
//      try {
//        throw new RuntimeException("here");
//      } catch (RuntimeException re) {
//        log.debug("logger ignoring finish - {}: {}", garbageCounter.get(), id, re);
//      }
    }

    if (wrappedSpan != null && callFinishOnWrappedSpan) {
      wrappedSpan.finish();
    }
  }

  @Override
  public void finish() {
    finish(true);
  }

  @Override
  public void finish(long finishMicros) {
    finish();
  }

  protected void incInterest() {
    garbageCounter.incrementAndGet();
//    if (garbageCounter.get() > 1) {
//      log.info("here");
//    }
//    try {
//      throw new RuntimeException("here");
//    } catch (RuntimeException re) {
//      log.debug("logger new interest {}: {}", garbageCounter.get(), id, re);
//    }

  }

  @Override
  public String toTraceId() {
    return null;
  }

  @Override
  public String toSpanId() {
    return null;
  }

  @Override
  public Iterable<Map.Entry<String, String>> baggageItems() {
    if (wrappedSpan != null) {
      return wrappedSpan.context().baggageItems();
    } else {
      return baggage.entrySet();
    }
  }
}
