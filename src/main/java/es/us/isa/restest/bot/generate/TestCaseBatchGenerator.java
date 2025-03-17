package es.us.isa.restest.bot.generate;

import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import io.swagger.v3.oas.models.OpenAPI;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import javax.annotation.Nullable;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONObject;

public class TestCaseBatchGenerator {
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

  private final String userConfigPath;
  private final RESTestLoader loader;
  private String serviceHost;

  public TestCaseBatchGenerator(String userConfigPath) {
    this.userConfigPath = userConfigPath;
    this.loader = new RESTestLoader(this.userConfigPath);
  }

  public void setupProxy(String proxyHostname) {
    OpenAPI specification = this.loader.getSpec().getSpecification();
    this.serviceHost = specification.getServers().get(0).getUrl();
    try {
      URL proxyUrl = new URL("http", proxyHostname, "");
      specification.getServers().get(0).setUrl(proxyUrl.toString());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }

    String header = "Proxy-Redirect: " + this.serviceHost;
    this.loader.setHeaders(ArrayUtils.add(this.loader.getHeaders(), header));
  }

  public JSONObject generateBatch() {
    String batchId = generateBatchId();

    try {
      AbstractTestCaseGenerator generator = this.loader.createGenerator();
      Collection<TestCase> testCases = generator.generate();
      this.saveTestCases(testCases, batchId);

      String className = generateTestClassName(loader.getTestClassName(), batchId);
      this.writeTestClass(className, batchId, testCases);

      return new JSONObject()
          .put("batchId", batchId)
          .put("userConfigPath", this.userConfigPath)
          .put("testClassName", className);
    } catch (IOException | RESTestException e) {
      throw new RuntimeException(e);
    }
  }

  private void saveTestCases(Collection<TestCase> testCases, String batchId) throws IOException {
    Path targetPath = Files.createDirectories(Path.of(loader.getTargetDirJava()));
    File file = targetPath.resolve(batchId).toFile();

    try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
      try (ObjectOutputStream out = new ObjectOutputStream(fileOutputStream)) {
        out.writeObject(testCases);
      }
    }
  }

  private void writeTestClass(String className, String batchId, Collection<TestCase> testCases) {
    RESTAssuredWriter writer = (RESTAssuredWriter) this.loader.createWriter();
    writer.setClassName(className);
    writer.setTestId(batchId);

    writer.write(testCases);
  }

  private static String generateBatchId() {
    return DATE_TIME_FORMATTER.format(LocalDateTime.now());
  }

  private static String generateTestClassName(String baseClassName, String batchId) {
    String id = batchId.replaceAll("[^a-zA-Z0-9]", "_");
    return baseClassName.concat("_").concat(id);
  }

  public String getServiceHost() {
    return serviceHost;
  }
}
