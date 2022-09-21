package cd.connect.cloudevents;

import org.jetbrains.annotations.NotNull;

public class CloudEventUtils {
  @NotNull
  public static String subject(@NotNull Class<? extends TaggedCloudEvent> clazz) {
    final CloudEventSubject subject = clazz.getAnnotation(CloudEventSubject.class);

    if (subject == null) {
      throw new RuntimeException("no cloud events subject available on " + clazz.getName());
    }

    return subject.value();
  }

  @NotNull
  public static String type(@NotNull Class<? extends TaggedCloudEvent> clazz) {
    final CloudEventType subject = clazz.getAnnotation(CloudEventType.class);

    if (subject == null) {
      throw new RuntimeException("no cloud events subject available on " + clazz.getName());
    }

    return subject.value();
  }
}
