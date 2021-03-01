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
package io.goobi.viewer.api.rest.v1.cms;

import static io.goobi.viewer.api.rest.v1.ApiUrls.CMS_MEDIA;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.GET;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.intranda.api.iiif.presentation.Collection;
import de.unigoettingen.sub.commons.contentlib.exceptions.ContentNotFoundException;
import de.unigoettingen.sub.commons.contentlib.exceptions.IllegalRequestException;
import io.goobi.viewer.api.rest.AbstractApiUrlManager;
import io.goobi.viewer.api.rest.bindings.ViewerRestServiceBinding;
import io.goobi.viewer.api.rest.v1.ApiUrls;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.SolrConstants;
import io.goobi.viewer.controller.SolrSearchIndex;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.exceptions.IndexUnreachableException;
import io.goobi.viewer.exceptions.PresentationException;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.model.cms.CMSCollection;
import io.goobi.viewer.model.cms.CMSSlider;
import io.goobi.viewer.model.iiif.presentation.builder.CollectionBuilder;
import io.goobi.viewer.model.iiif.presentation.builder.ManifestBuilder;

/**
 * @author florian
 *
 */
@javax.ws.rs.Path("/cms/slider/{sliderId}")
@ViewerRestServiceBinding
public class CMSSliderResource {

    private static final Logger logger = LoggerFactory.getLogger(CMSSliderResource.class);
    
    private final CMSSlider slider;

    public CMSSliderResource(@PathParam("sliderId") Long sliderId) throws DAOException {
        this.slider = DataManager.getInstance().getDao().getSlider(sliderId);
    }

    @GET
    @javax.ws.rs.Path("/slides")
    @Produces({ MediaType.APPLICATION_JSON })
    public List<URI> getSlides() throws ContentNotFoundException, PresentationException, IndexUnreachableException, IllegalRequestException {
        if (this.slider != null) {
            switch (slider.getSourceType()) {
                case COLLECTIONS:
                    return getCollectionsAsCollection(slider.getCollections());
                case RECORDS:
                    return getRecordsAsCollection(slider.getSolrQuery());
                default:
                    throw new IllegalRequestException("Cannot create collection for slider " + slider.getName());
            }
        } else {
            throw new ContentNotFoundException("Slider with requested id not found");
        }
    }

    /**
     * @param solrQuery
     * @return
     * @throws IndexUnreachableException
     * @throws PresentationException
     */
    private List<URI> getRecordsAsCollection(String solrQuery) throws PresentationException, IndexUnreachableException {

        List<URI> manifests = new ArrayList<>();
        AbstractApiUrlManager urls = DataManager.getInstance().getRestApiManager().getDataApiManager().orElse(null);
        if(urls == null) {
            return Collections.emptyList();
        } else {            
            SolrDocumentList solrDocs = DataManager.getInstance().getSearchIndex().search(solrQuery, Arrays.asList(SolrConstants.PI));
            for (SolrDocument doc : solrDocs) {
                String pi = (String) SolrSearchIndex.getSingleFieldValue(doc, SolrConstants.PI);
                URI uri = urls.path(ApiUrls.RECORDS_RECORD, ApiUrls.RECORDS_MANIFEST).params(pi).buildURI();
                manifests.add(uri);
            }
            return manifests;
        }
        
    }

    /**
     * @param collections
     * @return
     */
    private List<URI> getCollectionsAsCollection(List<String> collectionNames) {
        List<URI> collections = new ArrayList<>();
        AbstractApiUrlManager urls = DataManager.getInstance().getRestApiManager().getDataApiManager().orElse(null);
        if(urls == null) {
            return Collections.emptyList();
        } else {
            for (String collectionName : collectionNames) {
                String[] nameParts = collectionName.split("/");
                if(nameParts.length != 2) {
                    logger.error("Collection name for slider source has wrong format: " + collectionName);
                    continue;
                }
                String field = nameParts[0];
                String value = nameParts[1];
                URI uri = urls.path(ApiUrls.COLLECTIONS, ApiUrls.COLLECTIONS_COLLECTION).params(field, value).buildURI();
                collections.add(uri);
            }
            return collections;
        }
    }

}
