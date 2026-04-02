package com.av19.netanalyzer.utils;

import org.json.JSONObject;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class NetApiClient {

    private static final String BASE_URL = "https://api.macvendors.com/v1/lookup/";
    private static final String TOKEN = "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzUxMiIsImp0aSI6IjA0NjVmOTEzLTk2MDEtNDQ2ZS04OTMzLTE5MjhiYTI3ZTk0NyJ9.eyJpc3MiOiJtYWN2ZW5kb3JzIiwiYXVkIjoibWFjdmVuZG9ycyIsImp0aSI6IjA0NjVmOTEzLTk2MDEtNDQ2ZS04OTMzLTE5MjhiYTI3ZTk0NyIsImlhdCI6MTc2OTQ2NDA4NywiZXhwIjoyMDgzOTYwMDg3LCJzdWIiOiIxNzA5NCIsInR5cCI6ImFjY2VzcyJ9.-uaoPaIWXnU1psYH86aC-9S8IAsK_qPjV3YSwJy8vP0VanJ4q7ZOzP09eBalOewnoR8X2hmV5Bna5-on2Af4Uw";

    public static String getMacVendorSync(String mac) {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(BASE_URL + mac)
                .addHeader("Authorization", "Bearer " + TOKEN)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JSONObject json = new JSONObject(response.body().string());
                return json.getJSONObject("data").getString("organization_name");
            }
        } catch (Exception e) {
            return "Desconocido o Error";
        }
        return "No encontrado";
    }
}