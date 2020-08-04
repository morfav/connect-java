package cd.connect.opentracing;

import io.opentracing.Scope;
import io.opentracing.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 *   // when one of these gets created, another client is interested in this span
 *   // so we increase its garbage count. This is how Executor pools propagate spans
 *   // by creating new scopes foe them after getting the active span.
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class LoggerScope implements Scope {
  private static final Logger log = LoggerFactory.getLogger(LoggerScope.class);
  final LoggerSpan span;
  private Scope wrappedScope;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final boolean finishSpanOnClose;
  private final LoggingSpanTracer tracer;

  LoggerScope(LoggerSpan span, boolean finishSpanOnClose, LoggingSpanTracer tracer) {
    this.span = span;
    this.finishSpanOnClose = finishSpanOnClose;
    this.tracer = tracer;

    log.debug("log activating new scope with span {}", span.getId());

    if (finishSpanOnClose) {
      span.incInterest();
    }
  }

  public boolean isClosed() {
    return closed.get();
  }

  public void setWrappedScope(Scope wrappedScope) {
    this.wrappedScope = wrappedScope;
  }

  @Override
  public void close() {
//    try {
//      throw new RuntimeException("re");
//    } catch (RuntimeException re) {
//      log.debug("closing scope {}", span.getId(), re);
//    }

    if (closed.compareAndSet(false, true)) {
      if (span != null && finishSpanOnClose) {
        span.finish(false); // don't call finish because wrappedScope.close will
      }
      tracer.cleanupScope(this);
    }

    if (wrappedScope != null) {
      wrappedScope.close();
    }
  }

  public Span span() {
    return span;
  }
}
