package ru.rtkit;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CachedFile {
    private int frequency;
    private String strPath;
    private byte[] content;
    private String contentType;
    private String lastModified;
    private long contentLength;
    private String etag;

    public CachedFile(String strPath, byte[] content, String contentType, String lastModified, long contentLength, String etag) {
        this.frequency = 1;
        this.strPath = strPath;
        this.content = content;
        this.contentType = contentType;
        this.lastModified = lastModified;
        this.contentLength = contentLength;
        this.etag = etag;
    }

    public void incrementFrequency() {
        frequency++;
    }
}
