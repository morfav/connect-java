package cd.connect.jersey.common.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import java.io.IOException;

/**
 * @author Kefeng Deng (https://bit.ly/2JFoCO1)
 * <p>
 * This mutation aims to prevent client 411 http error when post request body is NULL
 */
public class EmptyClientPostMutation implements ClientRequestFilter {

  private static final Logger LOGGER = LoggerFactory.getLogger(EmptyClientPostMutation.class);

  @Override
  public void filter(ClientRequestContext requestContext) throws IOException {
    LOGGER.debug("Sending [{}] client request to [{}]", requestContext.getMethod(), requestContext.getUri().getPath());
    if (isPostingEmptyEntity(requestContext)) {
      // Filling an empty object by default
      requestContext.setEntity("{}");
    }
  }

  private boolean isPostingEmptyEntity(ClientRequestContext requestContext) {
    return HttpMethod.POST.equals(requestContext.getMethod()) && requestContext.getEntity() == null;
  }

}
