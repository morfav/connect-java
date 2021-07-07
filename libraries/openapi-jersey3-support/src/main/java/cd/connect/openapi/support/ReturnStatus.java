package cd.connect.openapi.support;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation is emitted when the return status is NOT 200
 * but something like 201 (created) or 204 (delete)
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
public @interface ReturnStatus {
  int code() default 200;
}
