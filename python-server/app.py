import pandas as pd
import logging
import numpy as np
import base64
import time
import requests
from flask import Flask, request, jsonify, url_for, render_template, flash, redirect, session, make_response, abort
from flask_cors import CORS, cross_origin
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics import pairwise_distances
import sklearn.metrics.pairwise as pw
from sklearn.preprocessing import MinMaxScaler
import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore



import functions_framework
import json
import os
import sys


app = Flask(__name__)
cors = CORS(app)
app.config['CORS_HEADERS'] = 'Content-Type'
app.logger.addHandler(logging.StreamHandler(sys.stdout))
app.logger.setLevel(logging.ERROR)
app.config.from_object('config')
#app.config.from_object('config.authConfig')

spotify_df_global = None
#simple function to create OHE features
def ohe_prep(df, column, new_name): 
    """ 
    Create One Hot Encoded features of a specific column

    Parameters: 
        df (pandas dataframe): Spotify Dataframe
        column (str): Column to be processed
        new_name (str): new column name to be used
        
    Returns: 
        tf_df: One hot encoded features 
    """
    
    tf_df = pd.get_dummies(df[column])
    feature_names = tf_df.columns
    tf_df.columns = [new_name + "|" + str(i) for i in feature_names]
    tf_df.reset_index(drop = True, inplace = True)    
    return tf_df

#function to build entire feature set
def create_feature_set(df, float_cols):
    """ 
    Process spotify df to create a final set of features that will be used to generate recommendations

    Parameters: 
        df (pandas dataframe): Spotify Dataframe
        float_cols (list(str)): List of float columns that will be scaled 
        
    Returns: 
        final: final set of features 
    """
    #tfidf genre lists
    #tfidf = TfidfVectorizer()
    #tfidf_matrix =  tfidf.fit_transform(df['genre'].apply(lambda x: "".join(x)))
    #genre_df = pd.DataFrame(tfidf_matrix.toarray())
    #genre_df.columns = ['genre' + "|" + i for i in tfidf.get_feature_names()]
    #genre_df.reset_index(drop = True, inplace=True)

    popularity_ohe = ohe_prep(df, 'popularity_red','pop') * 0.2
    mode_ohe = ohe_prep(df, 'mode','mode') * 0.6
    key_ohe = ohe_prep(df,'key','key') * 0.15
    time_ohe = ohe_prep(df,'time_signature','time') * 0.4
    explicity_ohe = ohe_prep(df,'explicit','explicit') * 1.2

    #scale float columns
    floats = df[float_cols].reset_index(drop = True)
    scaler = MinMaxScaler()
    floats_scaled = pd.DataFrame(scaler.fit_transform(floats), columns = floats.columns) * 0.35

    #concanenate all features
    final = pd.concat([floats_scaled, mode_ohe, key_ohe, time_ohe, popularity_ohe, explicity_ohe], axis = 1)
     
    #add song id
    final['id']=df['id'].values
    
    return final

def load_data_local():
    db = pd.read_csv(os.path.dirname(os.path.realpath(__file__)) + "/data.csv")
    return db.drop(columns='Unnamed: 0')

def export_db(db):
    spotify_df = load_data(db)
    spotify_df.to_csv("data4fmajor.csv")

def load_data(db):
    docs = db.collection(u'songs').where(u'time_signature', u'==', 4)
    docs = docs.where(u'explicit', u'==', False)
    docs = docs.where(u'mode', u'==', 1).stream()
    spotify_df = pd.DataFrame()
    counter = 0
    for doc in docs:
        spotify_df = pd.concat([spotify_df, pd.DataFrame([doc.to_dict()])], ignore_index = True)
        counter += 1
        if counter >= 100000:
            return spotify_df

    return spotify_df

def perform_feature_engineering(df, float_cols):

    # fix the genre column and prepare it for one-hot-encoding
    #df['genre'] = df['genre'].apply(lambda x: [re.sub(' ','_',i) for i in re.findall(r"'([^']*)'", x)])
    #df['genre'] = df['genre'].apply(lambda d: d if isinstance(d, list) else [])

    # create 5 point buckets for popularity 
    df['popularity_red'] = df['popularity'].apply(lambda x: int(x/5))
    #Drops duplicate song IDs, in case songs are repeated in the database and would generate good recommendations
    df = df.drop_duplicates(subset=['id'])
    #Run tfidf on genre list and finish preparation of the rest of the categories
    complete_feature_set = create_feature_set(df, float_cols=float_cols)

    return df, complete_feature_set

