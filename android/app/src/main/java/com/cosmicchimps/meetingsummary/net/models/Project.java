package com.cosmicchimps.meetingsummary.net.models;

public class Project {
    public String id;
    public String name;
    public String created_at;

    @Override
    public String toString() {
        return name != null ? name : id;
    }
}
