package cd.connect.maven;

import com.github.mustachejava.DefaultMustacheFactory;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Must of this code comes from (c) Copyright 2013 Jonathan Cobb. His repository is here: https://github.com/cobbzilla/merge-yml
 */

@Mojo(name = "mergeYaml",
	defaultPhase = LifecyclePhase.INITIALIZE,
	requiresDependencyResolution = ResolutionScope.NONE, threadSafe = true)
public class MergeYamlMojo extends AbstractMojo {
	private static final DefaultMustacheFactory DEFAULT_MUSTACHE_FACTORY = new DefaultMustacheFactory();

	enum VarSubstitution { NONE, MUSTACHE }

	@Parameter(name = "finalYaml", required = true, property = "merge-yaml.finalYaml")
	String finalYaml;

	@Parameter(name = "files", required = true, property = "merge-yaml.files")
	List<File> files;

	@Parameter(name = "flowStyle", property = "merge-yaml.flowStyle", defaultValue = "AUTO")
	DumperOptions.FlowStyle flowStyle;

	@Parameter(name = "varSubstitution", property = "merge-yaml.varSubstitution", defaultValue = "MUSTACHE")
	String varSubstitution;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		DumperOptions opts = new DumperOptions();
		opts.setDefaultFlowStyle(flowStyle);
		Yaml yaml = new Yaml(opts);
		Map<String, Object> scope = new HashMap<String, Object>();

		scope.putAll(System.getenv());


		try {
			String output = yaml.dump(merge(files, scope, yaml));

			FileUtils.write(new File(finalYaml), output, Charset.defaultCharset());

			getLog().debug("Written " + finalYaml);
		} catch (IOException e) {
			throw new MojoFailureException("Unable to merge yaml", e);
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> merge(List<File> files, Map<String, Object> scope, Yaml yaml) throws IOException {
		Map<String, Object> mergedResult = new LinkedHashMap<String, Object>();
		for (File file : files) {
			InputStream in = null;
			try {
				// read the file into a String
				in = new FileInputStream(file);
				final String entireFile = IOUtils.toString(in, Charset.defaultCharset());

				String substitutedFile;
				// substitute variables
				switch(VarSubstitution.valueOf(varSubstitution)) {
					case MUSTACHE:
						final StringWriter writer = new StringWriter(entireFile.length() + 10);
						DEFAULT_MUSTACHE_FACTORY.compile(new StringReader(entireFile), "mergeyml_"+System.currentTimeMillis()).execute(writer, scope);
						substitutedFile = writer.toString();
						break;

					default:
						// No substitution : take file as-is.
						substitutedFile = entireFile;
						break;
				}

				// load the YML file
				final Map<String, Object> yamlContents = (Map<String, Object>) yaml.load(substitutedFile);

				// merge into results map
				merge_internal(mergedResult, yamlContents);
				getLog().info("loaded YML from "+file+": "+yamlContents);

			} finally {
				if (in != null) in.close();
			}
		}

		return mergedResult;
	}

	@SuppressWarnings("unchecked")
	private void merge_internal(Map<String, Object> mergedResult, Map<String, Object> yamlContents) {

		if (yamlContents == null) return;

		for (String key : yamlContents.keySet()) {

			Object yamlValue = yamlContents.get(key);
			if (yamlValue == null) {
				addToMergedResult(mergedResult, key, yamlValue);
				continue;
			}

			Object existingValue = mergedResult.get(key);
			if (existingValue != null) {
				if (yamlValue instanceof Map) {
					if (existingValue instanceof Map) {
						merge_internal((Map<String, Object>) existingValue, (Map<String, Object>)  yamlValue);
					} else if (existingValue instanceof String) {
						throw new IllegalArgumentException("Cannot merge complex element into a simple element: "+key);
					} else {
						throw unknownValueType(key, yamlValue);
					}
				} else if (yamlValue instanceof List) {
					mergeLists(mergedResult, key, yamlValue);

				} else if (yamlValue instanceof String
					|| yamlValue instanceof Boolean
					|| yamlValue instanceof Double
					|| yamlValue instanceof Integer) {
					getLog().debug("overriding value of "+key+" with value "+yamlValue);
					addToMergedResult(mergedResult, key, yamlValue);

				} else {
					throw unknownValueType(key, yamlValue);
				}

			} else {
				if (yamlValue instanceof Map
					|| yamlValue instanceof List
					|| yamlValue instanceof String
					|| yamlValue instanceof Boolean
					|| yamlValue instanceof Integer
					|| yamlValue instanceof Double) {
					getLog().debug("adding new key->value: "+key+"->"+yamlValue);
					addToMergedResult(mergedResult, key, yamlValue);
				} else {
					throw unknownValueType(key, yamlValue);
				}
			}
		}
	}

	private IllegalArgumentException unknownValueType(String key, Object yamlValue) {
		final String msg = "Cannot merge element of unknown type: " + key + ": " + yamlValue.getClass().getName();
		getLog().error(msg);
		return new IllegalArgumentException(msg);
	}

	private Object addToMergedResult(Map<String, Object> mergedResult, String key, Object yamlValue) {
		return mergedResult.put(key, yamlValue);
	}

	@SuppressWarnings("unchecked")
	private void mergeLists(Map<String, Object> mergedResult, String key, Object yamlValue) {
		if (! (yamlValue instanceof List && mergedResult.get(key) instanceof List)) {
			throw new IllegalArgumentException("Cannot merge a list with a non-list: "+key);
		}

		List<Object> originalList = (List<Object>) mergedResult.get(key);
		originalList.addAll((List<Object>) yamlValue);
	}
}
