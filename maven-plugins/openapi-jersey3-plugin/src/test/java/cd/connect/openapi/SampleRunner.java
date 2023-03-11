package cd.connect.openapi;

import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.core.models.ParseOptions;
import org.junit.Test;
import org.openapitools.codegen.ClientOptInput;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.OpenAPIGenerator;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


public class SampleRunner {

  private final Jersey3ApiGenerator codegen = new Jersey3ApiGenerator();

  @Test
  public void runGenerator() {
    String location = getClass().getResource("/sample2.yaml").getFile();
    OpenAPIGenerator.main(Arrays.asList("generate",
      "--input-spec", location,
      "--generator-name", "jersey3-api",
      "--additional-properties", "server-security",
		  "--additional-properties", "server-delegate",
      "--additional-properties", "x-no-copy",
//      "--api-package", "api",
//      "--model-package", "model",
      "--output", "target/" + getClass().getSimpleName())
      .toArray(new String[0]));
  }
//  @Test
//  public void runFHGenerator() {
//    String location = "/Users/richard/projects/fh/featurehub/backend/mr-api/final.yaml";
//    OpenAPIGenerator.main(Arrays.asList("generate",
//      "--input-spec", location,
//      "--generator-name", "jersey2-api",
//      "--additional-properties", "server-delegate",
//      "--additional-properties", "serviceUrlBase=/mr-api",
//      "--output", "/Users/richard/projects/fh/featurehub/backend/mr-api/target/mr-api")
//      .toArray(new String[0]));
//  }

  @Test
  public void testMultipartForm() throws Exception {

    String outputPath = "target/generated-test-sources/openapi/src/gen/java";
    Files.createDirectories(Paths.get(outputPath));

    OpenAPI openAPI = new OpenAPIParser()
        .readLocation("upload.yaml", null, new ParseOptions()).getOpenAPI();

    codegen.setOutputDir(outputPath);
    codegen.additionalProperties().put("client", true);
    codegen.additionalProperties().put("server", true);

    ClientOptInput input = new ClientOptInput()
        .openAPI(openAPI)
        .config(codegen);

    DefaultGenerator generator = new DefaultGenerator();
    Map<String, File> files = generator.opts(input).generate().stream()
        .collect(Collectors.toMap(File::getName, Function.identity()));

    System.out.format("%s%n", files.get("FileServiceClient.java").getAbsolutePath());
    System.out.format("%s%n", files.get("FileServiceServiceImpl.java").getAbsolutePath());
  }
}
