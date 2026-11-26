package fr.xephi.authme.velocity;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Shared helper for the verified premium-name set: keeps the in-memory names in sync with the
 * backend messages and persists them across restarts. Names are stored normalized by callers.
 */
final class PremiumNames {

    private final Logger logger;
    private final ScheduledExecutorService executor;
    private final Path cacheFile;
    private volatile Set<String> names = ConcurrentHashMap.newKeySet();
    private List<String> listBuffer = new ArrayList<>();
    private int expectedChunk = -1;

    PremiumNames(Path dataDirectory, Logger logger, ScheduledExecutorService executor) {
        this.logger = logger;
        this.executor = executor;
        this.cacheFile = dataDirectory != null ? dataDirectory.resolve("premium_names.cache") : null;
        if (cacheFile != null) {
            loadCache();
        }
    }

    boolean contains(String name) {
        return names.contains(name);
    }

    void add(String name) {
        names.add(name);
        saveAsync();
    }

    void remove(String name) {
        names.remove(name);
        saveAsync();
    }

    void replaceFromCsv(String csv) {
        Set<String> newNames = ConcurrentHashMap.newKeySet();
        addNormalized(newNames, csv);
        replace(newNames);
    }

    synchronized void handleChunk(String payload) {
        String[] parts = payload.split(":", 3);
        int sequence;
        try {
            sequence = parts.length < 3 ? -1 : Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            sequence = -1;
        }
        if (sequence < 0) {
            logger.warn("Malformed premium.list.chunk payload: {}", payload);
            return;
        }
        if (sequence == 0) {
            listBuffer = new ArrayList<>();
            expectedChunk = 0;
        }
        if (sequence != expectedChunk) {
            logger.warn("Discarding premium list: received chunk {} instead of {}", sequence, expectedChunk);
            listBuffer = new ArrayList<>();
            expectedChunk = -1;
            return;
        }
        ++expectedChunk;
        addNormalized(listBuffer, parts[2]);
        if ("1".equals(parts[1])) {
            Set<String> newNames = ConcurrentHashMap.newKeySet();
            newNames.addAll(listBuffer);
            listBuffer = new ArrayList<>();
            expectedChunk = -1;
            replace(newNames);
        }
    }

    private void replace(Set<String> newNames) {
        names = newNames;
        logger.info("Premium list received from backend: {} premium player(s)", names.size());
        saveAsync();
    }

    private static void addNormalized(Collection<String> target, String csv) {
        if (csv.isEmpty()) {
            return;
        }
        for (String name : csv.split(",")) {
            if (!name.isEmpty()) {
                target.add(VelocityAuthenticationStore.normalizeName(name.trim()));
            }
        }
    }

    private void loadCache() {
        Set<String> loaded = ConcurrentHashMap.newKeySet();
        try (BufferedReader reader = Files.newBufferedReader(cacheFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (!name.isEmpty()) {
                    loaded.add(name);
                }
            }
            names = loaded;
            logger.info("Loaded {} premium username(s) from cache", loaded.size());
        } catch (NoSuchFileException ignored) {
            // No cache yet — first startup
        } catch (IOException e) {
            logger.warn("Failed to load premium names cache: {}", e.getMessage());
        }
    }

    private void saveAsync() {
        if (cacheFile == null) {
            return;
        }
        Set<String> snapshot = new HashSet<>(names);
        executor.execute(() -> {
            Path tmp = cacheFile.resolveSibling(cacheFile.getFileName() + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                for (String name : snapshot) {
                    writer.write(name);
                    writer.newLine();
                }
                Files.move(tmp, cacheFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                logger.warn("Failed to save premium names cache: {}", e.getMessage());
            }
        });
    }
}
