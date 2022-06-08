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
package io.goobi.viewer.model.security.clients;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.NetTools;
import io.goobi.viewer.dao.IDAO;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.managedbeans.ActiveDocumentBean;
import io.goobi.viewer.model.security.clients.ClientApplication.AccessStatus;

/**
 * Class managing registration and log-in of {@link ClientApplication}s 
 * 
 * @author florian
 *
 */
public class ClientApplicationManager {

    private static final Logger logger = LoggerFactory.getLogger(ClientApplicationManager.class);
    
    /**
     * client identifier for the core clientApplication representing all clients
     */
    public static final String GENERAL_CLIENT_IDENTIFIER = "74b2b989-753f-4eea-a3f9-8fa7243f3966";

    public static final String CLIENT_SESSION_ATTRIBUTE = "registered-client";
    public static final String CLIENT_IDENTIFIER_HEADER = "X-goobi-content-protection";
    public static final String CLIENT_RESPONSE_HEADER = "X-goobi-content-protection-status";


    private final IDAO dao;
    private ClientApplication allClients;
    
    /**
     * General constructor 
     * @param dao   The database storing the {@link ClientApplication}s
     * @throws DAOException
     */
    public ClientApplicationManager(IDAO dao) throws DAOException {
        this.dao = dao;
        this.allClients = dao.getClientApplicationByClientId(GENERAL_CLIENT_IDENTIFIER);
    }
    
    /**
     * To be called on server startup. If the database contains no {@link ClientApplication} representing all clients, add it to the database
     * @throws DAOException 
     */
    public void addGeneralClientApplicationToDB() throws DAOException {
        
        ClientApplication client = dao.getClientApplicationByClientId(GENERAL_CLIENT_IDENTIFIER);
        if(client == null) {            
            client = new ClientApplication(GENERAL_CLIENT_IDENTIFIER);
            client.setAccessStatus(AccessStatus.NON_APPLICABLE);
            dao.saveClientApplication(client);
            this.allClients = client;
        }
        
    }
    
    /**
     * Get the {@link ClientApplication} representing all clients created in {@link #addGeneralClientApplicationToDB()}
     * @return the allClients
     */
    public ClientApplication getAllClients() {
        return allClients;
    }
    
    /**
     * Store the given client in the given session to consider it for access condition checks. 
     * The client is only stored if it has {@link AccessStatus#GRANTED} 
     * If the session doesn't contain the client yet, its {@link ClientApplication#getDateLastAccess()} is updated and the client saved to database
     * @param client    the client to register
     * @param session   the session to store the client
     * @return  true if registration was successfull
     */
    public boolean registerClientInSession(ClientApplication client, HttpSession session) {
        if(AccessStatus.GRANTED.equals(client.getAccessStatus())) {    
            if(getClientFromSession(session).isEmpty()) {
                try {
                    client.setDateLastAccess(LocalDateTime.now());
                    dao.saveClientApplication(client);
                } catch (DAOException e) {
                    logger.error("Error updating client in database ",e);
                }
            }
            session.setAttribute(CLIENT_SESSION_ATTRIBUTE, client);
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Get the client stored in the given session by {@link #registerClientInSession(ClientApplication, HttpSession)}, if any
     * @param session   the session possibly containing the client
     * @return  An optional containing the client if one exists
     */
    public static Optional<ClientApplication> getClientFromSession(HttpSession session) {
        Object client = session.getAttribute(CLIENT_SESSION_ATTRIBUTE);
        if(client != null && client instanceof ClientApplication) {
            return Optional.of((ClientApplication)client);
        } else {
            return Optional.empty();
        }
    }
    

    /**
     * Get the client with the given {@link ClientApplication#getClientIdentifier()} from the database
     * @param clientIdentifier
     * @return  An optional containing the client if one matches the identifier
     * @throws DAOException 
     */
    public Optional<ClientApplication> getClientByClientIdentifier(String clientIdentifier) throws DAOException {
        return dao.getAllClientApplications().stream()
                .filter(c -> c.matchesClientIdentifier(clientIdentifier))
                .findAny();
    }

    /**
     * The the client identifier from a request header
     * @param servletRequest
     * @return  The identifier or null if non is in the header
     */
    public static String getClientIdentifier(HttpServletRequest request) {
       return request.getHeader(ClientApplicationManager.CLIENT_IDENTIFIER_HEADER);
    }
    
    /**
     * Create a new {@link ClientApplication} with the given identifier and IP of the given request and store it in the database
     * @param clientIdentifier  the identifier transmitted by the client
     * @param request   the request made by the client
     * @return  The newly persisted client
     * @throws DAOException
     */
    public ClientApplication persistNewClient(String clientIdentifier, HttpServletRequest request) throws DAOException {
        ClientApplication client = new ClientApplication(clientIdentifier);
        client.setAccessStatus(AccessStatus.REQUESTED);
        String ip = NetTools.getIpAddress(request);
        if(StringUtils.isNotBlank(ip)) {
            client.setClientIp(ip);
        }
        if(dao.saveClientApplication(client)) {
            return client;
        } else {
            throw new DAOException("Failed to persist client");
        }

    }
    
    /**
     * check if the given client is the client instance representing all clients
     * @param client
     * @return  true if the client does not represent all clients
     */
    public boolean isNotAllClients(ClientApplication client) {
        return Optional.ofNullable(client).map(ClientApplication::getId).map(id -> id != getAllClients().getId()).orElse(true);
    }
}
