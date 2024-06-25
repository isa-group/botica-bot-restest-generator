package es.us.isa.restest.runners;

/** Custom {@link RESTestLoader} for necessary missing property accessors. */
public class BoticaRESTestLoader extends RESTestLoader {
  public BoticaRESTestLoader(String userPropertiesFilePath) {
    super(userPropertiesFilePath);
  }

  public void setExperimentName(String experimentName) {
    this.experimentName = experimentName;
  }
}
