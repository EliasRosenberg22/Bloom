var SpotifyWebApi = require('spotify-web-api-node');
const express = require('express')
var fetch = require('node-fetch')

// This file is based off of code from: https://github.com/thelinmichael/spotify-web-api-node/blob/master/examples/tutorial/00-get-access-token.js

var spotifyApi = new SpotifyWebApi({
    clientId: 'd45e1813a6564588837b5f187309b617',
    clientSecret: '',
    redirectUri: 'http://localhost:8888/callback'
});


var authKey = null
var spotifyID = 'fnh6px78guvi666lyrtmr04p4'
var playlist_name_to_ID = {}
var test_songs_to_add = [ '4TEaJcvzB913p8iCoul3uJ', '4TEaJcvzB913p8iCoul3uJ', '4TEaJcvzB913p8iCoul3uJ','4TEaJcvzB913p8iCoul3uJ','4TEaJcvzB913p8iCoul3uJ','4TEaJcvzB913p8iCoul3uJ','4TEaJcvzB913p8iCoul3uJ'] //test song ids to add to a playlist

async function getAuthKey(){
    let authKey = await new Promise(async function (resolve, reject)  {
    const scopes = [
        'ugc-image-upload',
        'user-read-playback-state',
        'user-modify-playback-state',
        'user-read-currently-playing',
        'streaming',
        'app-remote-control',
        'user-read-email',
        'user-read-private',
        'playlist-read-collaborative',
        'playlist-modify-public',
        'playlist-read-private',
        'playlist-modify-private',
        'user-library-modify',
        'user-library-read',
        'user-top-read',
        'user-read-playback-position',
        'user-read-recently-played',
        'user-follow-read',
        'user-follow-modify'
    ];

    // credentials are optional


    const app = express();

    app.get('/login', (req, res) => {
        res.redirect(spotifyApi.createAuthorizeURL(scopes));
    });

    app.get('/callback', (req, res) => {
        const error = req.query.error;
        const code = req.query.code;
        const state = req.query.state;

        if (error) {
            console.error('Callback Error:', error);
            res.send(`Callback Error: ${error}`);
            reject(error)
            return;
        }

        spotifyApi
            .authorizationCodeGrant(code)
            .then(data => {
                const access_token = data.body['access_token'];
                const refresh_token = data.body['refresh_token'];
                const expires_in = data.body['expires_in'];

                spotifyApi.setAccessToken(access_token);
                spotifyApi.setRefreshToken(refresh_token);

                let authKey = data.body['access_token']
                resolve(authKey)




                console.log(
                    `Successfully retrieved access token. Expires in ${expires_in} s.`
                );

                res.send('Success! You can now close the window.');

                setInterval(async () => {
                    const data = await spotifyApi.refreshAccessToken();
                    const access_token = await data.body['access_token'];

                    console.log('The access token has been refreshed!');
                    console.log('access_token:', access_token);
                    authKey = access_token // resetting the saved variable for later use

                    spotifyApi.setAccessToken(access_token);
                }, expires_in / 2 * 1000);
            })
            .catch(error => {
                console.error('Error getting Tokens:', error);
                res.send(`Error getting Tokens: ${error}`);
                reject(error)
            });
    });
    app.listen(8888, () =>
        console.log(
            'HTTP Server up. Now go to http://localhost:8888/login in your browser.'
        )
    );
    })

    // run helper functions that require access token here
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
//Need to do something like, if user clicks this, run this function with certain params.

    




//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++


} //end of main function

//These helper functions were taken from https://github.com/thelinmichael/spotify-web-api-node, to whom I give full credit.





// helper functions for handling user data (making playlists, adding songs to playlists, playing songs, adding to queue)
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++


/*
gets the current user and returns their data. Used to see who is currently logged into the account. Takes the api object we
are currently using. In this case it is called 'spotifyApi'
 */
function getMe(api){
    api.getMe()
        .then(function(data) {
            console.log('Some information about the authenticated user', data.body);
        }, function(err) {
            console.log('Something went wrong!', err);
        });
}

/*
gets the playlists for the user param. User param takes a user's id, not their visible spotify name. Users must go to their
account info to find the string of their actual spotify id.
 */
function getUserPlaylists(api, userID){
    api.getUserPlaylists(userID)
        .then(function(data) {
            console.log('Retrieved playlists', data.body);
        },function(err) {
            console.log('Something went wrong!', err);
        });
}


/*
creates an empty playlist with a name and description taken as a playlist param. For now, let's just request name.
 */
function createPlaylist(api, name){
    api.createPlaylist(name, { 'description': 'Description_placeholder', 'public': true })
        .then(function(data) {
            console.log('Created playlist: ' + "\'"+name+"\'" + ' with id: ' + data.body['id']);
            playlist_name_to_ID[name] = data.body['id']
        }, function(err) {
            console.log('Something went wrong!', err);
        });
}

