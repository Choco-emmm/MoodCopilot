package com.moodcopilot.entity;

public class MusicMeta {
    private String title;
    private String artist;
    private String coverUrl;
    private String userLyric;

    public MusicMeta() {}

    public MusicMeta(String title, String artist, String coverUrl, String userLyric) {
        this.title = title;
        this.artist = artist;
        this.coverUrl = coverUrl;
        this.userLyric = userLyric;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getUserLyric() { return userLyric; }
    public void setUserLyric(String userLyric) { this.userLyric = userLyric; }
}
