package es.us.isa.restest.bot.generate;

import es.us.isa.botica.bot.AbstractBotApplication;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.runners.RESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import org.json.JSONObject;

/**
 * Generates batches of test cases and their respective test classes using the configuration
 * provided through the {@code USER_CONFIG_PATH} environment variable.
 *
 * @author Alberto Mimbrero
 */
public class TestCasesGeneratorBot extends AbstractBotApplication {
  public static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  private String userConfigPath;
  private RESTestLoader loader;

  @Override
  public void configure() {
    this.userConfigPath = System.getenv("USER_CONFIG_PATH");
    if (this.userConfigPath == null) {
      throw new IllegalStateException(
          "no USER_CONFIG_PATH environment variable was specified for this bot!");
    }
    this.loader = new RESTestLoader(this.userConfigPath);
  }

  @Override
  public void executeAction() {
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
