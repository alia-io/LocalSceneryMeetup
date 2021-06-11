package com.example.alifastith_lab8;

import com.google.android.gms.maps.model.Marker;

public class PostModel {

    public String postKey;
    public String uid;
    public String url;
    public String description;
    public String date;
    public String update;
    public Marker marker;

    public PostModel(String postKey, String uid, String url, String description, String date, String update, Marker marker) {
        this.postKey = postKey;
        this.uid = uid;
        this.url = url;
        this.description = description;
        this.date = date;
        this.update = update;
        this.marker = marker;
    }
}
