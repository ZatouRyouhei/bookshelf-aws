package com.example.dto;

public class RestBook {
    // ユーザID
    private String userId;
    // 連番
    private int seqNo;
    // タイトル
    private String title;
    // 著者
    private String author;
    // 値段
    private int price;
    // 出版社
    private String publisher;
    // 出版日yyyy-MM-dd
    private String published;
    // 読了日yyyy-MM-dd
    private String completeDate;
    // ジャンル
    private int genre;
    // 感想
    private String memo;
    // 評価
    private int rate;
    // 画像URL
    private String imgUrl;
    // 情報ページURL
    private String infoUrl;

    public String getUserId() {
        return userId;
    }
    public void setUserId(String userId) {
        this.userId = userId;
    }
    public int getSeqNo() {
        return seqNo;
    }
    public void setSeqNo(int seqNo) {
        this.seqNo = seqNo;
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
    public int getPrice() {
        return price;
    }
    public void setPrice(int price) {
        this.price = price;
    }
    public String getPublisher() {
        return publisher;
    }
    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }
    public String getPublished() {
        return published;
    }
    public void setPublished(String published) {
        this.published = published;
    }
    public String getCompleteDate() {
        return completeDate;
    }
    public void setCompleteDate(String completeDate) {
        this.completeDate = completeDate;
    }
    public int getGenre() {
        return genre;
    }
    public void setGenre(int genre) {
        this.genre = genre;
    }
    public String getMemo() {
        return memo;
    }
    public void setMemo(String memo) {
        this.memo = memo;
    }
    public int getRate() {
        return rate;
    }
    public void setRate(int rate) {
        this.rate = rate;
    }
    public String getImgUrl() {
        return imgUrl;
    }
    public void setImgUrl(String imgUrl) {
        this.imgUrl = imgUrl;
    }
    public String getInfoUrl() {
        return infoUrl;
    }
    public void setInfoUrl(String infoUrl) {
        this.infoUrl = infoUrl;
    }
}
