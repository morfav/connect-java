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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


public class SampleRunner {

  private final Jersey3ApiGenerator codegen = new Jersey3ApiGenerator();

  @Test
  public void runGenerator() {
    String location = getClass().getResource("/sample2.yaml").getFile();
    OpenAPIGenerator.main(getArgs(location, false));

    location = getClass().getResource("/upload.yaml").getFile();
    OpenAPIGenerator.main(getArgs(location, true));

    location = getClass().getResource("/nested-enum.yaml").getFile();
    OpenAPIGenerator.main(getArgs(location, false));
  }

  private String[] getArgs(String location, boolean clientAndServer) {

    List<String> args = Arrays.asList("generate",
        "--input-spec", location,
        "--generator-name", "jersey3-api",
        "--additional-properties", "server-security",
        "--additional-properties", "server-delegate",
        "--additional-properties", "x-no-copy",
//      "--api-package", "api",
//      "--model-package", "model",
        "--output", "target/" + getClass().getSimpleName());

    if (clientAndServer) {
      args = new ArrayList<>(args);
      args.addAll(
          Arrays.asList(
              "--additional-properties", "client=true",
              "--additional-properties", "server=true")
      );
    }
    return args.toArray(new String[0]);
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

}
