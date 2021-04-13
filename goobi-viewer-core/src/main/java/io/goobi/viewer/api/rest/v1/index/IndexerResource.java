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
package io.goobi.viewer.api.rest.v1.index;

import static io.goobi.viewer.api.rest.v1.ApiUrls.INDEXER;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.unigoettingen.sub.commons.contentlib.exceptions.IllegalRequestException;
import de.unigoettingen.sub.commons.contentlib.servlet.rest.CORSBinding;
import io.goobi.viewer.api.rest.bindings.ViewerRestServiceBinding;
import io.goobi.viewer.api.rest.model.IndexerVersionRequestParameters;
import io.goobi.viewer.api.rest.model.SuccessMessage;
import io.goobi.viewer.controller.DataManager;

/**
 * Resource for communicating with the indexer process.
 */
@Path(INDEXER)
@ViewerRestServiceBinding
public class IndexerResource {

    private static final Logger logger = LoggerFactory.getLogger(IndexerResource.class);

    @Context
    private HttpServletRequest servletRequest;
    @Context
    private HttpServletResponse servletResponse;

    @PUT
    @Path("/version")
    @Produces({ MediaType.APPLICATION_JSON })
    @Consumes({ MediaType.APPLICATION_JSON })
    public SuccessMessage setIndexerVersion(IndexerVersionRequestParameters params) throws IllegalRequestException {
        try {
            DataManager.getInstance().setIndexerVersion(new ObjectMapper().writeValueAsString(params));
            return new SuccessMessage(true);
        } catch (JsonProcessingException e) {
            logger.error(e.getMessage());
            throw new IllegalRequestException("Cannot parse request body to json ", e);
        }
    }
    
    @GET
    @Path("/version")
    @Produces({ MediaType.APPLICATION_JSON })
    public String getIndexerVersion() {
        return DataManager.getInstance().getIndexerVersion();
    }
}
