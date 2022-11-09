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
package io.goobi.viewer.model.security.authentication;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.model.security.user.User;

public class HttpHeaderProvider extends HttpAuthenticationProvider {

    private static final Logger logger = LogManager.getLogger(HttpHeaderProvider.class);

    /** Constant <code>TYPE_OPENID="openId"</code> */
    public static final String TYPE_HTTP_HEADER = "httpHeader";

    public static final String PARAMETER_TYPE_HEADER = "header";

    private final String parameterType;
    private final String parameterName;

    /**
     * 
     * @param name
     * @param label
     * @param url
     * @param image
     * @param timeoutMillis
     * @param parameterType
     * @param parameterName
     */
    public HttpHeaderProvider(String name, String label, String url, String image, long timeoutMillis, String parameterType, String parameterName) {
        super(name, label, TYPE_HTTP_HEADER, url, image, timeoutMillis);
        this.parameterType = parameterType;
        this.parameterName = parameterName;
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.authentication.IAuthenticationProvider#login(java.lang.String, java.lang.String)
     */
    @Override
    public CompletableFuture<LoginResult> login(String ssoId, String password) throws AuthenticationProviderException {
        HttpServletRequest request = BeanUtils.getRequest();
        HttpServletResponse response = BeanUtils.getResponse();

        try {
            List<User> users = DataManager.getInstance().getDao().getUsersByPropertyValue(parameterName, ssoId);
            if (users.size() == 1) {
                return CompletableFuture.completedFuture(new LoginResult(request, response, Optional.of(users.get(0)), false));
            } else if (users.size() > 1) {
                logger.error("SSO ID found on multiple users: {}", ssoId);
            }
            logger.trace("No user found for {}={}", parameterName, ssoId);
            // TODO add user to db?
        } catch (DAOException e) {
            logger.error(e.getMessage(), e);
        }

        return CompletableFuture.completedFuture(new LoginResult(request, response, Optional.empty(), true));
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.authentication.IAuthenticationProvider#logout()
     */
    @Override
    public void logout() throws AuthenticationProviderException {
        // noop
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.authentication.IAuthenticationProvider#allowsPasswordChange()
     */
    @Override
    public boolean allowsPasswordChange() {
        return false;
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.authentication.IAuthenticationProvider#allowsNicknameChange()
     */
    @Override
    public boolean allowsNicknameChange() {
        return false;
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.authentication.IAuthenticationProvider#allowsEmailChange()
     */
    @Override
    public boolean allowsEmailChange() {
        return false;
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.authentication.IAuthenticationProvider#getAddUserToGroups()
     */
    @Override
    public List<String> getAddUserToGroups() {
        return Collections.emptyList();
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.authentication.IAuthenticationProvider#setAddUserToGroups(java.util.List)
     */
    @Override
    public void setAddUserToGroups(List<String> addUserToGroups) {
        // noop

    }

    /**
     * @return the parameterType
     */
    public String getParameterType() {
        return parameterType;
    }

    /**
     * @return the parameterName
     */
    public String getParameterName() {
        return parameterName;
    }
}
