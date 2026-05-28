package com.moodcopilot.entity;

public class MusicMeta {
    private String title;
    private String artist;
    private String coverUrl;
    private String userLyric;
    private String songUrl;
    private String moodTags;
    private String themeSummary;

    public MusicMeta() {}

    public MusicMeta(String title, String artist, String coverUrl, String userLyric) {
        this.title = title;
        this.artist = artist;
        this.coverUrl = coverUrl;
        this.userLyric = userLyric;
    }

    public MusicMeta(String title, String artist, String coverUrl, String userLyric, String songUrl, String moodTags, String themeSummary) {
        this.title = title;
        this.artist = artist;
        this.coverUrl = coverUrl;
        this.userLyric = userLyric;
        this.songUrl = songUrl;
        this.moodTags = moodTags;
        this.themeSummary = themeSummary;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getArtist() { return artist; }
    public void setArtist(String artist) { this.artist = artist; }

    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getUserLyric() { return userLyric; }
    public void setUserLyric(String userLyric) { this.userLyric = userLyric; }

    public String getSongUrl() { return songUrl; }
    public void setSongUrl(String songUrl) { this.songUrl = songUrl; }

    public String getMoodTags() { return moodTags; }
    public void setMoodTags(String moodTags) { this.moodTags = moodTags; }

    public String getThemeSummary() { return themeSummary; }
    public void setThemeSummary(String themeSummary) { this.themeSummary = themeSummary; }
}
