package org.example.implants;

import org.junit.Assert;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertTrue;

public class GeneralTests {
    @Test
    public void testFindAllJars_WholeM2Repo_AllJars() {
        BlockingQueue<Optional<Path>> allJars = new LinkedBlockingQueue<>();
        SelfRepImplant.findAllJars("~/.m2/repository", allJars);

        Assert.assertFalse("Found something", allJars.isEmpty());
    }

    @Test
    public void testFindAllJars_M2RepoWithIgnores_AllJarsButTheIgnored() {
        String ignoreSpec = "~/.m2/repository/org/apache/maven;~/.m2/repository/org/springframework/boot";
        BlockingQueue<Optional<Path>> allJars = new LinkedBlockingQueue<>();
        SelfRepImplant.findAllJars("~/.m2/repository", ignoreSpec, allJars);

        for (Optional<Path> jarPath : allJars) {
            assertTrue(jarPath.isPresent());
            if (jarPath.toString().contains(".m2/repository/org/apache/maven")) {
                Assert.fail();
            }
            if (jarPath.toString().contains(".m2/repository/org/springframework/boot")) {
                Assert.fail();
            }
        }
    }
}
