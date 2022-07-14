
import SpotifyWebApi from 'spotify-web-api-node'
import express from 'express'

var spotifyApi = new SpotifyWebApi({
    clientId: 'd45e1813a6564588837b5f187309b617',
    clientSecret: '62aa2ebebc3c408cb399bc94e0d03df7', //translate
    redirectUri: 'http://localhost:8888/callback'
});

async function getAuthKey(){
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
        // run server with: heroku local -f Procfile.windows
        
        const app = express();
        
        app.get('/login', (req, res) => {
            res.redirect(spotifyApi.createAuthorizeURL(scopes)); //need to convert into python that runs when the server is started. ALL I NEED TO DO IS RETURN A REDIRECT AND THEN FIGURE SOMETHING OUT...
            
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
                    console.log(authKey)
                    //console.log(refresh_token)
                    
                    resolve(authKey)
                    console.log(
                        `Successfully retrieved access token. Expires in ${expires_in} s.`
                    );
                    
                    res.send('<html><head><title>Hello!</title><script>token="'+authKey+'";</script></head><body>This is the bloom page</body></html>');
                    
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
                    res.redirect(spotifyApi.createAuthorizeURL(scopes));
                });
        });
        app.listen(8888, () =>
            console.log(
                'HTTP Server up. Now go to http://localhost:8888/login in your browser.'
            )
        );
    

} //end of main function