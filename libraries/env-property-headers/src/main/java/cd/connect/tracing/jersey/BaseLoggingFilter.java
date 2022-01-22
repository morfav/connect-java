package cd.connect.tracing.jersey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Created by Richard Vowles on 11/01/18.
 */
abstract public class BaseLoggingFilter implements ContainerRequestFilter, ClientRequestFilter, ContainerResponseFilter {
	protected List<String> allHeaderNames = new ArrayList<>();
	protected Map<String, String> headerLogNameMap = new HashMap<>();
	private final Logger log = LoggerFactory.getLogger(getClass());

	// strip x-, replace - with .
	protected String headerNameToLogName(String name) {
		if (name.toLowerCase().startsWith("x-")) {
			name = name.substring(2);
		}

		return name.replace("-", ".");
	}


	abstract protected String getLocalValueForMissingHeader(String headerName);

	protected Map<String, String> makeMapFromConfig(String configKey) {
	  Map<String, String> converted = new HashMap<>();
	  java.lang.String envs = System.getProperty(configKey);
    if (envs != null) { // don't init twice
      StringTokenizer st = new StringTokenizer(envs, ",");
      while (st.hasMoreTokens()) {
        java.lang.String[] val = st.nextToken().split("[:=]");
        if (val.length == 2) { // two parts
	        String envName = val[0].trim();
	        String headerName = val[1].trim();

	        log.info("adding header: env {} header {}", envName, headerName);
          converted.put(envName, headerName);
        }
      }
    }

    return converted;
  }


  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
    throws IOException {
	  // now remove the headers so the next thread that uses this thread isn't polluted
    allHeaderNames.forEach(headerName -> {
      MDC.remove(headerLogNameMap.get(headerName));
    });
  }


	@Override
	public void filter(ContainerRequestContext requestContext) throws IOException {
		allHeaderNames.forEach(headerName -> {
			String val = requestContext.getHeaderString(headerName);

			if (val == null) {
				val = getLocalValueForMissingHeader(headerName);
			}

			if (val != null) {
				MDC.put(headerLogNameMap.get(headerName), val);
			}
		});
	}

	/**
	 * We are making a request out.
	 *
	 * @param requestContext - the context to append headers to.
	 * @throws IOException - a failure if any
	 */
	@Override
	public void filter(ClientRequestContext requestContext) throws IOException {
		MultivaluedMap<String, Object> headers = requestContext.getHeaders();

		allHeaderNames.forEach(headerName -> {
			Object first = headers.getFirst(headerName);

			// ensure we only set this value if it isn't already set
			if (first == null) {
				String val = MDC.get(headerLogNameMap.get(headerName));
				if (val == null) {
					val = getLocalValueForMissingHeader(headerName); // in case we originated this call
				}
				if (val != null) {
					headers.add(headerName, val);
				}
			}
		});
	}
}
