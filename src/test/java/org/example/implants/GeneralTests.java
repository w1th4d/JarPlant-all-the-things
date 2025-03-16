package org.example.implants;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.Assert.assertTrue;

import static org.junit.Assert.*;

public class GeneralTests {
    @Test
    public void testFindAllJars_WholeM2Repo_AllJars() {
        BlockingQueue<Optional<Path>> allJars = new LinkedBlockingQueue<>();
        SelfRepImplant.findAllJars("~/.m2/repository", allJars);

        assertFalse("Found something", allJars.isEmpty());
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

    @Test
    public void testGuessExecutionContext_RunByMainStackTrace_Main() {
        // Stack trace from IntelliJ running a test app by its main function.
        // IntelliJ, vscode, Eclipse and NetBeans all produce the same output.
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.Thread", "getStackTrace", "Thread.java", 1619),
                new StackTraceElement("app", null, null, "org.example.Main", "dumpStackTraceForTests", "Main.java", 282),
                new StackTraceElement("app", null, null, "org.example.Main", "main", "Main.java", 15),
        };

        int execCtxIndicator = SelfRepImplant.guessExecutionContext(stackTrace);

        assertEquals(SelfRepImplant.EXEC_CTX_MAIN, execCtxIndicator);
    }

    @Test
    public void testGuessExecutionContext_IntellijJunitTestStackTrace_Ide() {
        // Stack trace from IntelliJ running JUnit tests.
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.Thread", "getStackTrace", "Thread.java", 1619),
                new StackTraceElement("app", null, null, "org.example.ContextTests", "dumpStackTraceForTests", "ContextTests.java", 19),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0", "NativeMethodAccessorImpl.java", -2),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke", "NativeMethodAccessorImpl.java", 77),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.DelegatingMethodAccessorImpl", "invoke", "DelegatingMethodAccessorImpl.java", 43),
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.reflect.Method", "invoke", "Method.java", 569),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod$1", "runReflectiveCall", "FrameworkMethod.java", 59),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.model.ReflectiveCallable", "run", "ReflectiveCallable.java", 12),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod", "invokeExplosively", "FrameworkMethod.java", 56),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.statements.InvokeMethod", "evaluate", "InvokeMethod.java", 17),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner$1", "evaluate", "BlockJUnit4ClassRunner.java", 100),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runLeaf", "ParentRunner.java", 366),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 103),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 63),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$4", "run", "ParentRunner.java", 331),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$1", "schedule", "ParentRunner.java", 79),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runChildren", "ParentRunner.java", 329),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "access$100", "ParentRunner.java", 66),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$2", "evaluate", "ParentRunner.java", 293),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "run", "ParentRunner.java", 413),
                new StackTraceElement("app", null, null, "org.junit.runner.JUnitCore", "run", "JUnitCore.java", 137),
                new StackTraceElement("app", null, null, "com.intellij.junit4.JUnit4IdeaTestRunner", "startRunnerWithArgs", "JUnit4IdeaTestRunner.java", 69),
                new StackTraceElement("app", null, null, "com.intellij.rt.junit.IdeaTestRunner$Repeater$1", "execute", "IdeaTestRunner.java", 38),
                new StackTraceElement("app", null, null, "com.intellij.rt.execution.junit.TestsRepeater", "repeat", "TestsRepeater.java", 11),
                new StackTraceElement("app", null, null, "com.intellij.rt.junit.IdeaTestRunner$Repeater", "startRunnerWithArgs", "IdeaTestRunner.java", 35),
                new StackTraceElement("app", null, null, "com.intellij.rt.junit.JUnitStarter", "prepareStreamsAndStart", "JUnitStarter.java", 232),
                new StackTraceElement("app", null, null, "com.intellij.rt.junit.JUnitStarter", "main", "JUnitStarter.java", 55),
        };

        int execCtxIndicator = SelfRepImplant.guessExecutionContext(stackTrace);

        assertEquals(SelfRepImplant.EXEC_CTX_IDE, execCtxIndicator);
    }

    @Test
    public void testGuessExecutionContext_VscodeJunitStackTrace_Ide() {
        // Stack trace from vscode running JUnit tests.
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.Thread", "getStackTrace", "Thread.java", 1619),
                new StackTraceElement("app", null, null, "org.example.ContextTests", "dumpStackTraceForTests", "ContextTests.java", 19),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0", "NativeMethodAccessorImpl.java", -2),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke", "NativeMethodAccessorImpl.java", 77),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.DelegatingMethodAccessorImpl", "invoke", "DelegatingMethodAccessorImpl.java", 43),
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.reflect.Method", "invoke", "Method.java", 569),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod$1", "runReflectiveCall", "FrameworkMethod.java", 59),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.model.ReflectiveCallable", "run", "ReflectiveCallable.java", 12),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod", "invokeExplosively", "FrameworkMethod.java", 56),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.statements.InvokeMethod", "evaluate", "InvokeMethod.java", 17),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner$1", "evaluate", "BlockJUnit4ClassRunner.java", 100),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runLeaf", "ParentRunner.java", 366),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 103),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 63),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$4", "run", "ParentRunner.java", 331),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$1", "schedule", "ParentRunner.java", 79),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runChildren", "ParentRunner.java", 329),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "access$100", "ParentRunner.java", 66),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$2", "evaluate", "ParentRunner.java", 293),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "run", "ParentRunner.java", 413),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference", "run", "JUnit4TestReference.java", 93),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.TestExecution", "run", "TestExecution.java", 40),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "runTests", "RemoteTestRunner.java", 520),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "runTests", "RemoteTestRunner.java", 748),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "run", "RemoteTestRunner.java", 443),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "main", "RemoteTestRunner.java", 211),
        };

        int execCtxIndicator = SelfRepImplant.guessExecutionContext(stackTrace);

        assertEquals(SelfRepImplant.EXEC_CTX_IDE, execCtxIndicator);
    }

    @Test
    public void testGuessExecutionContext_EclipseJunitStackTrace_Ide() {
        // Stack trace from Eclipse running JUnit tests.
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.Thread", "getStackTrace", "Thread.java", 1619),
                new StackTraceElement("app", null, null, "org.example.ContextTests", "dumpStackTraceForTests", "ContextTests.java", 19),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0", "NativeMethodAccessorImpl.java", -2),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke", "NativeMethodAccessorImpl.java", 77),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.DelegatingMethodAccessorImpl", "invoke", "DelegatingMethodAccessorImpl.java", 43),
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.reflect.Method", "invoke", "Method.java", 569),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod$1", "runReflectiveCall", "FrameworkMethod.java", 59),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.model.ReflectiveCallable", "run", "ReflectiveCallable.java", 12),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod", "invokeExplosively", "FrameworkMethod.java", 56),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.statements.InvokeMethod", "evaluate", "InvokeMethod.java", 17),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner$1", "evaluate", "BlockJUnit4ClassRunner.java", 100),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runLeaf", "ParentRunner.java", 366),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 103),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 63),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$4", "run", "ParentRunner.java", 331),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$1", "schedule", "ParentRunner.java", 79),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runChildren", "ParentRunner.java", 329),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "access$100", "ParentRunner.java", 66),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$2", "evaluate", "ParentRunner.java", 293),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "run", "ParentRunner.java", 413),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit4.runner.JUnit4TestReference", "run", "JUnit4TestReference.java", 93),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.TestExecution", "run", "TestExecution.java", 40),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "runTests", "RemoteTestRunner.java", 530),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "runTests", "RemoteTestRunner.java", 758),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "run", "RemoteTestRunner.java", 453),
                new StackTraceElement("app", null, null, "org.eclipse.jdt.internal.junit.runner.RemoteTestRunner", "main", "RemoteTestRunner.java", 211),
        };

        int execCtxIndicator = SelfRepImplant.guessExecutionContext(stackTrace);

        assertEquals(SelfRepImplant.EXEC_CTX_IDE, execCtxIndicator);
    }

    @Test
    @Ignore("NetBeans IDE uses Maven for running tests, so it is indistinguishable from a Maven run")
    public void testGuessExecutionContext_NetbeansJunitStackTrace_Ide() {
        // Notice that NetBeans IDE just uses Maven for running tests.
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement(null, "java.base", "21.0.6", "java.lang.Thread", "getStackTrace", "Thread.java", 2451),
                new StackTraceElement("app", null, null, "org.example.ContextTests", "dumpStackTraceForTests", "ContextTests.java", 19),
                new StackTraceElement(null, "java.base", "21.0.6", "jdk.internal.reflect.DirectMethodHandleAccessor", "invoke", "DirectMethodHandleAccessor.java", 103),
                new StackTraceElement(null, "java.base", "21.0.6", "java.lang.reflect.Method", "invoke", "Method.java", 580),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod$1", "runReflectiveCall", "FrameworkMethod.java", 59),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.model.ReflectiveCallable", "run", "ReflectiveCallable.java", 12),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod", "invokeExplosively", "FrameworkMethod.java", 56),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.statements.InvokeMethod", "evaluate", "InvokeMethod.java", 17),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner$1", "evaluate", "BlockJUnit4ClassRunner.java", 100),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runLeaf", "ParentRunner.java", 366),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 103),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 63),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$4", "run", "ParentRunner.java", 331),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$1", "schedule", "ParentRunner.java", 79),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runChildren", "ParentRunner.java", 329),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "access$100", "ParentRunner.java", 66),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$2", "evaluate", "ParentRunner.java", 293),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "run", "ParentRunner.java", 413),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.junit4.JUnit4Provider", "execute", "JUnit4Provider.java", 316),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.junit4.JUnit4Provider", "executeWithRerun", "JUnit4Provider.java", 240),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.junit4.JUnit4Provider", "executeTestSet", "JUnit4Provider.java", 214),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.junit4.JUnit4Provider", "invoke", "JUnit4Provider.java", 155),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.booter.ForkedBooter", "runSuitesInProcess", "ForkedBooter.java", 385),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.booter.ForkedBooter", "execute", "ForkedBooter.java", 162),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.booter.ForkedBooter", "run", "ForkedBooter.java", 507),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.booter.ForkedBooter", "main", "ForkedBooter.java", 495),
        };

        int execCtxIndicator = SelfRepImplant.guessExecutionContext(stackTrace);

        // This will report EXEC_CTX_BUILD_SYS because NetBeans uses Maven.
        assertEquals(SelfRepImplant.EXEC_CTX_IDE, execCtxIndicator);
    }

    @Test
    public void testGuessExecutionContext_MavenStackTrace_Ide() {
        // Stack trace from Maven running JUnit tests.
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.Thread", "getStackTrace", "Thread.java", 1619),
                new StackTraceElement("app", null, null, "org.example.ContextTests", "dumpStackTraceForTests", "ContextTests.java", 19),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0", "NativeMethodAccessorImpl.java", -2),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke", "NativeMethodAccessorImpl.java", 77),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.DelegatingMethodAccessorImpl", "invoke", "DelegatingMethodAccessorImpl.java", 43),
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.reflect.Method", "invoke", "Method.java", 569),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod$1", "runReflectiveCall", "FrameworkMethod.java", 59),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.model.ReflectiveCallable", "run", "ReflectiveCallable.java", 12),
                new StackTraceElement("app", null, null, "org.junit.runners.model.FrameworkMethod", "invokeExplosively", "FrameworkMethod.java", 56),
                new StackTraceElement("app", null, null, "org.junit.internal.runners.statements.InvokeMethod", "evaluate", "InvokeMethod.java", 17),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner$1", "evaluate", "BlockJUnit4ClassRunner.java", 100),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runLeaf", "ParentRunner.java", 366),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 103),
                new StackTraceElement("app", null, null, "org.junit.runners.BlockJUnit4ClassRunner", "runChild", "BlockJUnit4ClassRunner.java", 63),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$4", "run", "ParentRunner.java", 331),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$1", "schedule", "ParentRunner.java", 79),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "runChildren", "ParentRunner.java", 329),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "access$100", "ParentRunner.java", 66),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$2", "evaluate", "ParentRunner.java", 293),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner$3", "evaluate", "ParentRunner.java", 306),
                new StackTraceElement("app", null, null, "org.junit.runners.ParentRunner", "run", "ParentRunner.java", 413),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.junit4.JUnit4Provider", "execute", "JUnit4Provider.java", 316),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.junit4.JUnit4Provider", "executeWithRerun", "JUnit4Provider.java", 240),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.junit4.JUnit4Provider", "executeTestSet", "JUnit4Provider.java", 214),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.junit4.JUnit4Provider", "invoke", "JUnit4Provider.java", 155),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.booter.ForkedBooter", "runSuitesInProcess", "ForkedBooter.java", 385),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.booter.ForkedBooter", "execute", "ForkedBooter.java", 162),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.booter.ForkedBooter", "run", "ForkedBooter.java", 507),
                new StackTraceElement("app", null, null, "org.apache.maven.surefire.booter.ForkedBooter", "main", "ForkedBooter.java", 495),
        };

        int execCtxIndicator = SelfRepImplant.guessExecutionContext(stackTrace);

        assertEquals(SelfRepImplant.EXEC_CTX_BUILD_TOOL, execCtxIndicator);
    }

    @Test
    public void testGuessExecutionContext_GradleStackTrace_Ide() {
        // Stack trace from Gradle running JUnit tests.
        StackTraceElement[] stackTrace = new StackTraceElement[]{
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.Thread", "getStackTrace", "Thread.java", 1619),
                new StackTraceElement("app", null, null, "org.example.Main", "dumpStackTraceForTests", "Main.java", 282),
                new StackTraceElement("app", null, null, "org.example.ContextTests", "testContext", "ContextTests.java", 16),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0", "NativeMethodAccessorImpl.java", -2),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke", "NativeMethodAccessorImpl.java", 77),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.DelegatingMethodAccessorImpl", "invoke", "DelegatingMethodAccessorImpl.java", 43),
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.reflect.Method", "invoke", "Method.java", 569),
                new StackTraceElement("app", null, null, "org.junit.platform.commons.util.ReflectionUtils", "invokeMethod", "ReflectionUtils.java", 688),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.MethodInvocation", "proceed", "MethodInvocation.java", 60),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.InvocationInterceptorChain$ValidatingInvocation", "proceed", "InvocationInterceptorChain.java", 131),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.extension.TimeoutExtension", "intercept", "TimeoutExtension.java", 149),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.extension.TimeoutExtension", "interceptTestableMethod", "TimeoutExtension.java", 140),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.extension.TimeoutExtension", "interceptTestMethod", "TimeoutExtension.java", 84),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.ExecutableInvoker$ReflectiveInterceptorCall", "lambda$ofVoidMethod$0", "ExecutableInvoker.java", 115),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.ExecutableInvoker", "lambda$invoke$0", "ExecutableInvoker.java", 105),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.InvocationInterceptorChain$InterceptedInvocation", "proceed", "InvocationInterceptorChain.java", 106),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.InvocationInterceptorChain", "proceed", "InvocationInterceptorChain.java", 64),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.InvocationInterceptorChain", "chainAndInvoke", "InvocationInterceptorChain.java", 45),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.InvocationInterceptorChain", "invoke", "InvocationInterceptorChain.java", 37),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.ExecutableInvoker", "invoke", "ExecutableInvoker.java", 104),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.execution.ExecutableInvoker", "invoke", "ExecutableInvoker.java", 98),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor", "lambda$invokeTestMethod$6", "TestMethodTestDescriptor.java", 210),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.ThrowableCollector", "execute", "ThrowableCollector.java", 73),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor", "invokeTestMethod", "TestMethodTestDescriptor.java", 206),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor", "execute", "TestMethodTestDescriptor.java", 131),
                new StackTraceElement("app", null, null, "org.junit.jupiter.engine.descriptor.TestMethodTestDescriptor", "execute", "TestMethodTestDescriptor.java", 65),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$5", "NodeTestTask.java", 139),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.ThrowableCollector", "execute", "ThrowableCollector.java", 73),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$7", "NodeTestTask.java", 129),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.Node", "around", "Node.java", 137),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$8", "NodeTestTask.java", 127),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.ThrowableCollector", "execute", "ThrowableCollector.java", 73),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "executeRecursively", "NodeTestTask.java", 126),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "execute", "NodeTestTask.java", 84),
                new StackTraceElement(null, "java.base", "17.0.14", "java.util.ArrayList", "forEach", "ArrayList.java", 1511),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService", "invokeAll", "SameThreadHierarchicalTestExecutorService.java", 38),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$5", "NodeTestTask.java", 143),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.ThrowableCollector", "execute", "ThrowableCollector.java", 73),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$7", "NodeTestTask.java", 129),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.Node", "around", "Node.java", 137),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$8", "NodeTestTask.java", 127),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.ThrowableCollector", "execute", "ThrowableCollector.java", 73),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "executeRecursively", "NodeTestTask.java", 126),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "execute", "NodeTestTask.java", 84),
                new StackTraceElement(null, "java.base", "17.0.14", "java.util.ArrayList", "forEach", "ArrayList.java", 1511),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService", "invokeAll", "SameThreadHierarchicalTestExecutorService.java", 38),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$5", "NodeTestTask.java", 143),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.ThrowableCollector", "execute", "ThrowableCollector.java", 73),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$7", "NodeTestTask.java", 129),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.Node", "around", "Node.java", 137),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "lambda$executeRecursively$8", "NodeTestTask.java", 127),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.ThrowableCollector", "execute", "ThrowableCollector.java", 73),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "executeRecursively", "NodeTestTask.java", 126),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.NodeTestTask", "execute", "NodeTestTask.java", 84),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService", "submit", "SameThreadHierarchicalTestExecutorService.java", 32),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.HierarchicalTestExecutor", "execute", "HierarchicalTestExecutor.java", 57),
                new StackTraceElement("app", null, null, "org.junit.platform.engine.support.hierarchical.HierarchicalTestEngine", "execute", "HierarchicalTestEngine.java", 51),
                new StackTraceElement("app", null, null, "org.junit.platform.launcher.core.EngineExecutionOrchestrator", "execute", "EngineExecutionOrchestrator.java", 108),
                new StackTraceElement("app", null, null, "org.junit.platform.launcher.core.EngineExecutionOrchestrator", "execute", "EngineExecutionOrchestrator.java", 88),
                new StackTraceElement("app", null, null, "org.junit.platform.launcher.core.EngineExecutionOrchestrator", "lambda$execute$0", "EngineExecutionOrchestrator.java", 54),
                new StackTraceElement("app", null, null, "org.junit.platform.launcher.core.EngineExecutionOrchestrator", "withInterceptedStreams", "EngineExecutionOrchestrator.java", 67),
                new StackTraceElement("app", null, null, "org.junit.platform.launcher.core.EngineExecutionOrchestrator", "execute", "EngineExecutionOrchestrator.java", 52),
                new StackTraceElement("app", null, null, "org.junit.platform.launcher.core.DefaultLauncher", "execute", "DefaultLauncher.java", 96),
                new StackTraceElement("app", null, null, "org.junit.platform.launcher.core.DefaultLauncher", "execute", "DefaultLauncher.java", 75),
                new StackTraceElement(null, null, null, "org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor$CollectAllTestClassesExecutor", "processAllTestClasses", "JUnitPlatformTestClassProcessor.java", 124),
                new StackTraceElement(null, null, null, "org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor$CollectAllTestClassesExecutor", "access$000", "JUnitPlatformTestClassProcessor.java", 99),
                new StackTraceElement(null, null, null, "org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestClassProcessor", "stop", "JUnitPlatformTestClassProcessor.java", 94),
                new StackTraceElement(null, null, null, "org.gradle.api.internal.tasks.testing.SuiteTestClassProcessor", "stop", "SuiteTestClassProcessor.java", 63),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke0", "NativeMethodAccessorImpl.java", -2),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.NativeMethodAccessorImpl", "invoke", "NativeMethodAccessorImpl.java", 77),
                new StackTraceElement(null, "java.base", "17.0.14", "jdk.internal.reflect.DelegatingMethodAccessorImpl", "invoke", "DelegatingMethodAccessorImpl.java", 43),
                new StackTraceElement(null, "java.base", "17.0.14", "java.lang.reflect.Method", "invoke", "Method.java", 569),
                new StackTraceElement(null, null, null, "org.gradle.internal.dispatch.ReflectionDispatch", "dispatch", "ReflectionDispatch.java", 36),
                new StackTraceElement(null, null, null, "org.gradle.internal.dispatch.ReflectionDispatch", "dispatch", "ReflectionDispatch.java", 24),
                new StackTraceElement(null, null, null, "org.gradle.internal.dispatch.ContextClassLoaderDispatch", "dispatch", "ContextClassLoaderDispatch.java", 33),
                new StackTraceElement(null, null, null, "org.gradle.internal.dispatch.ProxyDispatchAdapter$DispatchingInvocationHandler", "invoke", "ProxyDispatchAdapter.java", 92),
                new StackTraceElement(null, "jdk.proxy1", null, "jdk.proxy1.$Proxy4", "stop", null, -1),
                new StackTraceElement(null, null, null, "org.gradle.api.internal.tasks.testing.worker.TestWorker$3", "run", "TestWorker.java", 200),
                new StackTraceElement(null, null, null, "org.gradle.api.internal.tasks.testing.worker.TestWorker", "executeAndMaintainThreadName", "TestWorker.java", 132),
                new StackTraceElement(null, null, null, "org.gradle.api.internal.tasks.testing.worker.TestWorker", "execute", "TestWorker.java", 103),
                new StackTraceElement(null, null, null, "org.gradle.api.internal.tasks.testing.worker.TestWorker", "execute", "TestWorker.java", 63),
                new StackTraceElement(null, null, null, "org.gradle.process.internal.worker.child.ActionExecutionWorker", "execute", "ActionExecutionWorker.java", 56),
                new StackTraceElement(null, null, null, "org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker", "call", "SystemApplicationClassLoaderWorker.java", 121),
                new StackTraceElement(null, null, null, "org.gradle.process.internal.worker.child.SystemApplicationClassLoaderWorker", "call", "SystemApplicationClassLoaderWorker.java", 71),
                new StackTraceElement("app", null, null, "worker.org.gradle.process.internal.worker.GradleWorkerMain", "run", "GradleWorkerMain.java", 69),
                new StackTraceElement("app", null, null, "worker.org.gradle.process.internal.worker.GradleWorkerMain", "main", "GradleWorkerMain.java", 74),
        };

        int execCtxIndicator = SelfRepImplant.guessExecutionContext(stackTrace);

        assertEquals(SelfRepImplant.EXEC_CTX_BUILD_TOOL, execCtxIndicator);
    }

    @Test
    public void testShouldExecute_HostnameRegexes_Matches() {
        // Arrange
        int dummyExecCtx = SelfRepImplant.EXEC_CTX_IDE;
        SelfRepImplant.CONF_RUN_FROM_IDE = true;
        SelfRepImplant.CONF_TARGET_HOSTNAME_REGEX = ".*jenkins.*";

        // Assert positive cases
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "jenkins"));
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "jenkins01"));
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "jenkins-worker01"));
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "test-jenkins"));
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "test-jenkins-01"));
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "leroyjenkins"));

        // Assert negative cases
        assertFalse(SelfRepImplant.shouldExecute(dummyExecCtx, "derpins"));
        assertFalse(SelfRepImplant.shouldExecute(dummyExecCtx, "jenk"));
        assertFalse(SelfRepImplant.shouldExecute(dummyExecCtx, "ins"));
    }

    @Test
    public void testShouldExecute_NoHostNameRegex_AnythingGoes() {
        // Arrange
        int dummyExecCtx = SelfRepImplant.EXEC_CTX_IDE;
        SelfRepImplant.CONF_RUN_FROM_IDE = true;

        // Act + Assert
        SelfRepImplant.CONF_TARGET_HOSTNAME_REGEX = null;
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "whatever"));
        SelfRepImplant.CONF_TARGET_HOSTNAME_REGEX = "";
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "whatever"));
        SelfRepImplant.CONF_TARGET_HOSTNAME_REGEX = ".*";
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, "whatever"));
    }

    @Test
    public void testShouldExecute_NoHostnameFound_ExecuteAnyway() {
        // Arrange
        int dummyExecCtx = SelfRepImplant.EXEC_CTX_IDE;
        SelfRepImplant.CONF_RUN_FROM_IDE = true;

        // Act + Assert
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, null));
        assertTrue(SelfRepImplant.shouldExecute(dummyExecCtx, ""));
    }
}
