package cd.connect.jersey.prometheus;

import jakarta.ws.rs.container.DynamicFeature;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.FeatureContext;
import jakarta.ws.rs.ext.Provider;

/**
 * @author Richard Vowles - https://plus.google.com/+RichardVowles
 */
@Provider
public class PrometheusDynamicFeature implements DynamicFeature {
  private final boolean profileAll;
  private final String prefix;

  public PrometheusDynamicFeature() {
    this(profileAll());
  }

  private static boolean profileAll() {
    String val = System.getProperty("prometheus.jersey.all", System.getenv("PROMETHEUS_JERSEY_ALL"));
    return val == null ? true : Boolean.valueOf(val);
  }

  public PrometheusDynamicFeature(boolean profileAll) {
    this.prefix = System.getProperty("prometheus.jersey.prefix", System.getenv("PROMETHEUS_JERSEY_PREFIX") );

    GlobalJerseyMetrics.init(prefix);

    this.profileAll = profileAll;
  }

  @Override
  public void configure(ResourceInfo resourceInfo, FeatureContext context) {
    Prometheus annotation = resourceInfo.getResourceMethod().getAnnotation(Prometheus.class);
    if (annotation != null || profileAll) {
      context.register(new PrometheusFilter(resourceInfo, prefix, annotation));
    }
  }
}
