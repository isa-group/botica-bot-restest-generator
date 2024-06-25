package es.us.isa.restest.bot.generate;

import es.us.isa.botica.bot.AbstractBotApplication;
import es.us.isa.restest.generators.AbstractTestCaseGenerator;
import es.us.isa.restest.runners.BoticaRESTestLoader;
import es.us.isa.restest.testcases.TestCase;
import es.us.isa.restest.util.RESTestException;
import es.us.isa.restest.writers.restassured.RESTAssuredWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import org.json.JSONObject;

/**
 * Generates batches of test cases and their respective test classes using the configuration
 * provided through the {@code USER_CONFIG_PATH} environment variable.
 *
 * @author Alberto Mimbrero
 */
public class TestCasesGeneratorBot extends AbstractBotApplication {
  private String userConfigPath;
  private BoticaRESTestLoader loader;

  private String baseExperimentName;

  @Override
  public void configure() {
    this.userConfigPath = System.getenv("USER_CONFIG_PATH");
    this.loader = new BoticaRESTestLoader(this.userConfigPath);
    this.baseExperimentName = this.loader.getExperimentName();
  }

  @Override
  public void executeAction() {
    String batchId = createBatchId();
    String className;

    // temporary fix for the test reporter, documented in botica-bot-restest-reporter
    this.loader.setExperimentName(this.baseExperimentName.concat("-").concat(batchId));

    try {
      AbstractTestCaseGenerator generator = this.loader.createGenerator();
      Collection<TestCase> testCases = generator.generate();
      this.saveToFile(testCases, batchId);

      RESTAssuredWriter writer = (RESTAssuredWriter) this.loader.createWriter();

      // Append the batch id to the class name to allow queuing executions or running multiple
      // concurrently.
      className = writer.getClassName().concat("_").concat(batchId);
      writer.setClassName(className);

      writer.write(testCases);
    } catch (IOException | RESTestException e) {
      throw new RuntimeException(e);
    }

    publishOrder(
        new JSONObject()
            .put("batchId", batchId)
            .put("userConfigPath", this.userConfigPath)
            .put("testClassName", className)
            .toString());
  }

  private void saveToFile(Collection<TestCase> testCases, String batchId) throws IOException {
    Path targetPath = Files.createDirectories(Path.of(loader.getTargetDirJava()));
    File file = targetPath.resolve(batchId).toFile();

    try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
      try (ObjectOutputStream out = new ObjectOutputStream(fileOutputStream)) {
        out.writeObject(testCases);
      }
    }
  }

  private static String createBatchId() {
    return UUID.randomUUID().toString().substring(0, 13).replace("-", "_");
  }
}
