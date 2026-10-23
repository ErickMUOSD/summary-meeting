package com.cosmicchimps.meetingsummary.net.models;

public class Job {
    public String id;
    public String note_id;
    public String type;          // transcribe|summarize
    public String status;        // pending|running|done|error
    public int progress_pct;
    public String stage;
    public String error;

    public boolean isFinished() {
        return "done".equals(status) || "error".equals(status);
    }
}
