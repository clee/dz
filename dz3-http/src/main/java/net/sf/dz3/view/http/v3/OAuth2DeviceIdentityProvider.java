package net.sf.dz3.view.http.v3;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.sf.dz3.instrumentation.Marker;
import net.sf.dz3.view.http.common.HttpClientFactory;

/**
 * Stateless {@link https://oauth.net/2/device-flow/ OAuth 2.0 Device Flow} identity provider.
 * 
 * @author Copyright &copy; <a href="mailto:vt@freehold.crocodile.org">Vadim Tkachenko</a> 2001-2018
 */
public class OAuth2DeviceIdentityProvider {

    private final Logger logger = LogManager.getLogger(getClass());

    private final HttpClient httpClient = HttpClientFactory.createClient();

    /**
     * Obtain a client identity with given credentials.
     *
     * @param clientId OAuth 2.0 client ID.
     * @param clientSecret OAuth 2.0 client secret.
     * @param refreshTokenFile File for the refresh token to be stored to and retrieved from.
     * @param requesterIdentity Name of the module requesting authentication. Informational only,
     * but it would be a good idea to make it match the OAuth client name not to confuse the user.
     *
     * @return Client identity.
     */
    public String getIdentity(String clientId, String clientSecret, File refreshTokenFile, String requesterIdentity) throws IOException, InterruptedException {

        ThreadContext.push("getIdentity");
        Marker m = new Marker("getIdentity");

        try {

            // VT: FIXME: Add sanity checks for clientId
            // VT: FIXME: Add sanity checks for clientSecret
            // VT: FIXME: Add sanity checks for refreshTokenFileName

            String refreshToken = getString(refreshTokenFile, "refresh token", true);
            String accessToken;

            if (refreshToken == null) {

                accessToken = acquire(httpClient, clientId, clientSecret, refreshTokenFile, requesterIdentity);

            } else {

                accessToken = refresh(httpClient, clientId, clientSecret, refreshToken);
            }

            return getIdentityByToken(httpClient, accessToken);

        } finally {

            m.close();
            ThreadContext.pop();
        }

    }

    public String getIdentity(File clientIdFile, File clientSecretFile, File refreshTokenFile, String requesterIdentity) throws IOException, InterruptedException {

        return getIdentity(
                getString(clientIdFile, null, false),
                getString(clientSecretFile, null, false),
                refreshTokenFile,
                requesterIdentity);
    }

    private void storeRefreshToken(File target, String token) throws IOException {

        ThreadContext.push("storeRefreshToken");

        try {

            logger.debug("target: " + target);

            // Create the directory if it doesn't exist
            File dir = target.getParentFile();

            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new IOException("couldn't create " + dir);
                }
            }

            PrintWriter pw = new PrintWriter(new FileWriter(target));

