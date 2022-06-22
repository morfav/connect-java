package cd.connect.openapi.support;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.annotation.Annotation;

// suggestion from: https://stackoverflow.com/questions/4687271/jax-rs-how-to-return-json-and-http-status-code-together
@Provider
@ReturnStatus
public class ReturnStatusContainerResponseFilter implements ContainerResponseFilter {
  @Override
  public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) throws IOException {
    // a 204 (which is void) from a POST actually returns a 204 (no content) and we need to intercept that as well
    if (containerResponseContext.getStatus() == 200 || containerResponseContext.getStatus() == 204) {
      for (Annotation annotation : containerResponseContext.getEntityAnnotations()) {
        if(annotation instanceof ReturnStatus){
          containerResponseContext.setStatus(((ReturnStatus) annotation).code());
          break;
        }
      }
    }
  }
}
