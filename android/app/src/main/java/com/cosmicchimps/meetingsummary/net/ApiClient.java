package com.cosmicchimps.meetingsummary.net;

import android.content.Context;

import com.cosmicchimps.meetingsummary.Prefs;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Construye Retrofit/OkHttp con la URL y el token actuales de Prefs. */
public final class ApiClient {

    public static OkHttpClient okHttp(String token) {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);
        return new OkHttpClient.Builder()
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("Authorization", "Bearer " + token)
                                .build()))
                .addInterceptor(log)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)   // subidas grandes
                .build();
    }

    public static Api api(Context ctx) {
        Prefs prefs = new Prefs(ctx);
        return new Retrofit.Builder()
                .baseUrl(prefs.getBaseUrl())
                .client(okHttp(prefs.getToken()))
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(Api.class);
    }
}
