package com.example.alifastith_lab8;

import android.util.Log;

public class MessageModel {

    public String messageKey;
    public String sender;
    public String receiver;
    public String message;
    public String date;
    public String time;

    public MessageModel(String messageKey, String sender, String receiver, String message, String timestamp) {
        this.messageKey = messageKey;
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;

        int index = timestamp.lastIndexOf(" ");
        this.date = timestamp.substring(0, index);
        this.time = timestamp.substring(index + 1);
        Log.d("Date", "date = " + date);
    }
}
