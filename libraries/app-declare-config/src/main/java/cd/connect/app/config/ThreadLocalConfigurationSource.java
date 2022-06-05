package cd.connect.app.config;

import net.stickycode.configuration.ConfigurationKey;
import net.stickycode.configuration.ConfigurationSource;
import net.stickycode.configuration.ConfigurationValue;
import net.stickycode.configuration.ResolvedConfiguration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ThreadLocalConfigurationSource implements ConfigurationSource {
  public static class SourceHolder {
    public final Map<String, String> config;

    public SourceHolder() {
      this(new ConcurrentHashMap<>());
    }

    public SourceHolder(Map<String, String> config) {
      this.config = config;
    }
  }

  public static SourceHolder createContext() {
    final SourceHolder holder = new SourceHolder();
    testSource.set(holder);
    return holder;
  }

  public static SourceHolder createContext(Map<String, String> config) {
    final SourceHolder holder = new SourceHolder(config);
    testSource.set(holder);
    return holder;
  }


  public static void clearContext() {
    testSource.remove();
  }

  public static final ThreadLocal<SourceHolder> testSource = new ThreadLocal<>();

  public static String getKey(String key) {
    final SourceHolder holder = testSource.get();
    return holder != null ? holder.config.get(key) : null;
  }

  @Override
  public void apply(ConfigurationKey configurationKey, ResolvedConfiguration resolvedConfiguration) {
    final String key = configurationKey.join(".").get(0);

    final String value =  getKey(key);

    if (value != null) {
      resolvedConfiguration.add(new ConfigurationValue() {
        @Override
        public String get() {
          String newValue = getKey(key);

          return newValue == null ? value : newValue;
        }

        @Override
        public boolean hasPrecedence(ConfigurationValue v) {
          return false;
        }
      });
    }
  }
}
