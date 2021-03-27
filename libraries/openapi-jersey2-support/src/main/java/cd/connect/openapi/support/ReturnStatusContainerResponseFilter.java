package cd.connect.openapi.support;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.annotation.Annotation;

// suggestion from: https://stackoverflow.com/questions/4687271/jax-rs-how-to-return-json-and-http-status-code-together
@Provider
@ReturnStatus
public class ReturnStatusContainerResponseFilter implements ContainerResponseFilter {
  @Override
  public void filter(ContainerRequestContext containerRequestContext, ContainerResponseContext containerResponseContext) throws IOException {
    if (containerResponseContext.getStatus() == 200) {
      for (Annotation annotation : containerResponseContext.getEntityAnnotations()) {
        if(annotation instanceof ReturnStatus){
          containerResponseContext.setStatus(((ReturnStatus) annotation).code());
          break;
        }
      }
    }
  }
}
