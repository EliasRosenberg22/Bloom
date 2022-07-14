package com.bloom.server.indexer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.HttpURLConnection;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.cloud.FirestoreClient;
import com.google.cloud.functions.HttpFunction;
import com.google.cloud.functions.HttpRequest;
import com.google.cloud.functions.HttpResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;


//to deploy: gcloud functions deploy treeFunctions --trigger-http --entry-point com.bloom.server.indexer.TreeFunctions --runtime java11 --allow-unauthenticated

/**
 * @author Addison Wessel
 * @version 6.0
 * 
 * Expects JSON attached to request in the following form:
 * {
 *  "tree": <the tree>,
 *  "operation": <the operation you want to perform>,
 *  "node_id": <node on which to perform operation>
 *  "attribute": <[only needs to be set if using 'ATTRIBUTE' operation]>,
 *  "name": <full path to firestore tree object>
 * }
 */
public class TreeFunctions implements HttpFunction {

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
        
        if(request.getContentType().orElse("").equals("application/json")) {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();
            FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).setProjectId("bloom-838b5").build();
            if(FirebaseApp.getApps().isEmpty())
                FirebaseApp.initializeApp(options);
            Firestore db = FirestoreClient.getFirestore();
            JsonObject requestJson = gson.fromJson(request.getReader(), JsonElement.class).getAsJsonObject();
            JsonObject tree = requestJson.get("tree").getAsJsonObject();
            String operation = requestJson.get("operation").getAsString();
            String nodeID = requestJson.get("node_id").getAsString();
            String attribute = requestJson.get("attribute").getAsString();
            JsonElement treeNameJson = requestJson.get("name");
            String treeName = "users/Ihoc1nuTr9lL92TngABS/trees/2q5uA3rO1YnSd7pYXLUK";
            if(!treeNameJson.isJsonNull())
                treeName = treeNameJson.getAsString();
            switch(operation) {
                case "SHOW":
                    showChildren(tree, nodeID);
                    break;
                case "ATTRIBUTE":
                    changeAttribute(tree, nodeID, attribute);
                    break;
                case "GENERATE_CHILDREN":
                    generateChildren(tree);
                    break;
                case "DELETE":
                    deleteNode(tree, nodeID);
                    break;
                default:
                    break;
            }
            DocumentReference treeRef = db.document(treeName);
            treeRef.update("tree_json", tree.toString());
            response.getWriter().write(tree.toString());
        } else {
            response.setStatusCode(HttpURLConnection.HTTP_UNSUPPORTED_TYPE);
            return;
        }
    }
    

    public static void main(String[] args) {
        try {
            JsonObject tree = gson.fromJson(new FileReader(new File("").getAbsolutePath() + "/firebase-functions/server/indexer/src/main/java/com/server/indexer/test_json/tree.json"), JsonElement.class).getAsJsonObject();
            showChildren(tree, "JK9989");
            changeAttribute(tree, "TR10089", "valence");
            System.out.println(tree.toString());
            System.out.println("=================ADDING CHILDREN===============");
            generateChildren(tree);
            System.out.println(tree.toString());
            System.out.println("=================AFTER DELETION================");
            deleteNode(tree, "TR9989");
            System.out.println(tree.toString());
        } catch (JsonSyntaxException e) {
            e.printStackTrace();
        } catch (JsonIOException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }


    /**
     * Makes children of parentID visibile. 
     * @param tree tree to edit visibility on
     * @param parentID parent ID to change child visibility of
     */
    private static void showChildren(JsonObject tree, String parentID) {
        JsonObject nodeRef = findNode(tree, parentID);
        if(nodeRef == null) return;
        if(!nodeRef.get("left").isJsonNull())
            nodeRef.get("left").getAsJsonObject().get("root").getAsJsonObject().addProperty("visibility", true);
        if(!nodeRef.get("right").isJsonNull())
            nodeRef.get("right").getAsJsonObject().get("root").getAsJsonObject().addProperty("visibility", true);
    }

    private static void changeAttribute(JsonObject tree, String songID, String attribute) {
        JsonObject nodeRef = findNode(tree, songID);
        if(nodeRef == null) return;
        nodeRef.get("root").getAsJsonObject().addProperty("attr", attribute);

    }

    /**
     * adds children to all nodes with rec=0 and null left/right children
     * @param tree
     */
    private static void generateChildren(JsonObject tree) {
        //traverse tree to find boyz with rec = 0, if they don't have childs then `addchildrentonode` them
        if(tree.isJsonNull()) return;
        JsonElement rec = tree.get("root").getAsJsonObject().get("rec");
        if(rec.isJsonNull()) return;
        if(rec.getAsInt() != 0) return;
        if(tree.get("left").isJsonNull() && tree.get("right").isJsonNull()) {
            addChildrenToNode(tree);
        }
        if(!tree.get("left").isJsonNull()) {
            generateChildren(tree.get("left").getAsJsonObject());
        }
        if(!tree.get("right").isJsonNull()) {
            generateChildren(tree.get("right").getAsJsonObject());
        }
    }
    private static void addChildrenToNode(JsonObject node) {
        JsonObject leftChild = new JsonObject();
        leftChild.add("left", JsonNull.INSTANCE);
        leftChild.add("right", JsonNull.INSTANCE);
        //create empty left child root
        JsonObject leftChildRoot = new JsonObject();
        leftChildRoot.add("name", JsonNull.INSTANCE);
        leftChildRoot.add("songID", JsonNull.INSTANCE);
        leftChildRoot.add("album_cover", JsonNull.INSTANCE);
        leftChildRoot.addProperty("rec", 1);
        leftChildRoot.add("attr", JsonNull.INSTANCE);
        leftChildRoot.addProperty("visibility", false);
        leftChild.add("root", leftChildRoot);
        //create leftchild
        JsonObject rightChild = new JsonObject();
        rightChild.add("left", JsonNull.INSTANCE);
        rightChild.add("right", JsonNull.INSTANCE);
        //create empty left child root
        JsonObject rightChildRoot = new JsonObject();
        rightChildRoot.add("name", JsonNull.INSTANCE);
        rightChildRoot.add("songID", JsonNull.INSTANCE);
        rightChildRoot.add("album_cover", JsonNull.INSTANCE);
        rightChildRoot.addProperty("rec", 2);
        rightChildRoot.add("attr", JsonNull.INSTANCE);
        rightChildRoot.addProperty("visibility", false);
        rightChild.add("root", rightChildRoot);

        node.add("left", leftChild);
        node.add("right", rightChild);
    }
    private static void deleteNode(JsonObject tree, String songID) {
        String nodePath = findNodePath(tree, songID, "");
        if(nodePath == null) return;
        String[] path = nodePath.split("/");
        JsonObject treeRef = tree;
        for(int i = 0; i < path.length - 1; i++) {
            treeRef = treeRef.get(path[i]).getAsJsonObject();
        }
        treeRef.add(path[path.length-1], JsonNull.INSTANCE);
    }

    /**
     * Finds node with given songID in tree
     * @param curr current tree to search
     * @param songID songID to find
     * @return reference to node with id = songID
     */
    private static JsonObject findNode(JsonObject curr, String songID) {
        if(curr.isJsonNull()) {
            return null;
        }
        if(curr.getAsJsonObject("root").get("songID").getAsString().equals(songID)) {
            return curr;
        } else {
            if(!curr.get("left").isJsonNull()) {
                JsonObject left = findNode(curr.getAsJsonObject("left"), songID);
                if(left != null) {
                    return left;
                }
            }
            if(!curr.get("right").isJsonNull()) {
                JsonObject right = findNode(curr.getAsJsonObject("right"), songID);
                return right;
            }
        }
        return null;
    }

    /**
     * Finds path to node with given songID in tree
     * @param curr current tree to search
     * @param songID songID to find
     * @return /-separated path to node with id = songID
     */
    private static String findNodePath(JsonObject curr, String songID, String currPath) {
        if(curr.isJsonNull()) {
            return null;
        }
        if(curr.getAsJsonObject("root").get("songID").getAsString().equals(songID)) {
            return currPath.substring(1);
        } else {
            if(!curr.get("left").isJsonNull()) {
                String left = findNodePath(curr.getAsJsonObject("left"), songID, currPath + "/left");
                if(left != null) {
                    return left;
                }
            }
            if(!curr.get("right").isJsonNull()) {
                String right = findNodePath(curr.getAsJsonObject("right"), songID, currPath + "/right");
                return right;
            }
        }
        return null;
    }
}