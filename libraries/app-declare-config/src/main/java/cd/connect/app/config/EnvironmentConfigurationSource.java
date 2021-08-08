package cd.connect.app.config;

import net.stickycode.configuration.ConfigurationKey;
import net.stickycode.configuration.ConfigurationSource;
import net.stickycode.configuration.ConfigurationValue;
import net.stickycode.configuration.ResolvedConfiguration;

public class EnvironmentConfigurationSource implements ConfigurationSource {

  @Override
  public String toString() {
    return getClass().getSimpleName();
  }

  @Override
  public void apply(ConfigurationKey key, ResolvedConfiguration values) {
    for (String lookupKey : key.join("_")) {
      final String value = lookupValue(lookupKey.toUpperCase());
      if (value != null)
        values.add(new ConfigurationValue() {
          @Override
          public String get() {
            return value;
          }

          @Override
          public boolean hasPrecedence(ConfigurationValue configurationValue) {
            return true;
          }
        });
    }
  }

  protected String lookupValue(String environmentKey) {
    return System.getenv(environmentKey);
  }

}
