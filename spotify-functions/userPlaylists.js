var SpotifyWebApi = require('spotify-web-api-node');
const express = require('express')
var fetch = require('node-fetch')

// This file is copied from: https://github.com/thelinmichael/spotify-web-api-node/blob/master/examples/tutorial/00-get-access-token.js


async function getAuthKey() {
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
    var spotifyApi = new SpotifyWebApi({
        clientId: 'd45e1813a6564588837b5f187309b617',
        clientSecret: '',
        redirectUri: 'http://localhost:8888/callback'
    });

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

                const accessToken = data.body['access_token']




                createEmptyPlaylist('Test3', accessToken)


                var id =" "
                //addTracksToPlaylist(spotifyApi, id)

                console.log(access_token)

                console.log(
                    `Successfully retrieved access token. Expires in ${expires_in} s.`
                );
                res.send('Success! You can now close the window.');

                setInterval(async () => {
                    const data = await spotifyApi.refreshAccessToken();
                    const access_token = data.body['access_token'];

                    console.log('The access token has been refreshed!');
                    console.log('access_token:', access_token);
                    spotifyApi.setAccessToken(access_token);
                }, expires_in / 2 * 1000);
            })
            .catch(error => {
                console.error('Error getting Tokens:', error);
                res.send(`Error getting Tokens: ${error}`);
            });
    });

    app.listen(8888, () =>
        console.log(
            'HTTP Server up. Now go to http://localhost:8888/login in your browser.'
        )
    );
}

async function createEmptyPlaylist(playlistName, auth) {
    const request = await fetch('https://api.spotify.com/v1/users/fnh6px78guvi666lyrtmr04p4/playlists', {
        method: 'POST',
        headers: {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Authorization": "Bearer " + auth

        },
        body:JSON.stringify( {
            "name": playlistName,
            "description": "New playlist description",
            "public": false
        })

    })  .then(response => response.json())
        .then(data => {
            console.log(data['id'])
            return data['id']
        })
}

// Add tracks to a playlist
async function addTracksToPlaylist(api, id) {
    api.addTracksToPlaylist(id, ['spotify:track:1qAuIPMALdFtGv2Ymjy5l0', 'spotify:track:2hEYslbdYWFSIA6JboUvnR', 'spotify:track:0W9Xvd4Qx1aZPxEi94vgRY', 'spotify:track:1fnULsZuORnAgCFFbM8nTZ',])
        .then(function (data) {
            console.log('Added tracks to playlist!');
        }, function (err) {
            console.log('Something went wrong!', err);
        });
}

getAuthKey()