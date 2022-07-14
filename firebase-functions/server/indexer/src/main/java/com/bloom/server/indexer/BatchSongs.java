package com.bloom.server.indexer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.google.api.core.ApiFuture;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

/**
 * @author Addison Wessel
 * Bloom: song batcher
 * 
 * This class / function grabs all songs in the "songs" database and places them in documents of 1000 songs. 
 * This allows the algorithm to do 1000 times fewer calls to the database. 
 */
//to deploy: gcloud functions deploy song_batcher --trigger-http --entry-point com.bloom.server.indexer.BatchSongs --runtime java11 --allow-unauthenticated
public class BatchSongs implements HttpFunction {

    @Override
    public void service(HttpRequest request, HttpResponse response) throws Exception {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).setProjectId("bloom-838b5").build();
        if(FirebaseApp.getApps().isEmpty())
            FirebaseApp.initializeApp(options);
        Firestore db = FirestoreClient.getFirestore();
        CollectionReference songs = db.collection("songs");
        Query currPage = songs.orderBy("duration_ms").limit(1000);
        ApiFuture<QuerySnapshot> future = currPage.get();
        List<QueryDocumentSnapshot> docs = future.get(30, TimeUnit.SECONDS).getDocuments();
        int i = 0;
        while(docs.size() > 0) {
            Map<String, Object> docData = new HashMap<String, Object>();
            ArrayList<Object> songsInBatch = new ArrayList<>();
            for(QueryDocumentSnapshot qds : docs) {
                songsInBatch.add(qds.getData());
            }
            docData.put("songs", songsInBatch);

            ApiFuture<WriteResult> write = db.collection("songs-batched").document(Integer.toString(i++)).set(docData);
            response.getWriter().write("writing batch " + (i - 1) + " took " + write.get().getUpdateTime() + "ms\n");
            QueryDocumentSnapshot lastDoc = docs.get(docs.size() - 1);
            Query nextPage = songs.orderBy("duration_ms").startAfter(lastDoc).limit(1000);
            ApiFuture<QuerySnapshot> next = nextPage.get();
            docs = next.get(30, TimeUnit.SECONDS).getDocuments();
        }
    }
    
}