            pw.println(token);
            pw.close();

        } finally {
            ThreadContext.pop();
        }
    }

    /**
     * Read a single string from the given file.
     *
     * Used to read {@code client_id}, {@code client_secret}, and {@code refresh_token}.
     *
     * @param source File to read the string from.
     * @param title Title to report in the log if needed.
     * @param allowMissing {@code true} to return {@code null} in case the file is missing, {@code false} to throw an exception.
     *
     * @return The string, or {@code null} if the file doesn't exist and {@code allowMissing} is {@code true}.
     * @throws IOException if things went sour.
     * @throws IllegalStateException if the file is missing and {@code allowMissing} is {@code false}.
     */
    private String getString(File source, String title, boolean allowMissing) throws IOException {

        if (!source.exists()) {

            if (allowMissing) {

                logger.warn(source + " doesn't exist, assuming no " + title);
                return null;
            }
        }

        try (BufferedReader br = new BufferedReader(new FileReader(source))) {

            return br.readLine();
        }
    }

    /**
     * @return {@code access_token}.
     */
    private String acquire(
            HttpClient httpClient,
            String clientId,
            String clientSecret,
            File refreshTokenFile,
            String requesterIdentity) throws IOException, InterruptedException {

        ThreadContext.push("acquire");

        try {

            // Step 1
            // https://developers.google.com/identity/protocols/OAuth2ForDevices#step-1-request-device-and-user-codes

            URIBuilder builder = new URIBuilder("https://accounts.google.com/o/oauth2/device/code");

            builder.addParameter("client_id", clientId);
            builder.addParameter("scope", "email");

            HttpPost post = new HttpPost(builder.toString());

            post.setHeader("Content-Type", "application/x-www-form-urlencoded");

            HttpResponse rsp = httpClient.execute(post);
            int rc = rsp.getStatusLine().getStatusCode();

            // Step 2
            // https://developers.google.com/identity/protocols/OAuth2ForDevices#step-2-handle-the-authorization-server-response

            logger.debug("RC=" + rc);

            if (rc != 200) {

                logger.error("HTTP rc=" + rc + ", text follows:");
                logger.error(EntityUtils.toString(rsp.getEntity()));

                throw new IllegalStateException("request failed with HTTP code " + rc + ", see log for details");
            }

            String responseJson = EntityUtils.toString(rsp.getEntity());
            Gson gson = new Gson();
            Type mapType  = new TypeToken<Map<String,String>>(){}.getType();
            Map<String,String> responseMap = gson.fromJson(responseJson, mapType);

            logger.debug("response: " + responseMap);

            String verificationUrl = responseMap.get("verification_url");
            String userCode =  responseMap.get("user_code");
            int interval = Integer.parseInt(responseMap.get("interval").toString());

            // VT: FIXME: The loop will break after "expires_in"

            while (true) {

                // Step 3
                // https://developers.google.com/identity/protocols/OAuth2ForDevices#displayingthecode

                // It will most probably display this URL: https://www.google.com/device

                // VT: NOTE: Let's set the level to "warn" so it stands out

                logger.warn("OAuth 2.0 login to " + requesterIdentity + ": action required" );
                logger.warn("Please go to " + verificationUrl);
                logger.warn("and enter this code: " + userCode);

                Thread.sleep(interval * 1000);

                {
                    // Step 4
                    // https://developers.google.com/identity/protocols/OAuth2ForDevices#step-4-poll-googles-authorization-server

                    // Step 5 - on a different device
                    // https://developers.google.com/identity/protocols/OAuth2ForDevices#step-5-user-responds-to-access-request

                    builder = new URIBuilder("https://www.googleapis.com/oauth2/v4/token");

                    builder.addParameter("client_id", clientId);
                    builder.addParameter("client_secret", clientSecret);
                    builder.addParameter("code", responseMap.get("device_code"));
                    builder.addParameter("grant_type", "http://oauth.net/grant_type/device/1.0");

                    post = new HttpPost(builder.toString());
                    post.setHeader("Content-Type", "application/x-www-form-urlencoded");

                    rsp = httpClient.execute(post);
                    rc = rsp.getStatusLine().getStatusCode();
                    String responseBody = EntityUtils.toString(rsp.getEntity());

                    logger.debug("RC=" + rc);

                    if (rc != 200) {

                        if (rc == 428) {

                            logger.info("waiting for user response, will retry in " + interval + " seconds");

                        } else {

                            // VT: NOTE: These are not really expected, but let's hope that the user
                            // figures out what to with them

                            logger.error("Unexpected: HTTP rc=" + rc + ", text follows:");
                            logger.error(responseBody);

                            logger.warn("will retry in " + interval + " seconds");
                        }
                    }

                    responseJson = responseBody;

                    logger.debug("response: " + responseJson);

                    if (rc == 200) {
                        break;
                    }
                }
            }

            // Step 6
            // https://developers.google.com/identity/protocols/OAuth2ForDevices#step-6-handle-responses-to-polling-requests

            {
                responseMap = gson.fromJson(responseJson, mapType);

                String accessToken = responseMap.get("access_token");
                String refreshToken = responseMap.get("refresh_token");

                {
                    // VT: FIXME: Need to improve protocol handling should this become a problem

                    //String expiresIn = responseMap.get("expires_in");
                    //String tokenType = responseMap.get("token_type");

                    // Two fields below are not documented un the page above

                    //String scope = responseMap.get("scope");

                    // This is JWT: https://stackoverflow.com/questions/8311836/how-to-identify-a-google-oauth2-user/13016081#13016081
                    // Has the information we need to match HCC Core with HCC Remote.

                    //String idToken = responseMap.get("id_token");
                }

                storeRefreshToken(refreshTokenFile, refreshToken);

                return accessToken;
            }

        } catch (URISyntaxException ex) {

            throw new IOException("malformed URI", ex);

        } finally {
            ThreadContext.pop();
        }
    }

    /**
     * @return {@code access_token}.
     */
    private String refresh(HttpClient httpClient, String clientId, String clientSecret, String refreshToken) throws IOException {

        ThreadContext.push("refresh");

        try {

            URIBuilder builder = new URIBuilder("https://www.googleapis.com/oauth2/v4/token");

            builder.addParameter("client_id", clientId);
            builder.addParameter("client_secret", clientSecret);
            builder.addParameter("refresh_token", refreshToken);
            builder.addParameter("grant_type", "refresh_token");

            HttpPost post = new HttpPost(builder.toString());
            post.setHeader("Content-Type", "application/x-www-form-urlencoded");

            HttpResponse rsp = httpClient.execute(post);
            int rc = rsp.getStatusLine().getStatusCode();

            logger.debug("RC=" + rc);

            if (rc != 200) {

                logger.error("HTTP rc=" + rc + ", text follows:");
                logger.error(EntityUtils.toString(rsp.getEntity()));

                throw new IllegalStateException("request failed with HTTP code " + rc + ", see log for details");
            }

            String responseJson = EntityUtils.toString(rsp.getEntity());
            Gson gson = new Gson();
            Type mapType  = new TypeToken<Map<String,String>>(){}.getType();
            Map<String,String> responseMap = gson.fromJson(responseJson, mapType);

            logger.debug("response: " + responseMap);

            return responseMap.get("access_token");

        } catch (URISyntaxException ex) {

            throw new IOException("malformed URI", ex);

        } finally {
            ThreadContext.pop();
        }
    }

    /**
     * Retrieve the client identity from the access token.
     *
     * @param httpClient Transport to use.
     * @param accessToken Access token to use.
     *
     * @return Client identity.
     */
    private String getIdentityByToken(HttpClient httpClient, String accessToken) throws IOException {

        ThreadContext.push("getIdentityByToken");

        try {

            HttpPost post = new HttpPost("https://www.googleapis.com/oauth2/v3/userinfo");

            post.setHeader("Content-Type", "application/x-www-form-urlencoded");
            post.setHeader("Authorization", "Bearer " + accessToken);

            HttpResponse rsp = httpClient.execute(post);
            int rc = rsp.getStatusLine().getStatusCode();

            logger.debug("RC=" + rc);

            if (rc != 200) {

                logger.error("HTTP rc=" + rc + ", text follows:");
                logger.error(EntityUtils.toString(rsp.getEntity()));

                throw new IllegalStateException("OAuth 2.0 for TV and Limited-Input Device Applications: couldn't finish the sequence");
            }

            String responseJson = EntityUtils.toString(rsp.getEntity());

            logger.debug("response: " + responseJson);

            Gson gson = new Gson();
            Type mapType  = new TypeToken<Map<String,String>>(){}.getType();
            Map<String,String> responseMap = gson.fromJson(responseJson, mapType);
            String email = responseMap.get("email");

            if (email == null || "".equals(email)) {
                throw new IllegalStateException("null or empty email, shouldn't have ended up here");
            }

            return email;

        } finally {
            ThreadContext.pop();
        }
    }
}
