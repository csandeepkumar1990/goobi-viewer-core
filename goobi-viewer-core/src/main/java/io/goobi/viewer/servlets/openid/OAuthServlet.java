/*
 * This file is part of the Goobi viewer - a content presentation and management
 * application for digitized objects.
 *
 * Visit these websites for more information.
 *          - http://www.intranda.com
 *          - http://digiverso.com
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.goobi.viewer.servlets.openid;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import org.json.JSONObject;
import org.json.JSONTokener;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

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

    private static final Logger logger = LogManager.getLogger(OAuthServlet.class);

    /** Constant <code>URL="oauth"</code> */
    public static final String URL = "oauth";

    private static final BCrypt bcrypt = new BCrypt();

    /**
     * This future gets fulfilled once the {@link UserBean} has finished setting up the session and redirecting the request. Completing the request
     * should wait after the redirect, otherwise it will not have any effect
     */
    private static Future<Boolean> redirected = null;

    /** {@inheritDoc} */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            doPost(request, response);
        } catch (IOException | ServletException e) {
            logger.error(e.getMessage(), e);
        }
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
                } catch (OAuthSystemException e) {
                    logger.error(e.getMessage(), e);
                    redirected = provider.completeLogin(null, request, response);
                    listener.unregister(provider);
                } catch (AuthenticationProviderException e) {
                    logger.debug("AuthenticationProviderException thrown here: {}", e.getMessage());
                    try {
                        response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    } catch (IOException e1) {
                        logger.error(e1.getMessage());
                    }
                    redirected = provider.completeLogin(null, request, response);
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

        if (redirected != null) {
            try {
                redirected.get(1, TimeUnit.MINUTES); //redirected has an internal timeout, so this get() should never run into a timeout, but you never know
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Waiting for redirect after login unterrupted unexpectedly");
            } catch (TimeoutException e) {
                logger.error("Waiting for redirect after login took longer than a minute. This should not happen. Redirect will not take place");
            } catch (ExecutionException e) {
                logger.error("Unexpected error while waiting for redirect", e);
            }
        }
    }

    /**
     * 
     * @param oar
     * @param provider
     * @param request
     * @param response
     * @return
     * @throws OAuthProblemException
     * @throws OAuthSystemException
     * @throws AuthenticationProviderException
     */
    private static boolean handleResponse(OAuthAuthzResponse oar, OpenIdProvider provider, HttpServletRequest request, HttpServletResponse response)
            throws OAuthProblemException, OAuthSystemException, AuthenticationProviderException {
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
                        .buildBodyMessage();
                return doGoogle(provider, oAuthTokenRequest, request, response);
            case "facebook":
                oAuthTokenRequest = OAuthClientRequest.tokenProvider(OAuthProviderType.FACEBOOK)
                        .setGrantType(GrantType.AUTHORIZATION_CODE)
                        .setClientId(provider.getClientId())
                        .setClientSecret(provider.getClientSecret())
                        .setRedirectURI(ServletUtils.getServletPathWithHostAsUrlFromRequest(request) + "/" + URL)
                        .setCode(oar.getCode())
                        .buildQueryMessage();
                return doTheFaceBook(provider, oAuthTokenRequest, request, response);
            case "openid-test":
                String email = oar.getCode();
                String password = oar.getParam("token");
                Optional<User> user = loginUser(email, password);
                if (user.isPresent()) {
                    JSONObject jsonProfile = new JSONObject(Collections.singletonMap("email", email));
                    redirected = provider.completeLogin(jsonProfile, request, response);
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
                        .buildBodyMessage();
                OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
                oAuthClient.accessToken(oAuthTokenRequest);
        }

        return false;

    }

    /**
     * 
     * @param provider
     * @param oAuthTokenRequest
     * @param request
     * @param response
     * @return
     */
    static boolean doGoogle(OpenIdProvider provider, OAuthClientRequest oAuthTokenRequest, HttpServletRequest request, HttpServletResponse response) {
        OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
        try {
            OAuthAccessTokenResponse oAuthTokenResponse = oAuthClient.accessToken(oAuthTokenRequest);
            if (oAuthTokenResponse != null) {
                TokenValidator tv = new TokenValidator();
                tv.validate(oAuthTokenResponse);
                provider.setoAuthAccessToken(oAuthTokenResponse.getAccessToken());
                String idTokenEncoded = (oAuthTokenResponse.getParam("id_token"));
                String[] idTokenEncodedSplit = idTokenEncoded.split("[.]");
                if (idTokenEncodedSplit.length != 3) {
                    logger.error("Wrong number of segments in id_token. Expected 3, found {}", idTokenEncodedSplit.length);
                    return false;
                }
                String payload = new String(new Base64(true).decode(idTokenEncodedSplit[1]), StandardCharsets.UTF_8);
                JSONTokener tokener = new JSONTokener(payload);
                JSONObject jsonPayload = new JSONObject(tokener);
                redirected = provider.completeLogin(jsonPayload, request, response);
                return true;
            }
        } catch (OAuthSystemException | OAuthProblemException e) {
            logger.error(e.getMessage(), e);
            return false;
        }

        return false;
    }

    /**
     * 
     * @param provider
     * @param oAuthTokenRequest
     * @param request
     * @param response
     * @return
     * @throws OAuthSystemException
     * @throws OAuthProblemException
     */
    static boolean doTheFaceBook(OpenIdProvider provider, OAuthClientRequest oAuthTokenRequest, HttpServletRequest request,
            HttpServletResponse response) {
        OAuthClient oAuthClient = new OAuthClient(new URLConnectionClient());
        try {
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
                    JSONTokener tokener = new JSONTokener(resourceResponse.getBody());
                    JSONObject jsonProfile = new JSONObject(tokener);
                    redirected = provider.completeLogin(jsonProfile, request, response);
                    return true;
                }
            }
        } catch (OAuthSystemException | OAuthProblemException e) {
            logger.error(e.getMessage(), e);
            return false;
        }

        return false;
    }

    /**
     * 
     * @param email
     * @param password
     * @return
     * @throws AuthenticationProviderException
     */
    private static Optional<User> loginUser(String email, String password) throws AuthenticationProviderException {
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
