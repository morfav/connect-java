package cd.connect.opentracing;

import io.opentracing.References;
import io.opentracing.Scope;
import io.opentracing.ScopeManager;
import io.opentracing.Span;
import io.opentracing.SpanContext;
import io.opentracing.Tracer;
import io.opentracing.propagation.Format;
import io.opentracing.propagation.TextMap;
import io.opentracing.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Stack;

/**
 * This class is designed to ensure that the Connect context is able to extract
 * the identified context and write it to the logs.
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class LoggingSpanTracer implements Tracer {
  private static final Logger log = LoggerFactory.getLogger(LoggingSpanTracer.class);
  private final Tracer wrappedTracer;
  protected ThreadLocal<Stack<LoggerScope>> activeScopeStack = new ThreadLocal<>();
  private String appName;


  void pushScope(LoggerScope scope) {
    Stack<LoggerScope> scopes = this.activeScopeStack.get();
    if (scopes == null) {
      scopes = new Stack<>();
      this.activeScopeStack.set(scopes);
    }

    scopes.push(scope);
  }

  LoggerScope popScope() {
    Stack<LoggerScope> scopes = this.activeScopeStack.get();
    if (scopes != null && scopes.size() > 0) {
      LoggerScope pop = scopes.pop();
      log.debug("dropping scope for span {}", pop.span.getId());
      return  scopes.size() > 0 ? scopes.peek() : null;
    } else {
      return null;
    }
  }

  LoggerScope activeScope() {
    Stack<LoggerScope> scopes = this.activeScopeStack.get();
    return (scopes != null && scopes.size() > 0) ? scopes.peek() : null;
  }

  boolean activeScopeClosed() {
    Stack<LoggerScope> scopes = this.activeScopeStack.get();
    return (scopes != null && scopes.size() > 0) && scopes.peek().isClosed();
  }

  public LoggingSpanTracer(Tracer wrappedTracer) {
    this.wrappedTracer = wrappedTracer;

    appName = System.getProperty("app.name");

    if (appName == null) {
      try {
        appName = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException e) {
        appName = "unknown-app";
      }
    }
  }

  private LoggerScope cleanScopes() {
    if (activeScopeClosed()) {
      popScope();
      LoggerScope scope = activeScope();
      while (scope != null && scope.isClosed()) {
        scope = popScope();
      }
      // take the last active scope and make its span active again for logging
      if (scope != null && !scope.span.isFinished()) {
        scope.span.setActive(appName);
      }

      log.debug("scope count outstanding: {}", activeScopeStack.get().size());
      return scope;
    } else {
      return activeScope();
    }
  }

  void cleanupScope(LoggerScope scope) {
    scope.span.removeActive();

    log.debug("loggerscope with span {} has been finished", scope.span.getId());
    cleanScopes();
  }

  private final ScopeManager scopeManager = new ScopeManager() {
    private LoggerSpan currentSpan = null;

    @Override
    public Scope activate(Span span) {
      LoggerSpan loggerSpan = span instanceof LoggerSpan ? (LoggerSpan) span :
        new LoggerSpan(activeScope()).setWrappedSpan(span);

      LoggerScope newScope = new LoggerScope(loggerSpan, false, LoggingSpanTracer.this);

      Scope wrappedScope = wrappedTracer.scopeManager().activate(loggerSpan.getWrappedSpan());
      newScope.setWrappedScope(wrappedScope);

      loggerSpan.setActive(appName);

      if (loggerSpan.getBaggageItem(OpenTracingLogger.WELL_KNOWN_REQUEST_ID) == null) {
        String requestId = OpenTracingLogger.randomRequestIdProvider.get();
        loggerSpan.setBaggageItem(OpenTracingLogger.WELL_KNOWN_REQUEST_ID, requestId);
        loggerSpan.setTag(OpenTracingLogger.WELL_KNOWN_REQUEST_ID, requestId);
      }

      if (loggerSpan.getBaggageItem(OpenTracingLogger.WELL_KNOWN_ORIGIN_APP) == null) {
        loggerSpan.setBaggageItem(OpenTracingLogger.WELL_KNOWN_ORIGIN_APP, appName);
      }

      pushScope(newScope);

      this.currentSpan = loggerSpan;

      return newScope;
    }

    @Override
    public Span activeSpan() {
      return this.currentSpan;
    }
  };

  @Override
  public ScopeManager scopeManager() {
    return scopeManager;
  }

  @Override
  public Span activeSpan() {
    LoggerScope scope = cleanScopes();
    return (scope == null) ? null : scope.span();
  }

  @Override
  public Scope activateSpan(Span span) {
    return scopeManager.activate(span);
  }

  @Override
  public SpanBuilder buildSpan(String operationName) {
    return new LoggingSpanBuilder(wrappedTracer.buildSpan(operationName));
  }

  @Override
  public <C> void inject(SpanContext spanContext, Format<C> format, C c) {
    // get the internal span and inject its data, we have none
    LoggerSpan span = (LoggerSpan)spanContext;
    if (span.getWrappedSpan() != null) {
      wrappedTracer.inject(span.getWrappedSpan().context(), format, c);
    } else {
      wrappedTracer.inject(spanContext, format, c);
    }
  }

  protected void log(TextMap map) {
    map.iterator().forEachRemaining(e -> log.info("{}: {}", e.getKey(), e.getValue()));
  }

  @Override
  public <C> SpanContext extract(Format<C> format, C c) {
//    if (format == Format.Builtin.HTTP_HEADERS) {
//      log((TextMap)c);
//    }

    SpanContext ctx = wrappedTracer.extract(format, c);

    LoggerSpan span = null;
    if (ctx != null) {

      span = new LoggerSpan(null);

      if (ctx instanceof Span) {
        span.setWrappedSpan((Span)ctx);
      } else {
        span.setWrappedSpanContext(ctx);
      }

      LoggerSpan finalSpan = span;
      ctx.baggageItems().forEach(entry -> {
        // force keys to lower case
        finalSpan.setBaggageItem(entry.getKey().toLowerCase(), entry.getValue());
      });
    }

    return span;
  }

  @Override
  public void close() {
    wrappedTracer.close();
  }

  class LoggingSpanBuilder implements Tracer.SpanBuilder {
    private SpanBuilder spanBuilder;
    private LoggerSpan loggerSpan;

    LoggingSpanBuilder(SpanBuilder spanBuilder) {
      this.spanBuilder = spanBuilder;
      loggerSpan = new LoggerSpan(activeScope());
    }

    @Override
    public SpanBuilder asChildOf(SpanContext parent) {
      if (parent instanceof LoggerSpan) {
        LoggerSpan loggerSpan = (LoggerSpan)parent;

        if (loggerSpan.getWrappedSpanContext() != null) {
          this.spanBuilder = this.spanBuilder.asChildOf(loggerSpan.getWrappedSpanContext());
        } else {
          this.spanBuilder = this.spanBuilder.asChildOf(parent);
        }

        loggerSpan.setPriorSpan((LoggerSpan)parent);
      } else {
        this.spanBuilder = this.spanBuilder.asChildOf(parent);
      }

      return this;
    }

    @Override
    public SpanBuilder asChildOf(Span parent) {
      if (parent instanceof LoggerSpan) {
        LoggerSpan loggerSpan = (LoggerSpan)parent;

        if (loggerSpan.getWrappedSpan() != null) {
          this.spanBuilder = this.spanBuilder.asChildOf(loggerSpan.getWrappedSpan());
        } else if (loggerSpan.getWrappedSpanContext() != null) {
          this.spanBuilder = this.spanBuilder.asChildOf(loggerSpan.getWrappedSpanContext());
        } else {
          this.spanBuilder = this.spanBuilder.asChildOf(parent);
        }

        loggerSpan.setPriorSpan((LoggerSpan)parent);
      } else {
        this.spanBuilder = this.spanBuilder.asChildOf(parent);
      }

      return this;
    }

    @Override
    public SpanBuilder addReference(String referenceType, SpanContext referencedContext) {
      if (References.CHILD_OF.equals(referenceType)) {
        return asChildOf(referencedContext);
      } else { // follows from
        if (referencedContext instanceof LoggerSpan) {
          LoggerSpan loggerSpan = (LoggerSpan)referencedContext;
          if (loggerSpan.getWrappedSpanContext() != null) {
            this.spanBuilder = spanBuilder.addReference(referenceType, loggerSpan.getWrappedSpanContext());
          } else {
            this.spanBuilder = spanBuilder.addReference(referenceType, referencedContext);
          }

          loggerSpan.setPriorSpan(loggerSpan);
        } else {
          this.spanBuilder = spanBuilder.addReference(referenceType, referencedContext);
        }
      }

      // we don't use it, so ignore it
      return this;
    }

    @Override
    public SpanBuilder ignoreActiveSpan() {
      this.spanBuilder = spanBuilder.ignoreActiveSpan();
      loggerSpan.setPriorSpan(null);
      return this;
    }

    @Override
    public SpanBuilder withTag(String key, String value) {
      loggerSpan.setTag(key, value);
      spanBuilder = spanBuilder.withTag(key, value);
      return this;
    }

    @Override
    public SpanBuilder withTag(String key, boolean value) {
      loggerSpan.setTag(key, value);
      spanBuilder = spanBuilder.withTag(key, value);
      return this;
    }

    @Override
    public SpanBuilder withTag(String key, Number value) {
      loggerSpan.setTag(key, value);
      spanBuilder = spanBuilder.withTag(key, value);
      return this;
    }

    @Override
    public <T> SpanBuilder withTag(Tag<T> tag, T t) {
      loggerSpan.setTag(tag, t);
      spanBuilder = spanBuilder.withTag(tag, t);
      return this;
    }

    @Override
    public SpanBuilder withStartTimestamp(long microseconds) {
      spanBuilder = spanBuilder.withStartTimestamp(microseconds);
      return this;
    }

//    @Override
//    public Scope startActive(boolean finishSpanOnClose) {
//      Scope scope = spanBuilder.startActive(finishSpanOnClose);
//
//      // we can't use "activate" in our own scopeManager as it will activate the wrapped span again
//      loggerSpan.setWrappedSpan(scope.span());
//
//      LoggerScope newScope = new LoggerScope(loggerSpan, finishSpanOnClose, LoggingSpanTracer.this);
//
//      newScope.setWrappedScope(scope);
//
//      loggerSpan.setActive(appName);
//
//      if (loggerSpan.getBaggageItem(OpenTracingLogger.WELL_KNOWN_REQUEST_ID) == null) {
//        loggerSpan.setBaggageItem(OpenTracingLogger.WELL_KNOWN_REQUEST_ID, OpenTracingLogger.randomRequestIdProvider.get());
//      }
//
//      pushScope(newScope);
//
//      return newScope;
//    }
//
//    @Override
//    public Span startManual() {
//      return startActive(false).span();
//    }

    @Override
    public Span start() {
      loggerSpan.setWrappedSpan(spanBuilder.start());
      if (loggerSpan.getBaggageItem(OpenTracingLogger.WELL_KNOWN_REQUEST_ID) == null) {
        loggerSpan.setBaggageItem(OpenTracingLogger.WELL_KNOWN_REQUEST_ID, OpenTracingLogger.randomRequestIdProvider.get());
      }

      scopeManager.activate(loggerSpan);

      return loggerSpan;
    }
  }
}