def load_playlist_object_from_json(json):
    """ 
    Creates a dataframe from the json argument passed to the request to be used for playlist summary

    Parameters: 
        json (json string): the song tree
        
    Returns: 
        playlist: a list of all the song IDs in the JSON
    """
    #generate playlist dataframe
    playlist = pd.DataFrame()
    playlist = populate_playlist(json, playlist)
    return playlist

def populate_playlist(json, playlist):
    """
    Recursively adds items to playlist from json

    Parameters:
        json (json string): the song tree, which at the highest level has a root, left, and right objects
        playlist (pd.Dataframe): a dataframe containing all song IDs parent nods and possibly others

    Returns:
        playlist, updated with the song IDs of root and all children
    """

    if json["root"] is None:
        return playlist
    rootid = json["root"]["song"]["id"]
    playlist = pd.concat([playlist, pd.DataFrame([{'id': rootid}])], ignore_index=True)
    
    #Call recursively on the left and right children if they are not null
    if json["left"] is not None:
        playlist = populate_playlist(json["left"], playlist)

    if json["right"] is not None:
        playlist = populate_playlist(json["right"], playlist)

    return playlist

def find_lineage(json, weights, senderId):
    if json["root"] is None:
        return False
    rootid = json["root"]["song"]["id"]
    if rootid == senderId:
        return True
    print(weights[rootid])
    children = []
    if json["left"] is not None:
        children.append(json["left"])
    if json["right"] is not None:
        children.append(json["right"])

    for child in children:
        if find_lineage(child, weights, senderId):
            return True
    
    weights[rootid] = 0 * weights[rootid]
    return False

def calculate_weights(playlist, json, maxdiffs, weights = None, parent = None):
    
    # start with weight playlist as all 1s
    if (weights is None):
        pcols = playlist.columns
        weights = pd.concat([pd.DataFrame(np.ones((len(playlist), len(playlist.columns) - 1)), columns = pcols.drop(['id'])), playlist['id']], axis=1)
        # return weights

    # start with root, set all weights in the row corresponding to root in the playlist to be 1
    if json["root"] is None:
        return weights
    rootid = json["root"]["song"]["id"]
    # if this is the absolute root then its weights are already 1s, so do nothing
    if parent is not None:
        if (len(playlist.loc[playlist['id'] == rootid].index) >= 1):
            cols = weights.columns.tolist()
            cols.remove('id')
            for colname in cols:
                parent_colname_attribute = float(playlist.loc[playlist['id'] == parent].iloc[0][colname])
                parent_colname_attribute_weight = float(weights.loc[weights['id'] == parent].iloc[0][colname])
                child_colname_attribute = float(playlist.loc[playlist['id'] == rootid].iloc[0][colname])
                md = float(maxdiffs[colname])
                # weight function which assigns value 3/2 * parent weight at max similarity, 2/3 * parent weight at max dissimilarity, and is quadratically related
                weights.at[np.where((weights['id'] == rootid).values == True)[0][0], colname] = (-5/(6*md**2) * (abs(parent_colname_attribute - child_colname_attribute))**2 + 3/2) * parent_colname_attribute_weight

    # make a recursive call on left and on right (if either is null, exclude that one), passing in root as parent weights
    if json["left"] is not None:
        weights = calculate_weights(playlist, json["left"], maxdiffs, weights, rootid)
    if json["right"] is not None:
        weights = calculate_weights(playlist, json["right"], maxdiffs, weights, rootid)

    # after recursive call on left and right is done, return weight playlist
    return weights

def compute_max_diffs(feature_set):
    """
    Computes the maximum difference between two values in a given column. This is used to scale weights in calculate_weights, and the return is passed as an argument there.
    """
    maxdiffs = {}
    cols = feature_set.columns.tolist()
    cols.remove('id')
    for colname in cols:
        col = feature_set.loc[:,colname]
        maxdiffs[colname] = abs(float(max(col)) - float(min(col)))
    return maxdiffs

