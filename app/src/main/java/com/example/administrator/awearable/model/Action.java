package com.example.administrator.awearable.model;

/**
 * Ckass used as the structure of the database table that will store information related to Actions
 * such the moments the user glanced at the display
 */

public class Action {
    private long id;
    private String action;
    private Long time;
    private Long set;
    private Long accessOtherApps;


    public long getId() {
        return id;
    }
    public void setId(long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }
    public void setAction(String action) {
        this.action = action;
    }

    public Long getTime() {
        return time;
    }
    public void setTime(Long time) {
        this.time = time;
    }

    public Long getSet() {
        return set;
    }
    public void setSet(Long set) {
        this.set = set;
    }

    public Long getAccessOtherApps() {
        return accessOtherApps;
    }
    public void setAccessOtherApps(Long accessOtherApps) {
        this.accessOtherApps = accessOtherApps;
    }
}
