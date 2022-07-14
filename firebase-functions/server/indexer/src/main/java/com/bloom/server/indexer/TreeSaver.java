package com.bloom.server.indexer;

import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.Map;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;


//to deploy: gcloud functions deploy treeSaver --trigger-http --entry-point com.bloom.server.indexer.TreeSaver --runtime java11 --allow-unauthenticated

/**
 * @author Addison Wessel
 * 
 * Recieves a JSON object containig the tree, and converts it to a format saveable on Firestore. It then saves it. 
 * 
 * Expects JSON attached to request in the following form:
 * {
 *  "tree_json": <the tree>,
 *  "name": <full path to firestore tree object>
 * }
 */
public class TreeSaver implements HttpFunction {
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
            requestJson.get("name");
            JsonElement treeNameJson = requestJson.get("tree_name");
            requestJson.get("tree_json");
            String treeName = "";
            if(treeNameJson.isJsonNull())
                treeName = "users/Ihoc1nuTr9lL92TngABS/trees/2q5uA3rO1YnSd7pYXLUK";
            else
                treeName = treeNameJson.getAsString();
            DocumentReference treeRef = db.document(treeName);
            Map<String, Object> data = new HashMap<String, Object>();
            data.put("name", requestJson.get("name").getAsString());
            data.put("tree_json", requestJson.get("tree_json").toString());
            treeRef.set(data).get();          
            response.getWriter().write("success!");
        } else {
            response.setStatusCode(HttpURLConnection.HTTP_UNSUPPORTED_TYPE);
            return;
        }
    }
    
}