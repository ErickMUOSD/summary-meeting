package com.cosmicchimps.meetingsummary.net;

import com.cosmicchimps.meetingsummary.net.models.CreateBody;
import com.cosmicchimps.meetingsummary.net.models.Job;
import com.cosmicchimps.meetingsummary.net.models.Note;
import com.cosmicchimps.meetingsummary.net.models.Project;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface Api {

    @GET("projects")
    Call<List<Project>> listProjects();

    @POST("projects")
    Call<Project> createProject(@Body CreateBody body);

    @GET("projects/{id}/notes")
    Call<List<Note>> listNotes(@Path("id") String projectId);

    @POST("projects/{id}/notes")
    Call<Note> createNote(@Path("id") String projectId, @Body CreateBody body);

    @GET("notes/{id}")
    Call<Note> getNote(@Path("id") String noteId);

    @POST("notes/{id}/transcribe")
    Call<Job> transcribe(@Path("id") String noteId);

    @POST("notes/{id}/summarize")
    Call<Job> summarize(@Path("id") String noteId);

    @GET("jobs/{id}")
    Call<Job> getJob(@Path("id") String jobId);
}
