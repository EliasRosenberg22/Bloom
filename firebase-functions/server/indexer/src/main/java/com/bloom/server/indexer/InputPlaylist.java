package com.bloom.server.indexer;

import java.net.HttpURLConnection;
import java.util.List;
import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
// import com.google.gson.Gson;

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
public class InputPlaylist implements HttpFunction {
    // private static final Gson gson = new Gson();
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
            deleteCollection(db.collection("users/Ihoc1nuTr9lL92TngABS/trees/2q5uA3rO1YnSd7pYXLUK/input-playlist"), 10);
            response.getWriter().write("success!");
        } else {
            response.setStatusCode(HttpURLConnection.HTTP_UNSUPPORTED_TYPE);
            return;
        }
    }

    void deleteCollection(CollectionReference collection, int batchSize) {
        try {
            ApiFuture<QuerySnapshot> future = collection.limit(batchSize).get();
            int deleted = 0;
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            for (QueryDocumentSnapshot document : documents) {
                document.getReference().delete();
                ++deleted;
            }
            if(deleted >= batchSize) {
                deleteCollection(collection, batchSize);
            }
        } catch(Exception e) {
            System.out.println("Error deleting collection: " + e.getMessage());
        }
    }
}
