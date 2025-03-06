package es.us.isa.restest.bot.generate;

import es.us.isa.botica.bot.AbstractBotApplication;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import org.apache.commons.lang3.ArrayUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates batches of test cases and their respective test classes using the configuration
 * provided through the {@code USER_CONFIG_PATH} environment variable.
 *
 * @author Alberto Mimbrero
 */
public class TestCasesGeneratorBot extends AbstractBotApplication {
  private static final Logger log = LoggerFactory.getLogger(TestCasesGeneratorBot.class);
  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

  private String userConfigPath;
  private RESTestLoader loader;
  private String serviceHost;

  private Instant blockGenerationUntil = Instant.now();

  @Override
  public void configure() {
    this.userConfigPath = System.getenv("USER_CONFIG_PATH");
    if (this.userConfigPath == null) {
      throw new IllegalStateException(
          "no USER_CONFIG_PATH environment variable was specified for this bot!");
    }

    this.loader = new RESTestLoader(this.userConfigPath);

    String proxyBotId = System.getenv("PROXY_BOT_ID");
    if (proxyBotId != null) {
      this.setupProxy(proxyBotId);
    }

    this.registerOrderListener("restrict_generation", this::restrictGeneration);
  }

  private void setupProxy(String proxyBotId) {
    OpenAPI specification = this.loader.getSpec().getSpecification();
    this.serviceHost = specification.getServers().get(0).getUrl();
    try {
      URL proxyUrl = new URL("http", this.getBotHostname(proxyBotId), "");
      specification.getServers().get(0).setUrl(proxyUrl.toString());
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }

    String header = "Proxy-Redirect: " + this.serviceHost;
    this.loader.setHeaders(ArrayUtils.add(this.loader.getHeaders(), header));
  }

  private void restrictGeneration(String order, String message) {
    JSONObject parameters = new JSONObject(message);
    if (!parameters.getString("service").equals(this.serviceHost)) {
      return;
    }

    this.blockGenerationUntil = Instant.ofEpochMilli(parameters.getLong("until"));
    log.info("Stopping generation until {}", DATE_TIME_FORMATTER.format(this.blockGenerationUntil));
  }

  @Override
  public void executeAction() {
    if (this.blockGenerationUntil.isAfter(Instant.now())) {
      return;
    }

    String batchId = generateBatchId();

    try {
      AbstractTestCaseGenerator generator = this.loader.createGenerator();
      Collection<TestCase> testCases = generator.generate();
      this.saveTestCases(testCases, batchId);

      String className = generateTestClassName(loader.getTestClassName(), batchId);
      this.writeTestClass(className, batchId, testCases);

      publishOrder(
          new JSONObject()
              .put("batchId", batchId)
              .put("userConfigPath", this.userConfigPath)
              .put("testClassName", className)
              .toString());
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
}
