/**
 * This file is part of the Goobi viewer - a content presentation and management application for digitized objects.
 *
 * Visit these websites for more information.
 *          - http://www.intranda.com
 *          - http://digiverso.com
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.goobi.viewer.servlets.openid;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.StringUtils;
import org.apache.oltu.oauth2.client.OAuthClient;
import org.apache.oltu.oauth2.client.URLConnectionClient;
import org.apache.oltu.oauth2.client.request.OAuthBearerClientRequest;
import org.apache.oltu.oauth2.client.request.OAuthClientRequest;
import org.apache.oltu.oauth2.client.response.GitHubTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthAccessTokenResponse;
import org.apache.oltu.oauth2.client.response.OAuthAuthzResponse;
import org.apache.oltu.oauth2.client.response.OAuthResourceResponse;
import org.apache.oltu.oauth2.client.validator.TokenValidator;
import org.apache.oltu.oauth2.common.OAuth;
import org.apache.oltu.oauth2.common.OAuthProviderType;
import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;
import org.apache.oltu.oauth2.common.message.types.GrantType;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.goobi.viewer.controller.BCrypt;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.managedbeans.UserBean;
import io.goobi.viewer.model.security.authentication.AuthResponseListener;
import io.goobi.viewer.model.security.authentication.AuthenticationProviderException;
import io.goobi.viewer.model.security.authentication.OpenIdProvider;
import io.goobi.viewer.model.security.user.User;
import io.goobi.viewer.servlets.utils.ServletUtils;

/**
 * OpenID Connect business logic.
 */
public class OAuthServlet extends HttpServlet {

    private static final long serialVersionUID = 6279885446798463881L;

    private static final Logger logger = LoggerFactory.getLogger(OAuthServlet.class);

    /** Constant <code>URL="oauth"</code> */
    public static final String URL = "oauth";

    private BCrypt bcrypt = new BCrypt();

    /**
     * This future gets fulfilled once the {@link UserBean} has finished setting up the sessin and redirecting the request. Completing the request
     * should wait after the redirect, otherwise it will not have any effect
     */
    Future<Boolean> redirected = null;

