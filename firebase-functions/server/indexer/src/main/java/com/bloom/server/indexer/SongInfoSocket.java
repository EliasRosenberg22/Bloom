package com.bloom.server.indexer;

import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


//to deploy (from 'indexer' directory): gcloud functions deploy songinforeciever --trigger-http --entry-point com.bloom.server.indexer.SongInfoSocket --runtime java11 --allow-unauthenticated

/**
 * @author Addison Wessel
 * 
 * Reciever for Spotify song data being placed on the Database. 
 * It gets a song JSON from Elias's crawler, and then converts it to a format for Firestore. 
 * Then it places it on Firestore with the proper credentials. 
 */
public class SongInfoSocket implements HttpFunction {

    private static final Gson gson = new Gson();
    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {

        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).setProjectId("bloom-838b5").build();
        if(FirebaseApp.getApps().isEmpty())
            FirebaseApp.initializeApp(options);
        Firestore db = FirestoreClient.getFirestore();

        String contentType = request.getContentType().orElse("");
        if(contentType.equals("application/json")) {
            
            JsonElement requestParsed = gson.fromJson(request.getReader(), JsonElement.class);
            JsonObject requestJson = requestParsed.getAsJsonObject();
            PrintWriter writer = new PrintWriter((response.getWriter()));
            writer.printf("ID: %s\n", requestJson.get("id").getAsString());
            
            String id = requestJson.get("id").getAsString();
            String name = requestJson.get("name").getAsString();
            // if(name.length() > 99) {
            //     response.getWriter().write("Bloom cannot handle song titles longer than 99 words currently.\n");
            //     response.setStatusCode(HttpsURLConnection.HTTP_ENTITY_TOO_LARGE);
            //     return;
            // }
            String sanitizedTrackName = name.toLowerCase();

            Map<String, Object> docData = new HashMap<String, Object>();
            helperDouble(docData, "duration_ms", requestJson);
            docData.put("href", requestJson.get("href").getAsString());
            docData.put("id", requestJson.get("id").getAsString());
            docData.put("name", requestJson.get("name").getAsString());
            docData.put("uri", requestJson.get("uri").getAsString());
            docData.put("album_cover", requestJson.get("album").getAsJsonObject().get("images").getAsJsonArray().get(1).getAsJsonObject().get("url").getAsString());
            ArrayList<String> genres = new ArrayList<>();
            for(JsonElement genre : requestJson.get("genres").getAsJsonArray()) {
                genres.add(genre.getAsString());
            }


            docData.put("genres", genres);
            docData.put("artist", requestJson.get("artists").getAsJsonArray().get(0).getAsJsonObject().get("name").getAsString());
            docData.put("album", requestJson.get("album").getAsJsonObject().get("name").getAsString());  
            helperDouble(docData, "popularity", requestJson);
            docData.put("explicit", requestJson.get("explicit").getAsBoolean());
            helperDouble(docData, "danceability", requestJson);
            helperDouble(docData, "energy", requestJson);
            helperDouble(docData, "key", requestJson);
            helperDouble(docData, "loudness", requestJson);
            helperDouble(docData, "mode", requestJson);
            helperDouble(docData, "speechiness", requestJson);
            helperDouble(docData, "acousticness", requestJson);
            helperDouble(docData, "instrumentalness", requestJson);
            helperDouble(docData, "liveness", requestJson);
            helperDouble(docData, "valence", requestJson);
            helperDouble(docData, "tempo", requestJson);
            helperDouble(docData, "time_signature", requestJson);
            ArrayList<Object> prefixes = new ArrayList<>();
            for(int i = 1; i <= sanitizedTrackName.length(); i++) {
                prefixes.add(sanitizedTrackName.substring(0, i));
            }  
            docData.put("indexing", prefixes);
            ApiFuture<WriteResult> future = db.collection("songs").document(id).set(docData);
            response.getWriter().write("got in " + future.get().getUpdateTime() + "ms\n");

        } else {
            response.setStatusCode(HttpURLConnection.HTTP_UNSUPPORTED_TYPE);
        }

    }

    private void helperDouble(Map<String, Object> docData, String paramName, JsonObject requestJson) {
        docData.put(paramName, requestJson.get(paramName).getAsDouble());
    }
}
