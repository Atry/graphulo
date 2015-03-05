package edu.mit.ll.graphulo;

import org.apache.accumulo.core.data.*;
import org.apache.accumulo.core.iterators.*;
import org.apache.accumulo.core.util.PeekingIterator;
import org.apache.hadoop.io.Text;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.*;

/**
 * Interleave the hardcoded values
 (row1,colF3,colQ3,1)
 (row4,colF3,colQ3,1)
 (row7,colF3,colQ3,1)
 */
public class InjectIterator extends BranchIterator implements OptionDescriber {
    private static final Logger log = LogManager.getLogger(InjectIterator.class);


    @Override
    public SortedKeyValueIterator<Key,Value> initBranchIterator(Map<String, String> options, IteratorEnvironment env) throws IOException {

        return new HardListIterator();
//        side.init(null, null, env);
//        env.registerSideChannel( side );
    }



    @Override
    public OptionDescriber.IteratorOptions describeOptions() {
        return new OptionDescriber.IteratorOptions("inject", "injects hard-coded entries into iterator stream.", null, null);
    }

    @Override
    public boolean validateOptions(Map<String,String> options) {
        return true;
    }
}
