package cd.connect.cloudevents;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@CloudEventSubject("subject")
@CloudEventType("type")
class TaggedClass implements TaggedCloudEvent {
}

class BadTag implements TaggedCloudEvent {}

public class CloudEventsUtilsTest {
  @Test
  public void subjectTest() {
    assertThat(CloudEventUtils.subject(TaggedClass.class)).isEqualTo("subject");
    assertThat(CloudEventUtils.type(TaggedClass.class)).isEqualTo("type");
  }

  @Test()
  public void throwsException() {
    assertThrows(RuntimeException.class, () -> CloudEventUtils.subject(BadTag.class));
    assertThrows(RuntimeException.class, () -> CloudEventUtils.type(BadTag.class));
  }
}
