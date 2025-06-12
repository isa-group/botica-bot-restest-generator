package es.us.isa.restest.bot.generate;

import es.us.isa.botica.bot.BaseBot;
import es.us.isa.botica.bot.OrderHandler;
import es.us.isa.botica.bot.ProactiveTask;
import es.us.isa.botica.bot.shutdown.ShutdownRequestHandler;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates batches of test cases and their respective test classes using the configuration
 * provided through the {@code USER_CONFIG_PATH} environment variable.
 *
 * @author Alberto Mimbrero
 */
public class TestCasesGeneratorBot extends BaseBot {
  private static final Logger log = LoggerFactory.getLogger(TestCasesGeneratorBot.class);
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneId.systemDefault());

  private TestCaseBatchGenerator generator;
  private Instant pauseExpiration = Instant.now();

  @Override
  public void configure() {
    String userConfigPath = System.getenv("USER_CONFIG_PATH");
    if (userConfigPath == null) {
      throw new IllegalStateException(
          "no USER_CONFIG_PATH environment variable was specified for this bot!");
    }

    this.generator = new TestCaseBatchGenerator(userConfigPath);
    this.setupProxyBotIntegration();
  }

  private void setupProxyBotIntegration() {
    String proxyBotId = System.getenv("PROXY_BOT_ID");
    if (proxyBotId != null) {
      this.generator.setupProxy(this.getBotHostname(proxyBotId));
    }
  }

  @Override
  public void onStart() {
    StringBuilder message = new StringBuilder("Starting generation.");
    if (System.getenv("PROXY_BOT_ID") != null) {
      message.append(" Using proxy.");
    }
    this.sendTelegramMessage(message.toString());
  }

  @ProactiveTask
  public void executeAction() {
    if (this.pauseExpiration.isAfter(Instant.now())) {
      return;
    }
    JSONObject result = this.generator.generateBatch();
    this.publishOrder("executor_bots", "execute_test_cases", result.toString());
  }

  @OrderHandler("pause_service")
  public void pauseService(JSONObject details) {
    if (!details.getString("service").equals(this.generator.getServiceHost())) {
      return;
    }

    this.pauseExpiration = Instant.ofEpochMilli(details.getLong("until"));
    log.info("Pausing generation until {}", DATE_TIME_FORMATTER.format(this.pauseExpiration));
  }

  @ShutdownRequestHandler
  public void onShutdownRequest() {
    this.sendTelegramMessage("Shutting down...");
  }

  private void sendTelegramMessage(String message) {
    this.publishOrder(
        "telegram_bot",
        "send_message",
        new JSONObject()
            .put("recipient", "broadcast")
            .put("content", String.format("[%s] %s", this.getBotId(), message))
            .toString());
  }
}
