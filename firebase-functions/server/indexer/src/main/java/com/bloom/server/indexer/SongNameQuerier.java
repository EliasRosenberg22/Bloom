package com.bloom.server.indexer;

import java.net.HttpURLConnection;
import java.util.List;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;


//to deploy (from 'indexer' directory): gcloud functions deploy songnamequerier --trigger-http --entry-point com.bloom.server.indexer.SongNameQuerier --runtime java11 --allow-unauthenticated
//to call from terminal: curl -X POST https://us-central1-bloom-838b5.cloudfunctions.net/songnamequerier -H "Content-Type:application/json" -d "{\"query\": \"a\"}"


/**
 * @author Addison Wessel
 * SongNameQuerier is a cloud function that returns id, name, and 
 * album cover URL of all songs whose names start with the query term. 
 * 
 * It expects a POST with Content-Type:application/json, and json passed in the format {'query': <query term>}. 
 * 
 * TODO: factor in the current tree's genre tags to get more relevant results
 */
public class SongNameQuerier implements HttpFunction {
    private static final Gson gson = new Gson();
    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {

        response.appendHeader("Access-Control-Allow-Origin", "*");
        if("OPTIONS".equals(request.getMethod())) {
            response.appendHeader("Access-Control-Allow-Methods", "POST");
            response.appendHeader("Access-Control-Allow-Headers", "Content-Type");
            response.appendHeader("Access-Control-Max-Age", "3600");
            response.setStatusCode(HttpURLConnection.HTTP_NO_CONTENT);
            return;
        }
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).setProjectId("bloom-838b5").build();
        if(FirebaseApp.getApps().isEmpty())
            FirebaseApp.initializeApp(options);
        Firestore db = FirestoreClient.getFirestore();

        if(request.getContentType().orElse("").equals("application/json")) {
            JsonObject requestJson = gson.fromJson(request.getReader(), JsonElement.class).getAsJsonObject();
            System.out.println("gotten data: " + requestJson.toString());
            String queryStringSanitized = "";
            try {
                queryStringSanitized = requestJson.get("query").getAsString().toLowerCase();
            } catch (NullPointerException npe) {
                response.setStatusCode(HttpURLConnection.HTTP_BAD_REQUEST);
                response.getWriter().write("Couldn't find 'query' field in JSON object.");
                return;
            }

            ApiFuture<QuerySnapshot> results = db.collection("songs")
            .whereArrayContains("indexing", queryStringSanitized).limit(10).get();
            List<QueryDocumentSnapshot> documents = results.get().getDocuments();
            JsonArray resultsArray = new JsonArray();
            for (DocumentSnapshot doc : documents) {
                JsonObject songJson = new JsonObject();
                songJson.add("name", new JsonPrimitive(doc.getString("name")));
                songJson.add("id", new JsonPrimitive(doc.getString("id")));
                songJson.add("album_cover", new JsonPrimitive(doc.getString("album_cover")));
                resultsArray.add(songJson);
            }
            JsonObject responseJson = new JsonObject();
            responseJson.add("results", resultsArray);
            responseJson.add("has_results", new JsonPrimitive(resultsArray.size() > 0 ? true : false));
            response.setContentType("application/json");
            response.getWriter().write(responseJson.toString());
        } else {
            response.setStatusCode(HttpURLConnection.HTTP_UNSUPPORTED_TYPE);
            return;
        }
    }
}
