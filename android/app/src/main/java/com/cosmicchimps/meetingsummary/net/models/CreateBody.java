package com.cosmicchimps.meetingsummary.net.models;

/** Cuerpos JSON simples para crear proyecto (name) o nota (title). */
public class CreateBody {
    public String name;
    public String title;

    public static CreateBody project(String name) {
        CreateBody b = new CreateBody();
        b.name = name;
        return b;
    }

    public static CreateBody note(String title) {
        CreateBody b = new CreateBody();
        b.title = title;
        return b;
    }
}
