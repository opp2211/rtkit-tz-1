package ru.rtkit;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CachedFile {
    private int frequency;
    private String strPath;
    private String content;
    private String contentType;
    private String lastModified;
    private long contentLength;

    public CachedFile(String strPath, String content, String contentType, String lastModified, long contentLength) {
        this.frequency = 1;
        this.strPath = strPath;
        this.content = content;
        this.contentType = contentType;
        this.lastModified = lastModified;
        this.contentLength = contentLength;
    }

    public void incrementFrequency() {
        frequency++;
    }
}
