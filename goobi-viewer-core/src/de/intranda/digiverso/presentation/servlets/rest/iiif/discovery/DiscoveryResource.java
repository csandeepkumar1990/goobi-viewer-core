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
package de.intranda.digiverso.presentation.servlets.rest.iiif.discovery;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import de.intranda.digiverso.presentation.controller.SolrConstants;
import de.intranda.digiverso.presentation.exceptions.IndexUnreachableException;
import de.intranda.digiverso.presentation.exceptions.PresentationException;
import de.intranda.digiverso.presentation.model.iiif.OrderedCollection;
import de.intranda.digiverso.presentation.model.iiif.OrderedCollectionPage;
import de.intranda.digiverso.presentation.model.iiif.discovery.Activity;
import de.intranda.digiverso.presentation.model.iiif.discovery.ActivityCollectionBuilder;
import de.intranda.digiverso.presentation.servlets.rest.ViewerRestServiceBinding;

/**
 * Provides REST services according to the IIIF discovery API specfication (https://iiif.io/api/discovery/0.1/).
 * This class implements two resources:
 *  <ul>
 *      <li>{@link #getAllChanges() /iiif/discovery/activities/}</li>
 *      <li>{@link #getPage(int) /iiif/discovery/activities/&lt;pageNo&gt;/}</li>
 *  </ul> 
 *  
 *  This service supports activity types UPDATE, CREATE and DELETE. They are created from the SOLR fields 
 *  DATEUPDATED, DATECREATED AND DATEDELETED respectively
 * 
 * @author Florian Alpers
 *
 */
@Path("/iiif/discovery")
@ViewerRestServiceBinding
public class DiscoveryResource {
    
    private final static String[] CONTEXT = { "http://iiif.io/api/discovery/0/context.json", "https://www.w3.org/ns/activitystreams" };
    
    @Context
    protected HttpServletRequest servletRequest;
    @Context
    protected HttpServletResponse servletResponse;

    /**
     * Provides a view of the entire list of all activities by linking to the first and last page of the collection.
     * The pages contain the actual activity entries and are provided by {@link #getPage(int) /iiif/discovery/activities/&lt;pageNo&gt;/}.
     * This resource also contains a count of the total number of activities
     * 
     * @return  An {@link OrderedCollection} of {@link Activity Activities}
     * @throws PresentationException       If the contained Solr query is faulty (due to configuration errors)
     * @throws IndexUnreachableException   If the Solr index cannot be queried
     */
    @GET
    @Path("/activities")
    @Produces({ MediaType.APPLICATION_JSON })
    public OrderedCollection<Activity> getAllChanges() throws PresentationException, IndexUnreachableException {
        ActivityCollectionBuilder builder = new ActivityCollectionBuilder(servletRequest);
        OrderedCollection<Activity> collection = builder.buildCollection();
        collection.setContext(CONTEXT);
        return collection;
    }

    
    /**
     * Provides a partial list of {@link Activity Activities} along with links to the preceding and succeeding page 
     * as well as the parent collection as provided by {@link #getAllChanges() /iiif/discovery/activities/}
     * The number of Activities on the page is determined by 
     * {@link de.intranda.digiverso.presentation.controller.Configuration#getIIIFDiscoveryAvtivitiesPerPage() Configuration#getIIIFDiscoveryAvtivitiesPerPage()}
     * 
     * @param pageNo    The page number, starting with 0
     * @return  An {@link OrderedCollectionPage} of {@link Activity Activities}
     * @throws PresentationException       If the contained Solr query is faulty (due to configuration errors)
     * @throws IndexUnreachableException   If the Solr index cannot be queried
     */
    @GET
    @Path("/activities/{pageNo}")
    @Produces({ MediaType.APPLICATION_JSON })
    public OrderedCollectionPage<Activity> getPage(@PathParam("pageNo") int pageNo) throws PresentationException, IndexUnreachableException {
        ActivityCollectionBuilder builder = new ActivityCollectionBuilder(servletRequest);
        OrderedCollectionPage<Activity> page = builder.buildPage(pageNo);
        page.setContext(CONTEXT);
        return page;
    }
}