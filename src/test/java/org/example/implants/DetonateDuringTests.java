package org.example.implants;

import org.junit.Ignore;
import org.junit.Test;

public class DetonateDuringTests {
    @Test
    @Ignore("Don't detonate the implant during tests by default")
    public void testDetonate() {
        SelfRepImplant implant = new SelfRepImplant(SelfRepImplant.guessExecutionContext());
        implant.run();
    }
}
