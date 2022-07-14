import base64
import requests

"""
To generate both the authentication and refresh tokens, instantiate a spotify web api object which takes your clientId and clientSecret
and then run 'your_api_object'.getAuthKey(), which returns the necessary keys. These should be saved to the database for repeated use. All of this
should ideally run with an 'onClick' on the login page. This way, we no longer need Express to generate keys. 
"""
class SpotifyAPI: 
    access_token = None
    refresh_token = 'AQAh9ZZZaAXIgosnDYSjZxjyNN70NeH836o4_XcefacQNAyotfgjiO8-j49cRJ0sEutlqwMnPRPuPSwAjIjU_tuefFbEF-mHOzE0rOXLiMx0FFaih8wOvgh-YXaeywZ6rVc'
    access_token_expires = '3600s'
    token_url = "https://accounts.spotify.com/api/token"

    def __init__(self, client_id, client_secret):
        self.client_id = client_id
        self.client_secret = client_secret

    def get_client_credentials(self):
        """
        Returns a base64 encoded string
        """

        if self.client_secret == None or self.client_id == None:
            raise Exception("You must set client_id and client_secret")
        client_creds = f"{self.client_id}:{self.client_secret}"
        client_creds_b64 = base64.b64encode(client_creds.encode())
        return client_creds_b64.decode()

    def get_token_headers(self):
        client_creds_b64 = self.get_client_credentials()
        return {
            "Authorization": f"Basic {client_creds_b64}"
        }

    def get_token_data(self):
        return {
            "grant_type": "client_credentials"
        }

    def getAuthKey(self):
        token_url = self.token_url
        token_data = self.get_token_data()
        token_headers = self.get_token_headers()
        r = requests.post(token_url, data=token_data, headers=token_headers)
        if r.status_code not in range(200, 299):
            return False
        data = r.json()
        access_token = data['access_token']
        self.access_token = access_token
        print('The new auth token expires in: ' + self.access_token_expires + '\n')
        print('Auth token: ' + self.access_token,
              '\n' + 'Refresh token: ' + self.refresh_token)  # refresh token is indefinite apparently so I just generated that manually. May not work when we have a ton of users.
        return self.access_token, self.refresh_token


cID = 'd45e1813a6564588837b5f187309b617'
cSec = '62aa2ebebc3c408cb399bc94e0d03df7'

spotify = SpotifyAPI(cID, cSec)  # instantiating the spotify web api object with our client secret and client id
spotify.getAuthKey()
