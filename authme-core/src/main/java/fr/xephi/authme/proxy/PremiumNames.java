package fr.xephi.authme.proxy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;

/**
 * Shared helper for the verified premium-name set: keeps the in-memory names in sync with the
 * backend messages and persists them across restarts. Names are stored normalized by callers.
 * Each proxy module supplies its own logger and name-normalization function.
 */
public class PremiumNames {

    /** Minimal logging bridge so each proxy module keeps using its own logger API. */
    public interface LoggerAdapter {
        void info(String message);

        void warning(String message);
    }

    private final LoggerAdapter logger;
    private final Function<String, String> nameNormalizer;
    private final ScheduledExecutorService executor;
    private final Path cacheFile;
    private volatile Set<String> names = ConcurrentHashMap.newKeySet();
    private Set<String> listBuffer = ConcurrentHashMap.newKeySet();
    private int expectedChunk = -1;

    public PremiumNames(Path dataDirectory, LoggerAdapter logger, ScheduledExecutorService executor,
                        Function<String, String> nameNormalizer) {
        this.logger = logger;
        this.nameNormalizer = nameNormalizer;
        this.executor = executor;
        this.cacheFile = dataDirectory != null ? dataDirectory.resolve("premium_names.cache") : null;
        if (cacheFile != null) {
            loadCache();
        }
    }

    public boolean contains(String name) {
        return names.contains(name);
    }

    public void add(String name) {
        names.add(name);
        saveAsync();
    }

    public void remove(String name) {
        names.remove(name);
        saveAsync();
    }

    public void replaceFromCsv(String csv) {
        Set<String> newNames = ConcurrentHashMap.newKeySet();
        addNormalized(newNames, csv);
        replace(newNames);
    }

    public synchronized void handleChunk(String payload) {
        String[] parts = payload.split(":", 3);
        int sequence;
        try {
            sequence = parts.length < 3 ? -1 : Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            sequence = -1;
        }
        if (sequence < 0) {
            logger.warning("Malformed premium.list.chunk payload: " + payload);
            return;
        }
        if (sequence == 0) {
            listBuffer = ConcurrentHashMap.newKeySet();
            expectedChunk = 0;
        }
        if (sequence != expectedChunk) {
            logger.warning("Discarding premium list: received chunk " + sequence + " instead of " + expectedChunk);
            listBuffer = ConcurrentHashMap.newKeySet();
            expectedChunk = -1;
            return;
        }
        ++expectedChunk;
        addNormalized(listBuffer, parts[2]);
        if ("1".equals(parts[1])) {
            replace(listBuffer);
            listBuffer = ConcurrentHashMap.newKeySet();
            expectedChunk = -1;
        }
    }

    private void replace(Set<String> newNames) {
        names = newNames;
        logger.info("Premium list received from backend: " + names.size() + " premium player(s)");
        saveAsync();
    }

    private void addNormalized(Collection<String> target, String csv) {
        if (csv.isEmpty()) {
            return;
        }
        for (String name : csv.split(",")) {
            if (!name.isEmpty()) {
                target.add(nameNormalizer.apply(name.trim()));
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
            logger.info("Loaded " + loaded.size() + " premium username(s) from cache");
        } catch (NoSuchFileException ignored) {
            // No cache yet — first startup
        } catch (IOException e) {
            logger.warning("Failed to load premium names cache: " + e.getMessage());
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
                logger.warning("Failed to save premium names cache: " + e.getMessage());
            }
        });
    }
}
