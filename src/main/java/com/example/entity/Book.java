package com.example.entity;

public class Book {
    // ユーザID
    private String userId;
    // 連番
    private String seqNo;
    // タイトル
    private String title;
    // 著者
    private String author;
    // 値段
    private String price;
    // 出版社
    private String publisher;
    // 出版日yyyy-MM-dd
    private String published;
    // 購入日yyyy-MM-dd
    private String buyDate;
    // 読了日yyyy-MM-dd
    private String completeDate;
    // ジャンル
    private String genre;
    // 感想
    private String memo;
    // 評価
    private String rate;
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
    public String getSeqNo() {
        return seqNo;
    }
    public void setSeqNo(String seqNo) {
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
    public String getPrice() {
        return price;
    }
    public void setPrice(String price) {
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
    public String getBuyDate() {
        return buyDate;
    }
    public void setBuyDate(String buyDate) {
        this.buyDate = buyDate;
    }
    public String getCompleteDate() {
        return completeDate;
    }
    public void setCompleteDate(String completeDate) {
        this.completeDate = completeDate;
    }
    public String getGenre() {
        return genre;
    }
    public void setGenre(String genre) {
        this.genre = genre;
    }
    public String getMemo() {
        return memo;
    }
    public void setMemo(String memo) {
        this.memo = memo;
    }
    public String getRate() {
        return rate;
    }
    public void setRate(String rate) {
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