    /** {@inheritDoc} */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        doPost(request, response);
    }

    /** {@inheritDoc} */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // Apache Oltu
        try {
            OAuthAuthzResponse oar = OAuthAuthzResponse.oauthCodeAuthzResponse(request);
            // Confirm that the state received from the provider matches the state generated by the viewer
            AuthResponseListener<OpenIdProvider> listener = DataManager.getInstance().getOAuthResponseListener();
            for (OpenIdProvider provider : listener.getProviders()) {
                try {
                    boolean found = handleResponse(oar, provider, request, response);
                    if (found) {
                        listener.unregister(provider);
                        break;
                    }
                } catch (OAuthProblemException e) {
                    if (e.getMessage().startsWith("access_denied")) {
                        logger.debug("User aborted login");
                    } else {
                        logger.error(e.getMessage(), e);
                    }
                    listener.unregister(provider);
                } catch (OAuthSystemException | ParseException e) {
                    logger.error(e.getMessage(), e);
                    this.redirected = provider.completeLogin(null, request, response);
                    listener.unregister(provider);
                    //            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "OpenID Connect login failed");
                } catch (DAOException e) {
                    logger.debug("DAOException thrown here: {}", e.getMessage());
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database offline");
                    this.redirected = provider.completeLogin(null, request, response);
                    listener.unregister(provider);
                    return;
                } catch (AuthenticationProviderException e) {
                    logger.debug("AuthenticationProviderException thrown here: {}", e.getMessage());
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    this.redirected = provider.completeLogin(null, request, response);
                    listener.unregister(provider);
                    return;
                }
            }
        } catch (OAuthProblemException e) {
            if (e.getMessage().startsWith("access_denied")) {
                logger.debug("User aborted login");
            } else {
                logger.error(e.getMessage(), e);
            }
        }

        if (this.redirected != null) {
            try {
                this.redirected.get(1, TimeUnit.MINUTES); //redirected has an internal timeout, so this get() should never run into a timeout, but you never know
            } catch (InterruptedException e) {
                logger.warn("Waiting for redirect after login unterrupted unexpectedly");
            } catch (TimeoutException e) {
                logger.error("Waiting for redirect after login took longer than a minute. This should not happen. Redirect will not take place");
            } catch (ExecutionException e) {
                logger.error("Unexpected error while waiting for redirect", e);
            }
        }
    }

    private boolean handleResponse(OAuthAuthzResponse oar, OpenIdProvider provider, HttpServletRequest request, HttpServletResponse response)
            throws DAOException, OAuthProblemException, OAuthSystemException, ParseException, AuthenticationProviderException {
        if (provider.getoAuthState() == null || !oar.getState().equals(provider.getoAuthState())) {
            return false;
        }

        OAuthClientRequest oAuthTokenRequest = null;
        switch (provider.getName().toLowerCase()) {
            case "google":
                oAuthTokenRequest = OAuthClientRequest.tokenProvider(OAuthProviderType.GOOGLE)
                        .setGrantType(GrantType.AUTHORIZATION_CODE)
                        .setClientId(provider.getClientId())
                        .setClientSecret(provider.getClientSecret())
                        .setRedirectURI(ServletUtils.getServletPathWithHostAsUrlFromRequest(request) + "/" + URL)
                        .setCode(oar.getCode())
                        .buildBodyMessage(); {
                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
                OAuthAccessTokenResponse oAuthTokenResponse = oAuthClient.accessToken(oAuthTokenRequest);
                if (oAuthTokenResponse != null) {
                    TokenValidator tv = new TokenValidator();
                    tv.validate(oAuthTokenResponse);
                    provider.setoAuthAccessToken(oAuthTokenResponse.getAccessToken());
                    String idTokenEncoded = (oAuthTokenResponse.getParam("id_token"));
                    String[] idTokenEncodedSplit = idTokenEncoded.split("[.]");
                    if (idTokenEncodedSplit.length != 3) {
                        logger.error("Wrong number of segments in id_token. Expected 3, found " + idTokenEncodedSplit.length);
                        break;
                    }
                    // String header = new String(new Base64(true).decode(idTokenEncodedSplit[0]), Charset.forName("UTF-8"));
                    String payload = new String(new Base64(true).decode(idTokenEncodedSplit[1]), Charset.forName("UTF-8"));
                    // String signature = idTokenEncodedSplit[2];
                    JSONObject jsonPayload = (JSONObject) new JSONParser().parse(payload);
                    this.redirected = provider.completeLogin(jsonPayload, request, response);
                    return true;
                    //                    return provider.completeLogin();
                }
            }

                break;
            case "facebook":
                oAuthTokenRequest = OAuthClientRequest.tokenProvider(OAuthProviderType.FACEBOOK)
                        .setGrantType(GrantType.AUTHORIZATION_CODE)
                        .setClientId(provider.getClientId())
                        .setClientSecret(provider.getClientSecret())
                        .setRedirectURI(ServletUtils.getServletPathWithHostAsUrlFromRequest(request) + "/" + URL)
                        .setCode(oar.getCode())
                        .buildQueryMessage(); {
                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
                OAuthAccessTokenResponse oAuthTokenResponse = oAuthClient.accessToken(oAuthTokenRequest, GitHubTokenResponse.class);
                if (oAuthTokenResponse != null) {
                    TokenValidator tv = new TokenValidator();
                    tv.validate(oAuthTokenResponse);
                    provider.setoAuthAccessToken(oAuthTokenResponse.getAccessToken());

                    // Retrieve resources
                    OAuthClientRequest bearerClientRequest =
                            new OAuthBearerClientRequest("https://graph.facebook.com/me").setAccessToken(oAuthTokenResponse.getAccessToken())
                                    .buildQueryMessage();
                    OAuthResourceResponse resourceResponse =
                            oAuthClient.resource(bearerClientRequest, OAuth.HttpMethod.GET, OAuthResourceResponse.class);
                    if (resourceResponse != null) {
                        // logger.debug(resourceResponse.getBody());
                        JSONObject jsonProfile = (JSONObject) new JSONParser().parse(resourceResponse.getBody());
                        this.redirected = provider.completeLogin(jsonProfile, request, response);
                        return true;
                        //                        return provider.completeLogin();
                    }
                }
            }
                break;
            case "openid-test":
                String email = oar.getCode();
                String password = oar.getParam("token");
                Optional<User> user = loginUser(email, password);
                if (user.isPresent()) {
                    JSONObject jsonProfile = new JSONObject(Collections.singletonMap("email", email));
                    this.redirected = provider.completeLogin(jsonProfile, request, response);
                    return true;
                }
                break;
            default:
                // Other providers
                oAuthTokenRequest = OAuthClientRequest.tokenLocation(provider.getUrl() + "/access_token")
                        .setGrantType(GrantType.AUTHORIZATION_CODE)
                        .setClientId(provider.getClientId())
                        .setClientSecret(provider.getClientSecret())
                        .setRedirectURI(ServletUtils.getServletPathWithHostAsUrlFromRequest(request) + "/" + URL)
                        .setCode(oar.getCode())
                        .buildBodyMessage(); {
                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
                oAuthClient.accessToken(oAuthTokenRequest);
            }
        }
        return false;// Optional.empty();
    }

    private Optional<User> loginUser(String email, String password) throws AuthenticationProviderException {
        if (StringUtils.isNotEmpty(email)) {
            try {
                User user = DataManager.getInstance().getDao().getUserByEmail(email);
                boolean refused = true;
                if (user != null && StringUtils.isNotBlank(password) && user.getPasswordHash() != null
                        && bcrypt.checkpw(password, user.getPasswordHash())) {
                    refused = false;
                }
                return refused ? Optional.empty() : Optional.ofNullable(user);
            } catch (DAOException e) {
                throw new AuthenticationProviderException(e);
            }
        }
        return Optional.empty();
    }

}
