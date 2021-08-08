package cd.connect.app.config;

import net.stickycode.coercion.Coercion;
import net.stickycode.coercion.Coercions;
import net.stickycode.configuration.ConfigurationSource;
import net.stickycode.configuration.StickyConfiguration;
import net.stickycode.configured.Configuration;
import net.stickycode.configured.ConfigurationAttribute;
import net.stickycode.configured.ConfigurationRepository;
import net.stickycode.configured.InlineConfigurationRepository;
import net.stickycode.configured.TriedToInvertAnInvertedValue;
import net.stickycode.reflector.Reflector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.LinkedHashSet;
import java.util.Set;

public class DeclaredConfigResolver {
  private static final Logger log = LoggerFactory.getLogger(DeclaredConfigResolver.class);
  private static DeclaredConfigResolver instance = new DeclaredConfigResolver();

  private StickyConfiguration resolver = new StickyConfiguration();

  private Coercions coercions = new Coercions();


  private DeclaredConfigResolver() {
    // Michael has a thing for making his classes only usable by injection
    Set<Coercion> extensions = Collections.singleton(new MapCoercion(coercions));

    setInstanceValue(coercions, "extensions", extensions);

    Set<ConfigurationSource> configSources = new LinkedHashSet<>();

    configSources.add(new SystemPropertiesConfigurationSource());
    configSources.add(new EnvironmentConfigurationSource());

    setInstanceValue(resolver, "sources", configSources);

    resolver.startup();
  }

  // http://blog.sevagas.com/?Modify-any-Java-class-field-using-reflection - with runtime mods
  private static void setInstanceValue(final Object classInstance, final String fieldName, final Object newValue)  {
    try {
      // Get the private field
      final Field field = classInstance.getClass().getDeclaredField(fieldName);
      // Allow modification on the field
      field.setAccessible(true);
      // Sets the field to the new value for this instance
      field.set(classInstance, newValue);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  protected void resolveBean(Object bean) {
    ConfigurationRepository configurations = new InlineConfigurationRepository();

    new Reflector()
      .forEachField(new ConfigKeyProcessor(configurations))
      .process(bean);

    for (Configuration configuration : configurations)
      resolveAttributes(bean, configuration);

    for (Configuration configuration : configurations)
      for (ConfigurationAttribute attribute : configuration)
        if (attribute.requiresResolution()) {
          resolver.resolve(attribute);
          attribute.applyCoercion(coercions);
        }
  }

  private void resolveAttributes(Object target, Configuration configuration) {
    try {
      for (ConfigurationAttribute attribute : configuration) {
        resolveAttribute(target, attribute);
      }
    }
    catch (ConcurrentModificationException e) {
      throw new TriedToInvertAnInvertedValue(configuration);
    }
  }

  private void resolveAttribute(Object target, ConfigurationAttribute attribute) {
    if (attribute.requiresResolution() && attribute.getTarget() == target) {
      resolver.resolve(attribute);
      attribute.applyCoercion(coercions);
      attribute.update();
    }
  }

  public static void resolve(Object bean) {
    instance.resolveBean(bean);
  }
}
