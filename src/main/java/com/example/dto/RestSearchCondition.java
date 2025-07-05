package com.example.dto;

public class RestSearchCondition {
    private String userId;
    private String title;
    private String author;
    private String completeDateFrom;
    private String completeDateTo;
    private int genre;
    private int rate;

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getAuthor() {
        return author;
    }
    public void setAuthor(String author) {
        this.author = author;
    }
    public String getCompleteDateFrom() {
        return completeDateFrom;
    }
    public void setCompleteDateFrom(String completeDateFrom) {
        this.completeDateFrom = completeDateFrom;
    }
    public String getCompleteDateTo() {
        return completeDateTo;
    }
    public void setCompleteDateTo(String completeDateTo) {
        this.completeDateTo = completeDateTo;
    }
    public int getGenre() {
        return genre;
    }
    public void setGenre(int genre) {
        this.genre = genre;
    }
    public int getRate() {
        return rate;
    }
    public void setRate(int rate) {
        this.rate = rate;
    }
}
