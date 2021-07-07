package cd.connect.jersey.common;

import cd.connect.jersey.common.logging.JerseyFiltering;
import cd.connect.jersey.common.logging.JerseyFilteringConfiguration;
import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.logging.FilteringClientLoggingFilter;
import org.glassfish.jersey.logging.FilteringServerLoggingFilter;
import org.glassfish.jersey.logging.JerseyClientLogger;
import org.glassfish.jersey.logging.JerseyServerLogger;

import javax.inject.Singleton;
import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

public class LoggingConfiguration implements Feature {

	@Override
	public boolean configure(FeatureContext ctx) {
		ctx.register(FilteringClientLoggingFilter.class);
		ctx.register(FilteringServerLoggingFilter.class);
		ctx.register(new AbstractBinder() {
      @Override
      protected void configure() {
        bind(new JerseyFilteringConfiguration()).in(Singleton.class)
          .to(JerseyFiltering.class);
      }
    });
		return true;
	}
}
