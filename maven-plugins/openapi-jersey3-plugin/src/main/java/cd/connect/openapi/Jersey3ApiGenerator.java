package cd.connect.openapi;


import com.google.googlejavaformat.java.Formatter;
import com.google.googlejavaformat.java.FormatterException;
import com.google.googlejavaformat.java.JavaFormatterOptions;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.openapitools.codegen.CliOption;
import org.openapitools.codegen.CodegenConfig;
import org.openapitools.codegen.CodegenConstants;
import org.openapitools.codegen.CodegenModel;
import org.openapitools.codegen.CodegenOperation;
import org.openapitools.codegen.CodegenParameter;
import org.openapitools.codegen.CodegenProperty;
import org.openapitools.codegen.SupportingFile;
import org.openapitools.codegen.languages.AbstractJavaJAXRSServerCodegen;
import org.openapitools.codegen.model.ModelMap;
import org.openapitools.codegen.model.ModelsMap;
import org.openapitools.codegen.model.OperationsMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.openapitools.codegen.utils.StringUtils.camelize;

public class Jersey3ApiGenerator extends AbstractJavaJAXRSServerCodegen implements CodegenConfig {
	private static final Logger log = LoggerFactory.getLogger(Jersey3ApiGenerator.class);
	private static final String LIBRARY_NAME = "jersey3-api";
	private static final String JERSEY2_TEMPLATE_FOLDER = "jersey3-v3template";
	private static final String SERVICE_ADDRESS = "serviceAddress";
	private static final String SERVICE_NAME = "serviceName";
	private static final String SERVICE_PORT = "servicePort";
	private static final String SERVICE_DEFAULT_URL = "serviceDefaultUrl";
	// if this is set, then we always use this as the base path if it exists in all of the paths in the set of operations
	private static final String SERVICE_BASE = "serviceUrlBase";
	private static final String PREFIX_ALL_PATHS_USING_GET = "prefixGetPath";

	public Jersey3ApiGenerator() {
		super();
		library = LIBRARY_NAME;
		dateLibrary = "java8";
		supportedLibraries.clear();
		supportedLibraries.put(LIBRARY_NAME, LIBRARY_NAME);

		// tell the model about extra mustache files to generate

		// if we are using Kubernetes, we should get a service url. We separate these because the serviceName
		// is used for the Spring configuration class
		// this should appear in your config as:
		// <configOptions>
		//   <serviceName>...</serviceName> etc
		// </configOptions>
		cliOptions.add(new CliOption(SERVICE_NAME, "Name of service to use for @enable"));
		cliOptions.add(new CliOption(SERVICE_ADDRESS, "Name of service to use for @enable"));
		cliOptions.add(new CliOption(SERVICE_PORT, "Port of service to use for @enable"));
		cliOptions.add(new CliOption("suppressIgnoreUnknown", "Don't add the ignore unknown to the generated models"));

		if (this.typeMapping == null) {
			this.typeMapping = new HashMap<>();
		}

		this.typeMapping.put("void", "Void");

		// override the location
		embeddedTemplateDir = templateDir = JERSEY2_TEMPLATE_FOLDER;
	}


	public String getName() {
		return LIBRARY_NAME;
	}

	public String getHelp() {
		return "jersey3 api generator. generates all classes and interfaces with jax-rs annotations with jersey2 " +
			"extensions as necessary";
	}

	// we need to keep this for determining if we are using auth later
	private int parentPathParamCount = 0;

