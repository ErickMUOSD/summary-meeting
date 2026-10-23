package com.cosmicchimps.meetingsummary.net.models;

import java.util.List;

public class Note {
    public String id;
    public String project_id;
    public String title;
    public String status;              // created|uploaded|transcribed|summarized
    public String combined_txt_path;
    public String created_at;
    public String updated_at;

    // Presentes solo en el detalle (GET /notes/{id})
    public List<Recording> recordings;
    public Boolean has_summary;
    public Boolean has_deliverables;

    public boolean isTranscribed() {
        return "transcribed".equals(status) || "summarized".equals(status);
    }

    public boolean isSummarized() {
        return "summarized".equals(status);
    }
}
