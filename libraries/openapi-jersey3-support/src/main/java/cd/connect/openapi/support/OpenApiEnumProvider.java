package cd.connect.openapi.support;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.ParamConverterProvider;
import jakarta.ws.rs.ext.Provider;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

@Provider
public class OpenApiEnumProvider implements ParamConverterProvider {
  private final Map<Class<?>, ParamConverter<?>> converterMap = new HashMap<>();

  @Override
  public <T> ParamConverter<T> getConverter(Class<T> rawType, Type genericType, Annotation[] annotations) {
    final ParamConverter<?> paramConverter = converterMap.get(rawType);
    if (paramConverter != null) {
      return (ParamConverter<T>) paramConverter;
    }

    if (rawType.isEnum()) {
      try {
        final Method fromValue = rawType.getMethod("fromValue", String.class);

        final ParamConverter<T> enumConverter = new ParamConverter<T>() {
          @Override
          public T fromString(String value) {
            try {
              return (T) fromValue.invoke(null, value);
            } catch (IllegalAccessException | InvocationTargetException e) {
              return null;
            }
          }

          @Override
          public String toString(T value) {
            return value.toString();
          }
        };

        converterMap.put(rawType, enumConverter);

        return enumConverter;
      } catch (NoSuchMethodException e) {
        return null;
      }
    }

    return null;
  }
}
