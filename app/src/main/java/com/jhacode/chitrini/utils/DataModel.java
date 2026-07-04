package com.jhacode.chitrini.utils;

import com.google.gson.annotations.SerializedName;

public class DataModel {
    @SerializedName("target")
    private String target;
    
    @SerializedName("sender")
    private String sender;
    
    @SerializedName("data")
    private String data;
    
    @SerializedName("type")
    private DataModelType type;

    public DataModel() {}

    public DataModel(String target, String sender, String data, DataModelType type) {
        this.target = target;
        this.sender = sender;
        this.data = data;
        this.type = type;
    }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getData() { return data; }
    public void setData(String data) { this.data = data; }

    public DataModelType getType() { return type; }
    public void setType(DataModelType type) { this.type = type; }
}
