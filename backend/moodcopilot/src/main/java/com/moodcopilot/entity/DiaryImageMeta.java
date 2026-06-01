package com.moodcopilot.entity;

public class DiaryImageMeta {
    private String url;
    private String channel;
    private Integer origWidth;
    private Integer origHeight;
    private Integer compressedWidth;
    private Integer compressedHeight;
    private Long origSize;
    private Long compressedSize;
    private Double quality;
    private String mime;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public Integer getOrigWidth() {
        return origWidth;
    }

    public void setOrigWidth(Integer origWidth) {
        this.origWidth = origWidth;
    }

    public Integer getOrigHeight() {
        return origHeight;
    }

    public void setOrigHeight(Integer origHeight) {
        this.origHeight = origHeight;
    }

    public Integer getCompressedWidth() {
        return compressedWidth;
    }

    public void setCompressedWidth(Integer compressedWidth) {
        this.compressedWidth = compressedWidth;
    }

    public Integer getCompressedHeight() {
        return compressedHeight;
    }

    public void setCompressedHeight(Integer compressedHeight) {
        this.compressedHeight = compressedHeight;
    }

    public Long getOrigSize() {
        return origSize;
    }

    public void setOrigSize(Long origSize) {
        this.origSize = origSize;
    }

    public Long getCompressedSize() {
        return compressedSize;
    }

    public void setCompressedSize(Long compressedSize) {
        this.compressedSize = compressedSize;
    }

    public Double getQuality() {
        return quality;
    }

    public void setQuality(Double quality) {
        this.quality = quality;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }
}
