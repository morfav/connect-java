package cd.connect.jersey.common;

import cd.connect.jersey.prometheus.PrometheusDynamicFeature;

import jakarta.ws.rs.core.Feature;
import jakarta.ws.rs.core.FeatureContext;

public class InfrastructureConfiguration implements Feature {
	@Override
	public boolean configure(FeatureContext ctx) {
		ctx.register(JerseyPrometheusResource.class);
		ctx.register(HealthResource.class);
		ctx.register(PrometheusDynamicFeature.class);
		return true;
	}
}
