package cd.connect.openapi.support.auth;

import cd.connect.openapi.support.Pair;

import java.util.List;
import java.util.Map;

/**
 * Use this when they don't want the prefix
 */
public class StupidAuth implements Authentication {
  private String bearerToken;

  public StupidAuth() {
  }

  /**
   * Gets the token, which together with the scheme, will be sent as the value of the Authorization header.
   *
   * @return The bearer token
   */
  public String getBearerToken() {
    return bearerToken;
  }

  /**
   * Sets the token, which together with the scheme, will be sent as the value of the Authorization header.
   *
   * @param bearerToken The bearer token to send in the Authorization header
   */
  public StupidAuth setBearerToken(String bearerToken) {
    this.bearerToken = bearerToken;
    return this;
  }

  @Override
  public void applyToParams(List<Pair> queryParams, Map<String, String> headerParams) {
    if(bearerToken == null) {
      return;
    }

    headerParams.put("Authorization", bearerToken);
  }
}
