package cd.connect.app.config;

import net.stickycode.configured.MissingConfigurationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.api.Assertions.assertThat;

public class DeclaredConfigResolverTest {

  @After
  public void teardown() {
    System.clearProperty("sample1.value");
    System.clearProperty("sample1.value2");
    System.clearProperty("sample2.value");
    System.clearProperty("sample2.value2");

    ThreadLocalConfigurationSource.clearContext();
  }

  @Test
  public void basicTest() {
    System.setProperty("sample1.value", "s-value");
    System.setProperty("sample1.value2", "4");
    System.setProperty("sample2.value", "one, two, three");
    System.setProperty("sample2.value2", "one=two, three=four");

    doAsserts();
  }

  private void doAsserts() {
    Sample1 sample1 = new Sample1();
    assertThat(sample1.getValue()).isEqualTo("s-value");
    assertThat(sample1.getValue2()).isEqualTo(4);

    Sample2 sample2 = new Sample2();
    assertThat(sample2.getValue()).isEqualTo(Arrays.asList("one", " two", " three"));
    Map<String, String> map = new HashMap<>();
    map.put("one", "two");
    map.put("three", "four");
    assertThat(sample2.getValue2()).isEqualTo(map);
    assertThat(sample2.getAlreadyDefault()).isEqualTo("default");
  }

  @Test
  public void threadLocalTest() {
    ThreadLocalConfigurationSource.SourceHolder holder = ThreadLocalConfigurationSource.createContext();
    holder.config.put("sample1.value", "s-value");
    holder.config.put("sample1.value2", "4");
    holder.config.put("sample2.value", "one, two, three");
    holder.config.put("sample2.value2", "one=two, three=four");

    doAsserts();
  }


  @Test(expected = MissingConfigurationException.class)
  public void missingConfigTest() {
    new EvilSample1();
  }
}
