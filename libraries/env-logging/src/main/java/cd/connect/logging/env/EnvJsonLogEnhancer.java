package cd.connect.logging.env;

import cd.connect.logging.JsonLogEnhancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Created by Richard Vowles on 9/01/18.
 */
public class EnvJsonLogEnhancer implements JsonLogEnhancer {
	protected Map<String, Object> converted = new ConcurrentHashMap<>();
	final protected static String CONFIG_KEY = "connect.logging.environment";
	protected static EnvJsonLogEnhancer self;
	final private static Logger log = LoggerFactory.getLogger(EnvJsonLogEnhancer.class);

	static class EnvFlattener {
		Map<String, EnvFlattener> envs = new HashMap<>();
		Map<String, String> plain = new HashMap<>();

		public void put(String k, String val) {
			if (k.contains(".")) {
				int pos = k.indexOf(".");
				EnvFlattener flat = envs.computeIfAbsent(k.substring(0, pos), key -> new EnvFlattener());
				flat.put(k.substring(pos + 1), val);
			} else {
				plain.put(k, val);
			}
		}

		public Map<String, Object> flatten() {
			Map<String, Object> data = new HashMap<>(plain);
			envs.forEach((k,v) -> data.put(k, v.flatten()));
			return data;
		}
	}

  /**
   * The difficulty here is that the logging gets initialized _very_ early. So you need to choose something
   * that works with your framework that will initialize it after the properties have been set. This may
   * be on the command line, in which case up front is fine. Otherwise later will have to do.
   *
   * Command line will work out of the box, i.e. -Dconnect.logging.environment, otherwise call it when you can
   */
	public static void initialize() {
	  if (self != null) {
      String envs = System.getProperty(CONFIG_KEY, System.getenv(CONFIG_KEY));
      if (envs != null && self.converted.size() == 0) { // don't init twice

        StringTokenizer st = new StringTokenizer(envs, ",");
	      EnvFlattener envFlattener = new EnvFlattener();
        List<String> environmentVariables = new ArrayList<>();
        while (st.hasMoreTokens()) {
          String[] val = st.nextToken().split("[:=]");
          if (val.length == 2) { // two parts
            String e = self.getEnv(val[0]);
            if (e != null) {
              e = e.trim();
              if (e.length() > 0) {
                envFlattener.put(val[1], e);
                environmentVariables.add(val[1]);
              }
            }
          }
        }

				self.converted.putAll(envFlattener.flatten());

        if (environmentVariables.size() > 0) {
        	log.info("Environment logger created with {} variables", String.join(", ", environmentVariables));
        } else {
        	log.info("No environmental loggers detected, probably misconfigured.");
        }
      }
    }
  }

	public EnvJsonLogEnhancer() {
    self = this;
    initialize();
	}

	protected String getEnv(String env) {
		return System.getenv(env);
	}

	@Override
	public int getMapPriority() {
		return 20;
	}

	@Override
	public void map(Map<String, String> context, Map<String, Object> log, List<String> alreadyEncodedJsonObjects) {
		converted.forEach(log::put);
	}

	@Override
	public void failed(Map<String, String> context, Map<String, Object> log, List<String> alreadyEncodedJsonObjects, Throwable e) {
	}
}