def generate_playlist_features(complete_feature_set, playlist_df, json, senderId):
    """ 
    Splits the complete feature set into the feature set for songs in the json tree (a), and the feature set for songs not in the json tree (b).
    Then a is used to compute recommendations in b later. 

    Parameters: 
        complete_feature_set (pandas dataframe): Dataframe which includes all of the features for the songs in the database
        playlist_df (pandas dataframe): Dataframe which contains all song IDs in the json tree
        json: the json tree which playlist_df was built on
        
    Returns: 
        playlist_feature_set (pandas series): single feature that summarizes the playlist
        nonplaylist_feature_set (pandas dataframe): Dataframe containing all features for all songs in the database which are not in playlist_df
    """
    playlist_feature_set = complete_feature_set[complete_feature_set['id'].isin(playlist_df['id'].values)]
    playlist_feature_set = playlist_feature_set.merge(playlist_df[['id']], on = 'id', how = 'inner')
    nonplaylist_feature_set = complete_feature_set[~complete_feature_set['id'].isin(playlist_df['id'].values)]
    maxdiffs = compute_max_diffs(complete_feature_set)
    weight_vec = calculate_weights(playlist_feature_set,json,maxdiffs)
    weight_vec = find_lineage(json,weight_vec,senderId)
    playlist_feature_set.update(playlist_feature_set.iloc[:,:-1].mul(weight_vec,0))
    playlist_feature_set = playlist_feature_set.iloc[:, :-1]
    
    return playlist_feature_set.sum(axis = 0), nonplaylist_feature_set

def generate_playlist_recos(df, features, nonplaylist_features, top_many):
    """ 
    Pull songs from a specific playlist.

    Parameters: 
        df (pandas dataframe): spotify dataframe
        features (pandas series): summarized playlist feature
        nonplaylist_features (pandas dataframe): feature set of songs that are not in the selected playlist
        top_many (int): how many songs to return in the algorithm
        
    Returns: 
        non_playlist_df_top_x: Top top_many recommendations for that playlist
    """
    #good options: cosine similarity, cosine distances
    non_playlist_df = df[df['id'].isin(nonplaylist_features['id'].values)]
    non_playlist_df['sim'] = pw.cosine_distances(nonplaylist_features.drop('id', axis = 1).values, features.values.reshape(1, -1))
    non_playlist_df_top_x = non_playlist_df.sort_values('sim',ascending = True).head(top_many)
    
    return non_playlist_df_top_x

def extract_artists(artist_dict):
    str = ""
    n_artists = len(artist_dict)
    for artist in range(0,n_artists):
        str += artist_dict[artist]['name']
        if (artist != n_artists - 1):
            str += ", "
    return str

def output_recommendations(recs, sp):
    i = 0
    for rec in recs.iterrows():
        i += 1
        print(str(i) + ". " + sp.track(rec[1]['id'])['name'] + " by " + extract_artists(sp.track(rec[1]['id'])['artists']))

pd.set_option('display.max_columns', None)

def perform_analysis(pname, spotify_df, senderId):
    float_cols = np.concatenate([spotify_df.dtypes[spotify_df.dtypes == 'float64'].index.values,spotify_df.dtypes[spotify_df.dtypes == 'int64'].index.values])
    spotify_df, complete_feature_set = perform_feature_engineering(spotify_df, float_cols)
    test_playlist = load_playlist_object_from_json(pname)
    complete_feature_set_playlist_vector, complete_feature_set_nonplaylist = generate_playlist_features(complete_feature_set, test_playlist, pname, senderId)
    recs_top10 = generate_playlist_recos(spotify_df, complete_feature_set_playlist_vector, complete_feature_set_nonplaylist, 2)
    return recs_top10['id']

@app.route('/algo/', methods=['POST'])
@cross_origin
def algorithm():
    data = request.json
    print('got:\n' + str(data) + '\n------\n')
    pname = data["pname"]
    sender = data["sender"]
    recos = perform_analysis(pname, spotify_df_global, sender)
    req = '{"songs":['
    for id in range(len(recos.values) - 1):
        req = req + '"' + recos.values[id] + '",'
    req = req + '"' + recos.values[len(recos.values)-1] + '"]}'
    print(req)
    print("Done!")
    return req

@app.route('/getmsg/', methods=['GET'])
def respond():
    name = request.args.get("name", None)
    print(f"got name: {name}")
    response = {}

    if not name:
        response["ERROR"] = "no name found"
    elif str(name).isdigit():
        response["ERROR"] = "name cna't be numeric"
    else:
        response['MESSAGE'] = f"Hello {name}!"

    return jsonify(response)

def get_client_credentials(): 
    client_id = "d45e1813a6564588837b5f187309b617"
    client_secret = "62aa2ebebc3c408cb399bc94e0d03df7"
    if client_secret == None or client_id == None: 
        raise Exception ("you must enter both your client_id and client_secret!")
    else: 
        client_creds = f"{client_id}:{client_secret}"
        client_creds_b64 = base64.b64encode(client_creds.encode())
        return client_creds_b64.decode()

