package com.av19.netanalyzer.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OpenRouterApiClient {

    private static final String TAG = "OpenRouterApiClient";
    private static final String BASE_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String MODEL = "@preset/upnp-ssdp-device-parser";
    private static final String PREFS_NAME = "MiAppPrefs";
    private static final String KEY_API_TOKEN = "open_router_api_key";

    private static Context appContext;
    private static OpenRouterApiClient instance = null;

    private OpenRouterApiClient(Context context) {
        init(context);
    }

    public static OpenRouterApiClient getInstance(Context context) {
        if (instance == null) {
            instance = new OpenRouterApiClient(context);
        }
        return instance;
    }

    private static void init(Context context) {
        appContext = context.getApplicationContext();
        Log.d(TAG, "OpenRouterApiClient initialized");
    }

    private static String getToken() {
        if (appContext == null) {
            throw new IllegalStateException("OpenRouterApiClient no inicializado. Llama a init() primero.");
        }
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_API_TOKEN, "");
        Log.d(TAG, "Token retrieved: " + (token.isEmpty() ? "empty" : "exists (length " + token.length() + ")"));
        return token;
    }

    public static JSONObject parseDeviceInfoSync(String rawDeviceInfo) throws JSONException {
        Log.d(TAG, rawDeviceInfo);
        
        String token = getToken();
        if (token.isEmpty()) {
            Log.e(TAG, "Error: No se encontró el API token en SharedPreferences");
            return null;
        }

        OkHttpClient client = new OkHttpClient();

        JSONObject requestBody = new JSONObject();
        requestBody.put("model", MODEL);

        JSONArray messages = new JSONArray();
        JSONObject userMessage = new JSONObject();
        userMessage.put("role", "user");
        userMessage.put("content", rawDeviceInfo);
        messages.put(userMessage);
        requestBody.put("messages", messages);

        Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody.toString(), MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            Log.d(TAG, "Response code: " + response.code());
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                Log.d(TAG, "Raw response body:\n" + responseBody);

                JSONObject jsonResponse = new JSONObject(responseBody);
                JSONArray choices = jsonResponse.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject message = choices.getJSONObject(0).getJSONObject("message");

                    // 1. Intentar obtener content normal
                    String content = message.optString("content");
                    if (!content.isEmpty() && !content.equals("null")) {
                        Log.d(TAG, "Content found, length: " + content.length());
                        return extractJsonFromText(content);
                    }
                }
            } else {
                Log.e(TAG, "HTTP error " + response.code() + ": " + response.body().string());
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception during API call", e);
        }
        return null;
    }

    private static JSONObject extractJsonFromText(String text) throws JSONException {
        if (text == null || text.isEmpty()) {
            throw new JSONException("Empty text");
        }
        // Buscar el primer '{' y el último '}' que cierre un objeto JSON válido
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start == -1 || end == -1 || end <= start) {
            throw new JSONException("No JSON object found in text");
        }
        String jsonCandidate = text.substring(start, end + 1);
        Log.d(TAG, "Extracted JSON candidate: " + jsonCandidate);
        return new JSONObject(jsonCandidate);
    }

    public static boolean hasToken() {
        if (appContext == null) {
            Log.w(TAG, "hasToken called but appContext is null");
            return false;
        }
        SharedPreferences prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean has = !prefs.getString(KEY_API_TOKEN, "").isEmpty();
        Log.d(TAG, "hasToken: " + has);
        return has;
    }
}