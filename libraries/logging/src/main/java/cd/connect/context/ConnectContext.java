package cd.connect.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import cd.connect.jackson.JacksonObjectProvider;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Map;

/**
 * We use the underlying MDC of the logging library to store these as essentially they are
 * used for logging, so much be compatible.
 *
 * Objects always get stored as JSON mapped strings, and the key is changed to "json."
 *
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
public class ConnectContext {
  static class NamedConnectImpl implements NamedConnectContext {
    private final String key;

    NamedConnectImpl(String key) {
      this.key = key;
    }

    @Override
    public String get() {
      return ConnectContext.get(key);
    }

    @Override
    public void set(String id) {
      ConnectContext.set(key, id);
    }

    @Override
    public void remove() {
      ConnectContext.remove(key);
    }
  }

  public static NamedConnectContext requestId = new NamedConnectImpl(NamedConnectContext.requestId);
  public static NamedConnectContext scenarioId = new NamedConnectImpl(NamedConnectContext.scenarioId);

  public static final String JSON_PREFIX = "json:";

  /**
   * Clear the logging context (usually this thread)
   */
  public static void clear() {
    MDC.clear();
  }

  /**
   * Remove the specific key. Use clear to remove them all, always wrap this in a try/finally.
   *
   * @param key - key to remove from context
   */
  public static void remove(String key) {
    MDC.remove(key);
  }

  /**
   * logging contexts can generally only deal with strings
   *
   * @param key - the key to use, will be replaced with json.KEY if value is object. If the value is a JSON
   *            object that needs to be preserved as a JSON object, use the JSON_PREFIX in your key.
   * @param value - java.lang variants are stored as strings, other objects are converted to JSON
   */
  public static void set(String key, Object value) {
    if (key != null) {
      if (value == null) {
        MDC.remove(key);
      } else {
        if ("java.lang".equals(value.getClass().getPackage().getName())) {
          MDC.put(key, value.toString());
        } else {
          try {
            MDC.put(JSON_PREFIX + key, JacksonObjectProvider.mapper.writeValueAsString(value));
          } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }
  }

  /**
   * Gets a key and returns the string representation of it.
   *
   * @param key - key and will try and find the json version of it if missing.*
   * @return - always returns a string
   */

  public static String get(String key) {
    String val = MDC.get(key);

    if (val == null) {
      val = MDC.get(JSON_PREFIX + key);
    }

    return val;
  }

  /**
   * Takes the given key, finds the json: prefix version of it.
   *
   * @param key - the key to get (minus the json prefix)
   * @param clazz - the class to serialize into
   * @param <T> - the type
   * @return - null (if not found) or new instance of object
   */
  public static <T> T get(String key, Class<T> clazz) {
    if (key != null) {
      String val = MDC.get(JSON_PREFIX + key);

      if (val != null) {
        try {
          return JacksonObjectProvider.mapper.readValue(val, clazz);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }

    return null;
  }

  /*
   * The complete context.
   */
  public static Map<String, String> getContext() {
    return MDC.getCopyOfContextMap();
  }

  /*
   * used when transferring context between threads
   */
  public static void setContext(Map<String, String> pushContext) {
    MDC.setContextMap(pushContext);
  }
}