	@Override
	public void preprocessOpenAPI(OpenAPI openAPI) {
		super.preprocessOpenAPI(openAPI);
		if (openAPI.getServers() != null && openAPI.getServers().size() == 1) {
			additionalProperties.put(SERVICE_DEFAULT_URL, openAPI.getServers().get(0).getUrl());
		}

		if (!additionalProperties.containsKey("jersey2")) {
			additionalProperties.put("jersey3", "true");
		}

		if (additionalProperties.containsKey(PREFIX_ALL_PATHS_USING_GET)) {
			String getPath = additionalProperties.get(PREFIX_ALL_PATHS_USING_GET).toString();
			PathItem parentPath = openAPI.getPaths().get(getPath);

			if (parentPath == null || parentPath.getGet() == null ) {
				throw new RuntimeException(
					String.format("Attempt to get path `%s` failed because it is not in spec or has no GET",
					getPath));
			}

			if (!getPath.endsWith("/")) {
				getPath = getPath + "/";
			}

			// this allows us to pick up later for each parameter and drop them from the list if we are using
			//
			parentPath.getGet().getParameters().forEach(p -> p.addExtension("x-" + PREFIX_ALL_PATHS_USING_GET, "true"));
			parentPathParamCount = parentPath.getGet().getParameters().size();

			final String prefixPath = getPath;

			Paths newPaths = new Paths();

			openAPI.getPaths().forEach((path, pathItem) -> {
				if (pathItem != parentPath) {
					parentPath.getGet().getParameters().forEach(pathItem::addParametersItem);
					if (path.startsWith("/")) {
						path = path.substring(1);
					}
					path = prefixPath + path;
					newPaths.put(path, pathItem);
				}
			});

			openAPI.getPaths().clear();
			openAPI.setPaths(newPaths);
		}
	}

	// stoplight has a tendency to insert rubbish in the oas.json file
	@Override
	public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
		super.postProcessModelProperty(model, property);

		if ("null".equals(property.example)) {
			property.example = null;
		}

		if ("uuid".equals(property.getDataFormat())) {
			property.isFreeFormObject = false;
		}

