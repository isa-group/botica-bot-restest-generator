package es.us.isa.restest.bot.generate;

import es.us.isa.botica.bot.BotApplicationRunner;

public class TestCasesGeneratorBotLauncher {
  public static void main(String[] args){
    BotApplicationRunner.run(new TestCasesGeneratorBot(), args);
  }
}
