package ru.rtkit;

import ru.rtkit.exception.NotFoundException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class FileManager {

    private static final String RESOURCES_STR_PATH = "src/main/resources";
    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z");

    private final String rootDir;
    private final long maxTotalContentLength;

    private final Map<String, CachedFile> cache = new HashMap<>();
    private final PriorityQueue<CachedFile> minHeap = new PriorityQueue<>(Comparator.comparingInt(CachedFile::getFrequency));

    private long currentTotalContentLength;

    public FileManager(String rootDir, long maxTotalContentLength) {
        this.rootDir = rootDir;
        this.maxTotalContentLength = maxTotalContentLength;
    }

    public CachedFile get(String strPath) {
        if (cache.containsKey(strPath)) {
            CachedFile cachedFile = cache.get(strPath);
            increment(cachedFile);
            return cachedFile;
        } else {
            CachedFile newFile = loadFile(strPath);
            insert(newFile);
            return newFile;
        }
    }

    private void increment(CachedFile cachedFile) {
        cachedFile.incrementFrequency();
        minHeap.remove(cachedFile);
        minHeap.offer(cachedFile);
    }

    private void insert(CachedFile newFile) {
        long newTotalContentLength = currentTotalContentLength + newFile.getContentLength();
        if (newTotalContentLength > maxTotalContentLength) {
            evictLFU();
        }

        cache.put(newFile.getStrPath(), newFile);
        minHeap.offer(newFile);
        currentTotalContentLength = newTotalContentLength;
    }

    private void evictLFU() {
        CachedFile lfuFile = minHeap.poll();
        cache.remove(Objects.requireNonNull(lfuFile).getStrPath());
    }

    private CachedFile loadFile(String strPath) {
        Path path = Path.of(RESOURCES_STR_PATH, rootDir, strPath);
        if (!Files.exists(path)) {
            throw new NotFoundException(String.format("File %s does not exist", strPath));
        }

        try {
            String content = Files.readString(path);
            String contentType = Files.probeContentType(path);

            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long contentLength = attrs.size();
            String lastModified = attrs.lastModifiedTime().toInstant().atZone(ZoneId.of("GMT")).format(DATE_TIME_FORMATTER);

            return new CachedFile(strPath, content, contentType, lastModified, contentLength);
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
