package com.example.alifastith_lab8;

import com.google.firebase.database.ServerValue;

import java.util.HashMap;
import java.util.Map;

public class User {

    public String displayName;
    public String emailAddress;
    public String phoneNumber;
    public Object timestamp;
    public Map<String, String> chats = new HashMap<String, String>();

    public User() { }

    public User(String displayName, String emailAddress, String phoneNumber) {
        this.displayName = displayName;
        this.emailAddress = emailAddress;
        this.phoneNumber = phoneNumber;
        this.timestamp = ServerValue.TIMESTAMP;
    }

    public Object getTimestamp() { return timestamp; }
}
