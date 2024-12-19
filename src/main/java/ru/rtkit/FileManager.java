package ru.rtkit;

import lombok.extern.slf4j.Slf4j;
import ru.rtkit.exception.NotFoundException;
import ru.rtkit.exception.NotModifiedException;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class FileManager {

    private static final String RESOURCES_STR_PATH = "src/main/resources";
    public static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z").localizedBy(Locale.US);

    private final String rootDir;
    private final long maxTotalContentLength;

    private final Map<String, CachedFile> cache = new HashMap<>();
    private final PriorityQueue<CachedFile> minHeap = new PriorityQueue<>(Comparator.comparingInt(CachedFile::getFrequency));

    private long currentTotalContentLength;

    public FileManager(String rootDir, long maxTotalContentLength) {
        this.rootDir = rootDir;
        this.maxTotalContentLength = maxTotalContentLength;
    }

    public CachedFile get(String strPath, String etag, String modifiedSince) throws NotFoundException, NotModifiedException {
        CachedFile cachedFile;
        if (cache.containsKey(strPath)) {
            cachedFile = cache.get(strPath);
            increment(cachedFile);
            log.debug("{} returned from cache", strPath);
        } else {
            cachedFile = loadFile(strPath);
            insert(cachedFile);
            log.debug("{} returned from disk", strPath);
        }
        if (etag != null) {
            log.debug("Having etag option: {}", etag);
            if (!cachedFile.getEtag().equals(etag)) {
                log.debug("{File tag: {} not equals passed etag: {}", cachedFile.getEtag(), etag);
                return cachedFile;
            } else {
                log.debug("Etags dont match: {}", etag);
                throw new NotModifiedException("File etag matching passed etag");
            }
        } else if (modifiedSince != null) {
            log.debug("Having modifiedSince option: {}", modifiedSince);
            ZonedDateTime mo = ZonedDateTime.parse(modifiedSince, DATE_TIME_FORMATTER);
            ZonedDateTime fileTime = ZonedDateTime.parse(cachedFile.getLastModified(), DATE_TIME_FORMATTER);
            if (fileTime.isAfter(mo)) {
                log.debug("Success: File modified since passed date, LastModified: {}", cachedFile.getLastModified());
                return cachedFile;
            } else {
                log.debug("Fail: File not modified since passed date, LastModified: {}", cachedFile.getLastModified());
                throw new NotModifiedException("Not modified since passed date");
            }
        } else {
            return cachedFile;
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

    private CachedFile loadFile(String strPath) throws NotFoundException {
        Path path = Path.of(RESOURCES_STR_PATH, rootDir, strPath);
        if (!Files.exists(path)) {
            throw new NotFoundException(String.format("File %s does not exist", strPath));
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            String contentType = Files.probeContentType(path);

            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            String checksum = new BigInteger(1, hash).toString(16);
            String etag = "\"" + checksum.substring(0, 16) + "\"";

            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            long contentLength = attrs.size();
            String lastModified = attrs.lastModifiedTime().toInstant().atZone(ZoneId.of("GMT")).format(DATE_TIME_FORMATTER);

            return new CachedFile(strPath, bytes, contentType, lastModified, contentLength, etag);
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
