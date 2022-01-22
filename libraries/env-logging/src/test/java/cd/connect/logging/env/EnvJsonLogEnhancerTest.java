package cd.connect.logging.env;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.fest.assertions.api.Assertions.assertThat;

/**
 * Created by Richard Vowles on 10/01/18.
 */
public class EnvJsonLogEnhancerTest {
	@Test
	public void simpleTest() {
		Map<String, String> fakeEnv = new HashMap<>();
		fakeEnv.put("FRED", "sausage");
		fakeEnv.put("WILBUR", "belieber");

		System.setProperty(EnvJsonLogEnhancer.CONFIG_KEY, "FRED=freud,WILBUR=kubernetes.wilbur,CHUNKY=shouldnotbethere");

		EnvJsonLogEnhancer envLogger = new EnvJsonLogEnhancer() {
			@Override
			protected String getEnv(String env) {
				return fakeEnv.get(env);
			}
		};

		Map<String, Object> log = new HashMap<>();

		envLogger.map(null, log, null);

		assertThat(log.size()).isEqualTo(2);
		assertThat(log.get("freud")).isEqualTo("sausage");
		Map<String, Object> hm = new HashMap<>();
		hm.put("wilbur", "belieber");
		assertThat(log.get("kubernetes")).isEqualTo(hm);
	}
}
