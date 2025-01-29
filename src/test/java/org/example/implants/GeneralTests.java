package org.example.implants;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Set;

public class GeneralTests {
    @Test
    public void testFindAllJars_WholeM2Repo_AllJars() {
        Set<Path> allJars = SelfRepImplant.findAllJars("~/.m2/repository");

        Assert.assertFalse("Found something", allJars.isEmpty());
    }

    @Test
    public void testFindAllJars_M2RepoWithIgnores_AllJarsButTheIgnored() {
        String ignoreSpec = "~/.m2/repository/org/apache/maven;~/.m2/repository/org/springframework/boot";
        Set<Path> allJars = SelfRepImplant.findAllJars("~/.m2/repository", ignoreSpec);

        for (Path jarPath : allJars) {
            if (jarPath.toString().contains(".m2/repository/org/apache/maven")) {
                Assert.fail();
            }
            if (jarPath.toString().contains(".m2/repository/org/springframework/boot")) {
                Assert.fail();
            }
        }
    }
}
