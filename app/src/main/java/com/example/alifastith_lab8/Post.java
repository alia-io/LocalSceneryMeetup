package com.example.alifastith_lab8;

import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class Post {

    public String uid;
    public String url;
    public String description;
    public String latitude;
    public String longitude;
    public Object timestamp;
    public Object lastEditTimestamp = null;
    public int likesCount = 0;
    public Map<String, Boolean> likes = new HashMap<>();

    public Post() { }

    public Post(String uid, String url, String description, String latitude, String longitude) {
        this.uid = uid;
        this.url = url;
        this.description = description;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = ServerValue.TIMESTAMP;
    }

    public void updatePost(String url, String description, String latitude, String longitude) {

        if (url != null && !this.url.equals(url)) {
            this.url = url;
            this.latitude = latitude;
            this.longitude = longitude;
            this.lastEditTimestamp = ServerValue.TIMESTAMP;
        }

        if (description != null && !this.description.equals(description)) {
            this.description = description;
            this.lastEditTimestamp = ServerValue.TIMESTAMP;
        }
    }
}