		model.imports.remove("ApiModelProperty");
		model.imports.remove("ApiModel");
	}

	/**
	 * This gets called once we have been passed the configuration read in by the plugin.
	 */
	@Override
	public void processOpts() {
		super.processOpts();

		if (!useBeanValidation) {
			additionalProperties.remove("useBeanValidation");
		}

		apiTemplateFiles.remove("api.mustache");

		// no documentation, we're british
		modelDocTemplateFiles.clear();
		apiDocTemplateFiles.clear();

		modelTemplateFiles.put("model.mustache", ".java");

		if (additionalProperties.containsKey("client")) {
			apiTemplateFiles.put("Impl.mustache", ".java");
			apiTemplateFiles.put("ClientService.mustache", ".java");
		}
		if (additionalProperties.containsKey("server")) {
			apiTemplateFiles.put("Service.mustache", ".java");
		}
		if (additionalProperties.containsKey("server-security")) {
			apiTemplateFiles.put("SecurityService.mustache", ".java");
		}
		if (additionalProperties.containsKey("server-delegate")) {
			apiTemplateFiles.put("DelegateServerService.mustache", ".java");
			apiTemplateFiles.put("DelegateService.mustache", ".java");
			apiTemplateFiles.put("SecurityService.mustache", ".java");
		}

		if (usingDelegateHolderPackage()) {
			apiTemplateFiles.put("DelegateServerService.mustache", ".java");
			apiTemplateFiles.put("SecurityService.mustache", ".java");
		}

//    apiTemplateFiles.put("Configuration.mustache", ".java");

		// this is the name of the library and the date package we use

		apiTestTemplateFiles.clear();


		if (additionalProperties.get(SERVICE_NAME) != null) {
			String serviceName = additionalProperties.get(SERVICE_NAME).toString();
			if (additionalProperties.get(SERVICE_ADDRESS) != null) {
				addJersey2Client(serviceName, additionalProperties.get(SERVICE_ADDRESS).toString());
			} else if (additionalProperties.get(SERVICE_PORT) != null) {
				addJersey2Client(serviceName, String.format("%s-service:%s", serviceName,
					additionalProperties.get(SERVICE_PORT).toString()));
			}
		}

		if (additionalProperties.containsKey(CodegenConstants.IMPL_FOLDER)) {
			implFolder = (String) additionalProperties.get(CodegenConstants.IMPL_FOLDER);
		}
	}

	// if we are using delegate style but are actually delegating to another implementation
	private boolean usingDelegateHolderPackage() {
		return additionalProperties.containsKey("delegateHolderPackage");
	}

	// are we using a single path: get: as a prefix path to override other methods?
	private boolean usingPrefixPathSupport() {
		return additionalProperties.containsKey(PREFIX_ALL_PATHS_USING_GET);
	}

	// ensure we remove the
	private boolean stripPrefixPathSupportForDelegates() {
		return additionalProperties.containsKey("delegatePackageStripPrefix");
	}


	@Override
	public OperationsMap postProcessOperationsWithModels(OperationsMap objs, List<ModelMap> allModels) {
		super.postProcessOperationsWithModels(objs, allModels);

		List<CodegenOperation> codegenOperations = getCodegenOperations(objs);

		// check if the imports of the APIs have classes that have changed their package names
		// and if so, replace them
		List<Map<String, String>> imports = objs.getImports();
		for (Map<String, String> anImport : imports) {
			if (anImport.containsKey("import") && anImport.containsKey("classname")) {
				String clazzName = anImport.get("classname");
				final CodegenModel codegenModel = packageOverrideModelNames.get(clazzName);
				if (codegenModel != null) {
					anImport.put("import", codegenModel.getVendorExtensions().get("x-package") +
						"." + codegenModel.getClassname());
				}
			}
		}

		// we need to be able to prevent voracious common-pathing if APIs are scattered because Jersey
		// can't find URLs that have the same common path offset with the @Path annotation at the top of
		// the file
		String baseUrlOverride =
			Optional.ofNullable(additionalProperties.get(SERVICE_BASE)).map(Object::toString).orElse(null);

		if (baseUrlOverride != null && objs.containsKey("commonPath") && objs.get("commonPath").toString().startsWith(baseUrlOverride)) {
			String commonPath = objs.get("commonPath").toString().substring(baseUrlOverride.length());

			codegenOperations.forEach(co -> co.path = commonPath + co.path);

			objs.put("commonPath", baseUrlOverride);
		}

		if (codegenOperations.size() > 0) {
			objs.put("apiName", codegenOperations.get(0).baseName);
		}

		if (additionalProperties.containsKey(SERVICE_DEFAULT_URL)) {
			objs.put(SERVICE_DEFAULT_URL, additionalProperties.get(SERVICE_DEFAULT_URL));
		}

		String className = objs.getOperations().getClassname();

		for (CodegenOperation op : codegenOperations) {
			// need to ensure the path if it has params as <> that it uses {} instead
			op.path = op.path.replace('<', '{').replace('>', '}');

			// an Object is actually a Response header. Ideally we don't ever want to return these but occasionally they
			// are required.
			if ("Object".equals(op.returnBaseType)) {
				op.returnBaseType = "Response";
			}

			if ("void".equals(op.returnType)) {
				op.vendorExtensions.put("x-void-return", Boolean.TRUE);
			}

			if (op.produces != null) {
				int lastProducesHasNext = op.produces.size();
				for (Map<String, String> produce : op.produces) {
					lastProducesHasNext--;
					if (lastProducesHasNext > 0) {
						produce.put("hasMore", "true");
					}
				}
			}

			if (op.getHasQueryParams()) {
				final List<CodegenParameter> optionalQueryParams =
					op.allParams.stream().filter(p -> p.isQueryParam && !p.required).collect(Collectors.toList());
				if (optionalQueryParams.size() > 0) {
					op.vendorExtensions.put("x-has-delegator-holder", Boolean.TRUE);
					op.vendorExtensions.put("x-delegator-holder-params", optionalQueryParams);
					String delegateHolderPrefix = additionalProperties.containsKey("delegateHolderPackage") ?
							additionalProperties.get("delegateHolderPackage").toString() + String.format(".%sDelegate.", className)
						: "";
					// additionalProperties["delegateHolderPackage"].toString() + ".${className}Delegate." + StringUtils
					// .camelize(op.operationId + "-holder", false)
					op.vendorExtensions.put("x-class-delegator-holder", delegateHolderPrefix + camelize(op.operationId + "-holder", false));
				}
			}

			// .filter { p: CodegenParameter -> !"alSlice".equals(p.paramName) && !"alOrganisation".equals(p.paramName) }
			boolean amStrippingPrefixPath = stripPrefixPathSupportForDelegates();
			// regardless we don't want un-required query params in this one
			final String params =
				op.allParams.stream()
					.filter(p -> !p.isQueryParam || p.required)
					.filter(p -> !amStrippingPrefixPath || (p.vendorExtensions == null || !p.vendorExtensions.containsKey("x-" + PREFIX_ALL_PATHS_USING_GET) ))
					.map(p -> p.paramName)
					.collect(Collectors.joining(","));
			if (params.length() > 0) {
				op.vendorExtensions.put("x-has-java-params", Boolean.TRUE);
				op.vendorExtensions.put("x-java-params", params);
				op.vendorExtensions.put("x-java-params-plus-types",
					op.allParams.stream().filter(p -> !p.isQueryParam || p.required).map(p -> p.dataType + " " + p.paramName).collect(Collectors.joining(","))
				);
			}

			// figuring out if we need a comma in the delegate is too complicated in mustache, so we figure it out here.
			if (op.allParams != null && ((op.allParams.toArray().length != parentPathParamCount && usingPrefixPathSupport()) || (!usingPrefixPathSupport() && op.allParams.size() > 0)) && op.authMethods != null && op.authMethods.size() > 0) {
				op.vendorExtensions.put("x-has-auth", ", ");
			}

			op.responses.stream().filter(r -> r.is2xx && !"200".equalsIgnoreCase(r.code)
					&& op.returnType != null && op.returnType.equals(r.dataType)).findFirst()
				.ifPresent(resp -> op.vendorExtensions.put("statusCode", resp.code));

			if (op.responses.stream().noneMatch(r -> r.is2xx) ) {
				op.vendorExtensions.remove("x-java-is-response-void");
				op.returnType = null; // force it to be Response object
			}

			if ("void".equals(op.returnType) && !op.vendorExtensions.containsKey("statusCode")) {
				// if this is returning void, it will in fact return a 204, so lets find the first 2xx code and tag this method
				op.responses.stream()
					.filter(r -> r.is2xx && "void".equals(r.dataType))
					.findFirst()
					.ifPresent(resp -> op.vendorExtensions.put("statusCode", resp.code));
			}
		}

		return objs;
	}


	Map<String, CodegenModel> modelNames = new HashMap<>();
	Map<String, CodegenModel> packageOverrideModelNames = new HashMap<>();
	Set<String> newImportsBecausePackageOverrides = new HashSet<>();

	@Override
	public String modelFilename(String templateName, String modelName) {
		if (packageOverrideModelNames.containsKey(modelName)) {
			CodegenModel model = packageOverrideModelNames.get(modelName);

			String modelFolder =
			(this.outputFolder + File.separator + this.sourceFolder + File.separator +
				model.getVendorExtensions().get("x-package").toString().replace('.', File.separatorChar))
				.replace('/', File.separatorChar);

			String suffix = this.modelTemplateFiles().get(templateName);

			return modelFolder + File.separator + this.toModelFilename(modelName) + suffix;
		}

		return super.modelFilename(templateName, modelName);
	}

	@Override
	public Map<String, ModelsMap> postProcessAllModels(Map<String, ModelsMap> originalAllModels) {
		Map<String, ModelsMap> allModels = super.postProcessAllModels(originalAllModels);

		allModels.keySet().forEach(modelCollectionName -> {
			ModelsMap info = allModels.get(modelCollectionName);

			newImportsBecausePackageOverrides.forEach(pkg -> {
				if (!pkg.equals(info.get("package"))) {
					Map<String, String> newImports = new HashMap<>();
					newImports.put("import", pkg + ".*");
					List<Map<String, String>> imports = info.getImports();
					imports.add(newImports);
				}
			});

			Set<String> extraImports = checkForMapKeyOverride(modelCollectionName, allModels);
			// now walk through all the imports and re-write them
			List<Map<String, String>> importStatements = info.getImports();
			final String prefix = modelPackage();

			importStatements.forEach(statement -> {
				String iStatement = statement.get("import");
				if (iStatement != null && iStatement.startsWith(prefix)) {
					String statementModelName = iStatement.substring(prefix.length() + 1);
					if (packageOverrideModelNames.containsKey(statementModelName)) {
						final String newImport = packageOverrideModelNames
							.get(statementModelName).getVendorExtensions().get("x-package").toString() + "." + statementModelName;
						extraImports.remove(newImport); // no dupes - remove new package import
						statement.put("import", newImport);
					} else {
						extraImports.remove(iStatement);  // no dupes
					}
				} else if (iStatement != null) {
					extraImports.remove(iStatement);  // no dupes
				}
			});

			extraImports.forEach(i -> {
				Map<String, String> importMap = new HashMap<>();
				importMap.put("import", i);
				importStatements.add(importMap);
			});

		});

		return allModels;
	}

	private static class XPropertyRef {
		CodegenModel model;
		String importPath;

		public XPropertyRef(CodegenModel model, String importPath) {
			this.model = model;
			this.importPath = importPath;
		}
	}

	private Set<String> checkForMapKeyOverride(String modelName, Map<String, ModelsMap> modelMap) {
		Set<String> extraImports = new HashSet<>();

		ModelsMap info = modelMap.get(modelName);
		List<ModelMap> models = info.getModels();
		if (models.size() == 1) {
			CodegenModel model = (CodegenModel) models.get(0).get("model");
			if (model != null) {
				model.allVars.forEach(p -> resetMapOverrideKey(modelMap, extraImports, p));
				model.vars.forEach(p -> resetMapOverrideKey(modelMap, extraImports, p));
			}
		}

		return extraImports;
	}

	private void resetMapOverrideKey(Map<String, ModelsMap> modelMaps, Set<String> extraImports, CodegenProperty p) {
		if (p.isMap) {
			String keyType = "String";
			if (!p.getVendorExtensions().containsKey("x-property-ref")) {
				p.getVendorExtensions().put("x-property-ref", keyType);
			} else {
				String ref = p.getVendorExtensions().get("x-property-ref").toString();
				XPropertyRef refName = ref.startsWith("#/components") ? extractModelFromRef(modelMaps, ref) :
					extractModelFromShortName(modelMaps, ref);
				if (refName != null) {
					extraImports.add(refName.importPath);
					keyType = refName.model.classname;
					p.getVendorExtensions().put("x-property-ref", keyType);
					p.dataType = p.dataType.replace("<String,", "<" + keyType + ",");
					p.datatypeWithEnum = p.datatypeWithEnum.replace("<String,", "<" + keyType + ",");
				}
			}
		}
	}

	private XPropertyRef extractModelFromShortName(Map<String, ModelsMap> info, String ref) {
		ModelsMap modelInfo = info.get(ref);

		if (modelInfo != null) {
			List<ModelMap> models = modelInfo.getModels();
			if (models != null && models.size() == 1) {
				CodegenModel model = (CodegenModel) models.get(0).get("model");
				String importPath = (String) models.get(0).get("importPath");
				if (importPath != null && model != null) {
					return new XPropertyRef(model, importPath);
				}
			}
		}

		return null;
	}

	/**
	 * here we have to cut off the stuff and then return the model from the short name
	 */
	private XPropertyRef extractModelFromRef(Map<String, ModelsMap> info, String ref) {
		String shortName = ref.substring(ref.lastIndexOf("/")+1);
		return extractModelFromShortName(info, shortName);
	}


	@SuppressWarnings("unchecked")
	@Override
	public ModelsMap postProcessModels(ModelsMap originalModels) {
		final ModelsMap newModels = super.postProcessModels(originalModels);

		List<ModelMap> models = newModels.getModels();
		List<Map<String, String>> imports = newModels.getImports();
		imports.forEach((map) -> {
			if (map.containsKey("import")) {
				if (map.get("import").startsWith("io.swagger")) {
					map.remove("import");
				}
			}
		});
		imports.removeIf(Map::isEmpty);

		models.forEach(model -> {
			CodegenModel m = model.getModel();
			modelNames.put(m.classname, m);
			if (m.getVendorExtensions() != null && m.getVendorExtensions().containsKey("x-package")) {
				packageOverrideModelNames.put(m.classname, m);
				String packageName = m.getVendorExtensions().get("x-package").toString();

				newModels.put("package", packageName);
				model.put("importPath", packageName);
				newImportsBecausePackageOverrides.add(packageName);
			} else {
				newModels.put("package", this.modelPackage());
			}

			m.vars.forEach(p -> {
					if (p.getVendorExtensions().containsKey("x-basename")) {
						p.setBaseName(p.getVendorExtensions().get("x-basename").toString());
					}
				}
			);

			// there is a bug in 5.2.1 where we are getting duplicate enums
			if (m.allowableValues != null && m.allowableValues.get("enumVars") != null) {
				List<Map<String, Object>> enumVars = (List<Map<String, Object>>)m.allowableValues.get("enumVars");
				List<Map<String, Object>> newList = new ArrayList<>();
				enumVars.forEach(var -> {
					Object name = var.get("name");
					if (newList.stream().noneMatch(v -> v.get("name").equals(name))) {
						newList.add(var);
					}
				});
				m.allowableValues.put("enumVars", newList);
			}
		});

		return newModels;
	}

	@Override
	public void postProcessFile(File file, String fileType) {
		if ("java".equals(FilenameUtils.getExtension(file.toString()))) {
			try {
				final FileReader ifile = new FileReader(file);
				String inputFile = IOUtils.toString(ifile);
				ifile.close();
				String result = new Formatter(
					JavaFormatterOptions.builder()
						.style(JavaFormatterOptions.Style.GOOGLE)
						.build()).formatSourceAndFixImports(inputFile);
				if (result.trim().length() == 0) {
					log.error("Unable to format `{}`", file.getAbsolutePath());
				} else {
					final FileWriter ofile = new FileWriter(file);
					IOUtils.write(result, ofile);
					ofile.flush();
					ofile.close();
				}
			} catch (FormatterException | IOException e) {
				log.error("Failed to format file `{}`", file.getAbsolutePath(), e);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private List<CodegenOperation> getCodegenOperations(Map<String, Object> objs) {
		return (List<CodegenOperation>) getOperations(objs).get("operation");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getOperations(Map<String, Object> objs) {
		return (Map<String, Object>) objs.get("operations");
	}



	@Override
	public String toModelName(String name) {
		return (name != null) ? super.toModelName(name) : "<<unknown-to-model-name-is-null>>";
	}


	@Override
	public void addOperationToGroup(String tag, String resourcePath, Operation operation, CodegenOperation co,
	                                Map<String, List<CodegenOperation>> operations) {
		operations.computeIfAbsent(tag, k -> new ArrayList<>()).add(co);
	}

	private void addJersey2Client(String serviceName, String serviceAddress) {
		System.out.printf("Service %s - located at `%s`\n", serviceName, serviceAddress);

		// standard Spring style naming
		String className = "Enable" + camelize(serviceName, false) + "Service";
		additionalProperties.put(SERVICE_NAME, className);
		additionalProperties.put(SERVICE_ADDRESS, serviceAddress);
		additionalProperties.put("package", modelPackage());

		supportingFiles.add(new SupportingFile("enable.mustache",
			sourceFolder + "/" + apiPackage().replace('.', '/'), className + ".java"));
	}

	@Override
	public String apiFilename(String templateName, String tag) {
		String suffix = this.apiTemplateFiles().get(templateName);
		String result = this.apiFileFolder() + '/' + this.toApiFilename(tag) + suffix;
		int ix;
		if (templateName.endsWith("Impl.mustache")) {
			ix = result.lastIndexOf(47);
			result = result.substring(0, ix) + "/impl" + result.substring(ix, result.length() - 5) + "ServiceImpl.java";
		} else if (templateName.endsWith("Factory.mustache")) {
			ix = result.lastIndexOf(47);
			result = result.substring(0, ix) + "/factories" + result.substring(ix, result.length() - 5) + "ServiceFactory" +
				".java";
		} else if (templateName.endsWith("ClientService.mustache")) {
			ix = result.lastIndexOf(46);
			result = result.substring(0, ix) + "Client.java";
		} else if (templateName.endsWith("SecurityService.mustache")) {
			ix = result.lastIndexOf(46);
			result = result.substring(0, ix) + ".java";
		} else if (templateName.endsWith("DelegateServerService.mustache")) {
			ix = result.lastIndexOf(46);
			result = result.substring(0, ix) + "Delegator.java";
		} else if (templateName.endsWith("DelegateService.mustache")) {
			ix = result.lastIndexOf(46);
			result = result.substring(0, ix) + "Delegate.java";
		} else if (templateName.endsWith("Service.mustache")) {
			ix = result.lastIndexOf(46);
			result = result.substring(0, ix) + ".java";
		}

		return result;
	}


	public String toApiName(String name) {
		if (additionalProperties.get(SERVICE_NAME) != null) {
			return additionalProperties.get(SERVICE_NAME).toString();
		}

		if (name.length() == 0) {
			return "DefaultApi";
		}

		return org.openapitools.codegen.utils.StringUtils.camelize(name);
	}
}
