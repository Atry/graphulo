package edu.mit.ll.graphulo_ocean;

import com.beust.jcommander.Parameter;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.ClientConfiguration;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.Instance;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.accumulo.core.client.security.tokens.AuthenticationToken;
import org.apache.accumulo.core.client.security.tokens.PasswordToken;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Execute this in a directory that can see all the sample files.
 * Ex: java -cp "/home/gridsan/dhutchison/gits/graphulo/target/graphulo-1.0.0-SNAPSHOT-all.jar" edu.mit.ll.graphulo_ocean.OceanIngestKMers -listOfSamplesFile "/home/gridsan/dhutchison/gits/istc_oceanography/metadata/test_one_sample_filename.csv"
 * cd /home/gridsan/groups/istcdata/datasets/ocean_metagenome/csv_data/parsed
 * Ex: java -cp "/home/gridsan/dhutchison/gits/graphulo/target/graphulo-1.0.0-SNAPSHOT-all.jar" edu.mit.ll.graphulo_ocean.OceanIngestKMers -listOfSamplesFile "/home/gridsan/dhutchison/gits/istc_oceanography/metadata/valid_samples_GA02_filenames_perm.csv" -everyXLines 2 -startOffset 0 -K 11 -oTsampleDegree oTsampleDegree | tee "$HOME/node-043-ingest.log"
 * createtable oTsampleSeqRaw0
 * addsplits C T G
 */
public class OceanIngestKMers {
  private static final Logger log = LogManager.getLogger(OceanIngestKMers.class);

  public static void main(String[] args) {
    new OceanIngestKMers().execute(args);
  }

  private static class Opts extends Help {
    @Parameter(names = {"-listOfSamplesFile"}, required = true)
    public String listOfSamplesFile;

    @Parameter(names = {"-oTsampleSeqRaw"})
    public String oTsampleSeqRaw = "oTsampleSeqRaw";

    @Parameter(names = {"-everyXLines"})
    public int everyXLines = 1;

    @Parameter(names = {"-startOffset"})
    public int startOffset = 0;

    @Parameter(names = {"-txe1"})
    public String txe1 = "classdb54";

    @Parameter(names = {"-K"}, required = true)
    public int K;

    @Parameter(names = {"-oTsampleDegree"}, required = true)
    public String oTsampleDegree;

    @Override
    public String toString() {
      return "Opts{" +
          "listOfSamplesFile='" + listOfSamplesFile + '\'' +
          ", oTsampleSeqRaw='" + oTsampleSeqRaw + '\'' +
          ", everyXLines=" + everyXLines +
          ", startOffset=" + startOffset +
          ", txe1='" + txe1 + '\'' +
          ", K=" + K +
          ", oTsampleDegree='" + oTsampleDegree + '\'' +
          '}';
    }



  }

  public void execute(final String[] args) {
    Opts opts = new Opts();
    opts.parseArgs(OceanIngestKMers.class.getName(), args);
    log.info(OceanIngestKMers.class.getName() + " " + opts);

    Connector conn = setupConnector(opts.txe1);

    ingestFileList(conn, opts.listOfSamplesFile, opts.oTsampleSeqRaw,
        opts.everyXLines, opts.startOffset, opts.K, opts.oTsampleDegree);
  }

  private Connector setupConnector(String txe1) {
    String instanceName = txe1;
    String zookeeperHost = txe1+".cloud.llgrid.txe1.mit.edu:2181";
    ClientConfiguration cc = new ClientConfiguration().withInstance(instanceName).withZkHosts(zookeeperHost);// .withZkTimeout(timeout)
    Instance instance = new ZooKeeperInstance(cc);
    AuthenticationToken auth = getTXE1Authentication(txe1);
    try {
      return instance.getConnector("AccumuloUser", auth);
    } catch (AccumuloException | AccumuloSecurityException e) {
      throw new RuntimeException("Trouble authenticating to database "+txe1,e);
    }
  }

  private AuthenticationToken getTXE1Authentication(String txe1) {
    File file = new File("/home/gridsan/groups/databases/"+txe1+"/accumulo_user_password.txt");
    PasswordToken token;
    try (BufferedReader is = new BufferedReader(new FileReader(file))) {
      token = new PasswordToken(is.readLine());
    } catch (FileNotFoundException e) {
      log.error("Cannot find accumulo_user_password.txt for instance "+txe1, e);
      throw new RuntimeException(e);
    } catch (IOException e) {
      log.error("Problem reading accumulo_user_password.txt for instance " + txe1, e);
      throw new RuntimeException(e);
    }
    return token;
  }

  private void ingestFileList(Connector conn,
                              String listOfSamplesFile, String oTsampleSeqRaw,
                              int everyXLines, int startOffset, int K, String oTsampleDegree) {
    try (BufferedReader fo = new BufferedReader(new FileReader(listOfSamplesFile))) {
      for (int i = 0; i < startOffset; i++)
        fo.readLine();

      String line;
      long linecnt = 0;
      long entriesProcessed = 0;
      while ((line = fo.readLine()) != null)
        if (!line.isEmpty() && linecnt++ % everyXLines == 0) {
          log.info("Starting file: "+line);
          File file = new File(line);
          if (!file.exists() || !file.canRead()) {
            log.warn("Problem with file: "+file.getName());
            continue;
          }

          long ep = new CSVIngesterKmer(conn, K).ingestFile(file, oTsampleSeqRaw, false, oTsampleDegree);
          entriesProcessed += ep;
          log.info("Finished file: "+line+"; entries ingested: "+ep+"; cummulative: "+entriesProcessed);
        }

    } catch (IOException e) {
      throw new RuntimeException("",e);
    }
  }

}
