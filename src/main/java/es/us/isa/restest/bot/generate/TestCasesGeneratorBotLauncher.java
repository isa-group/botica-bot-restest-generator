package es.us.isa.restest.bot.generate;

import es.us.isa.botica.bot.BotLauncher;

public class TestCasesGeneratorBotLauncher {
  public static void main(String[] args) {
    BotLauncher.run(new TestCasesGeneratorBot(), args);
  }
}