/*
takes the api, a playlist id, and an array of song ids to be added to that playlist.
 */
function addTracksToPlaylist(api, playlistID, song_array){
    var newArray = []
    for (var track in song_array){
        newArray.push('spotify:track:' + song_array[track])
    }

    api.addTracksToPlaylist(playlistID, newArray)
        .then(function(data) {
            console.log('Added tracks to playlist: ' + playlistID);
        }, function(err) {
            console.log('Something went wrong!', err);
        });
}


/*
gets the information of a specific playlist. Takes that playlist's id as a param, as well as the api.
 */
function getPlaylist(api, playlistID){
    api.getPlaylist(playlistID)
        .then(function(data) {
            console.log('Some information about this playlist', data.body);
        }, function(err) {
            console.log('Something went wrong!', err);
        });
}


// helper functions for grabbing spotify data (song, album, artist metadata, etc.)
//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++


/*
returns the id of song given the track name and artist
 */
function findSongID(track, artist) {
// Search for tracks with given track name and artist name
    spotifyApi.searchTracks('track:' + track + ' artist:' + artist)
        .then(function (data) {
            //console.log(data.body['tracks']['items'][0]['id']);
            var id = data.body['tracks']['items'][0]['id'] //song id for pulling metadata.
            console.log(id)

        }, function (err) {
            console.error(err);
        });
}



/* returns an artist's spotify ID given just their name. Used for finding top tracks with an artist ID.
 */
async function findArtistID(api, artist) {
    api.searchArtists(artist)
        .then(async function (data) {
            var id = await data.body['artists']['items'][0]['id']
            console.log(artist + ': ' + id)
            return id

        }, function(err) {
            console.error(err);
        });
}

//still needs function getting all the ids from an artist's discography. Can then run the below method on that, get the
//track IDs, and then run all those to add to the dataset.

/*
returns the id's of the tracks for a given albumID.
 */
function getTracksFromAlbum(api, albumID) {
    api.getAlbum(albumID)
        .then(function(data) {
            for(var track in data.body['tracks']['items']){
                console.log(data.body['tracks']['items'][track]['id'])
            }
        }, function(err) {
            console.error(err);
        });
}


/*
returns the genre data for an artist to be attached to their metadata.
 */
async function getArtistGenre(api, artistID) {
    return new Promise(function (resolve, reject){
        api.getArtist(artistID)
            .then(function (data) {
                // console.log(data.body)
                resolve(data)
            }, function (err) {
                reject(err);
            });
    });
}


/*
returns the metadata JSON object for a given track ID
 */
async function findMetaData(api, id) {
    return new Promise(function (resolve, reject){
        api.getAudioFeaturesForTrack(id)
            .then(function (data) {
                // console.log(data.body)
                resolve(data)
            }, function (err) {
                reject(err);
            });
    });
}



/* returns a JSON object containing top tracks for a given artist's ID. Will use this to build out the base dataset for
the website tutorial. Can also be used to find all albums of an artist, and all the songs on those albums. MUST be ran within
a loop that has at least a 4000 ms delay to respect REST api conditions for spotify, as seen in SpotifyData.js
 */
function findTopTracks(api, artistID) {
    console.log('start: ==============================================================================')
    // Get an artist's top tracks
    api.getArtistTopTracks(artistID, 'GB')
        .then(async function(data) {
            for(var tracks in data.body) {
                for (var track in data.body[tracks]) {
                    var id = data.body[tracks][track]['id']

                    try {
                        var metaData = await findMetaData(id)
                        var artistInfo = await getArtistGenre(artistID)

                    } catch (error) {
                        console.error('no song data found for' + id)
                        for (var data_tag in metaData.body) {
                            data.body[tracks][track][data_tag] = null
                            console.log("No metadata found. Marking the vectors as null")
                        }
                    }

                    data.body[tracks][track]['genres'] = artistInfo['body']['genres']
                    for(var data_tag in metaData.body) { //here we have to loop through every piece of metadata. and add it to the JSON
                        data.body[tracks][track][data_tag] = metaData.body[data_tag]
                    }
                    console.log(data.body[tracks][track])


                    var url = "https://us-central1-bloom-838b5.cloudfunctions.net/songinforeciever";
                    fetch(url, {method: "POST", headers: {'Content-Type': 'application/json'}, body: JSON.stringify(data.body[tracks][track])})
                }
            }
            console.log("end ============================================================================")
        }, function(err) {
            console.log('Something went wrong!', err);
        });
}




//Running main function ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
getAuthKey()