def get_token_headers():
    client_creds_b64 = get_client_credentials()
    return f"Basic {client_creds_b64}"

def getToken(code):
	token_url = 'https://accounts.spotify.com/api/token'
	authorization = get_token_headers()
	redirect_uri = "http://127.0.0.1:5002/callback"

	headers = {'Authorization': authorization, 'Accept': 'application/json', 'Content-Type': 'application/x-www-form-urlencoded'}
	body = {'code': code, 'redirect_uri': redirect_uri, 'grant_type': 'authorization_code'}
	post_response = requests.post(token_url, headers=headers, data=body)

	# 200 code indicates access token was properly granted
	if post_response.status_code == 200:
		json = post_response.json()
		return json['access_token'], json['refresh_token'], json['expires_in']
	else:
		logging.error('getToken:' + str(post_response.status_code))
		return None

# def refreshToken(refresh_token):
# 	token_url = 'https://accounts.spotify.com/api/token'
# 	authorization = get_token_headers
#     refresh_token = app.config['REFRESH_TOKEN']

# 	headers = {'Authorization': authorization, 'Accept': 'application/json', 'Content-Type': 'application/x-www-form-urlencoded'}
# 	body = {'refresh_token': refresh_token, 'grant_type': 'refresh_token'}
# 	post_response = requests.post(token_url, headers=headers, data=body)

# 	# 200 code indicates access token was properly granted
# 	if post_response.status_code == 200:
# 		return post_response.json()['access_token'], post_response.json()['expires_in']
# 	else:
# 		logging.error('refreshToken:' + str(post_response.status_code))
# 		return None
 

# to fix main.js 
# 'accessToken:auth_token'
#  Sc,{to:"/tutorial"}
@app.route("/") #code adapted from https://github.com/lucaoh21/Spotify-Discover-2.0/blob/master/routes.py
def index():
    return render_template("index.html")
@app.route("/tutorial")
def tutorial():
    return index()

@app.route("/auth")
def auth(): 
	client_id = app.config['CLIENT_ID']
	client_secret =  app.config['CLIENT_SECRET']
	redirect_uri = "http://127.0.0.1:5002/callback"
	scope =  'ugc-image-upload, user-read-playback-state, user-modify-playback-state, user-read-currently-playing, streaming app-remote-control, user-read-email, user-read-private, playlist-read-collaborative, playlist-modify-public, playlist-read-private, playlist-modify-private, user-library-modify, user-library-read, user-top-read, user-read-playback-position, user-read-recently-played, user-follow-read, user-follow-modify'
	authorize_url = 'https://accounts.spotify.com/en/authorize?'
	parameters = 'response_type=code&client_id=' + client_id + '&redirect_uri=' + redirect_uri + '&scope=' + scope 
	response = make_response(redirect(authorize_url + parameters))
	print('authorizing user...', file=sys.stderr)
	return response
	
@app.route('/callback')
def callback():
    print(request.args, file=sys.stderr)
    code = request.args.get('code')
    payload = getToken(code)
    print(payload, file=sys.stderr)
    if payload != None: 
        session['token'] = payload[0]
        session['refresh_token'] = payload[1]
        session['token_expiration'] = time.time() + payload[2]

        app.config['AUTH_KEY'] = payload[0]
        app.config['REFRESH_TOKEN'] = payload[1]
        app.config['EXPIRATION_TIME'] = payload[2]

        authData = {'auth_key': {payload[0]}, 'refresh_token':{payload[1]}, 'expiration': {payload[2]}}

        return redirect(url_for('tutorial', authData= authData))
    else: 
        return auth()
    



#Firebase stuff
def pull_firebase():
    project_id = "bloom-838b5"
    if not len(firebase_admin._apps):
        cred = credentials.ApplicationDefault()
        firebase_admin.initialize_app(cred, {'projectId': project_id})

    db = firestore.client()
    export_db(db)

if __name__ == '__main__':
    spotify_df_global = load_data_local()
    app.secret_key = 'this is my very super secret key'
    print("starting!")
    #download_from_github() This all needs to be done to make editing easier --> shouldn't have to run winbuild for every little update to the bloom repo
    #() # probably the hardest
    #move_files_from_dist()
    app.run(threaded=True, port=5002)
