package edu.mit.ll.graphulo;

import edu.mit.ll.graphulo.skvi.RemoteSourceIterator;
import edu.mit.ll.graphulo.util.AccumuloTestBase;
import edu.mit.ll.graphulo.util.TestUtil;
import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchScanner;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.IteratorSetting;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.io.Text;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Holds tests that reproduce Accumulo bugs.
 */
public class AccumuloBugTest extends AccumuloTestBase {
  private static final Logger log = LogManager.getLogger(AccumuloBugTest.class);

  @Test
  public void testNoDeadlockWithFewScans() throws TableNotFoundException, AccumuloSecurityException, AccumuloException {
    // less than 16 tablets will not deadlock.
    testScansOnTablets(14);
  }

  /**
   * This will deadlock an Accumulo tablet server if numtablets >= 16 and there is only one tablet server.
   */
  @Ignore("NORUN KnownBug ACCUMULO-XXXX")
  @Test
  public void testDeadlockWithManyScans() throws TableNotFoundException, AccumuloSecurityException, AccumuloException {
    // 16 is the default max number of threads allowed in the read-ahead pool. >16 tablets means a deadlock is possible.
    testScansOnTablets(50);
  }

  private void testScansOnTablets(final int numtablets) throws TableNotFoundException, AccumuloSecurityException, AccumuloException {
    Connector connector = tester.getConnector();
    final String tA = getUniqueNames(1)[0];

    // create a table with given number of tablets, inserting one entry into each tablet
    Map<Key, Value> input = new TreeMap<>(TestUtil.COMPARE_KEY_TO_COLQ);
    SortedSet<Text> splits = new TreeSet<>();
    for (int i = 0; i < numtablets; i++) {
      String row = StringUtils.leftPad(Integer.toString(1+2*i), 2, '0');                  // 01, 03, 05, ...
      input.put(new Key(row, "", ""), new Value(Integer.toString(1+2*i).getBytes()));
      if (i > 0)
        splits.add(new Text(StringUtils.leftPad(Integer.toString(2 * (i - 1)), 2, '0'))); // --, 02, 04, ...
    }
    TestUtil.createTestTable(connector, tA, splits, input);

    IteratorSetting itset = RemoteSourceIterator.iteratorSetting(
        25, connector.getInstance().getZooKeepers(), 5000, connector.getInstance().getInstanceName(),
        tA, connector.whoami(), new String(tester.getPassword().getPassword()), Authorizations.EMPTY, null, null,
        false, null);

    BatchScanner bs = connector.createBatchScanner(tA, Authorizations.EMPTY, numtablets); // scan every tablet
    bs.setRanges(Collections.singleton(new Range()));
    bs.addScanIterator(itset);

    SortedMap<Key, Value> actual = new TreeMap<>(TestUtil.COMPARE_KEY_TO_COLQ);
    try {
      for (Map.Entry<Key, Value> entry : bs) {                    // DEADLOCK TabletServer!
        actual.put(entry.getKey(), entry.getValue());
        log.debug("Entry "+actual.size()+": "+entry.getKey()+"    "+entry.getValue());
      }
    } finally {
      bs.close();
    }
    Assert.assertEquals(input, actual);

    connector.tableOperations().delete(tA);
  }

}
