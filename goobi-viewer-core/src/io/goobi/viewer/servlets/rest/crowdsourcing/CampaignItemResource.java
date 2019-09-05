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
package io.goobi.viewer.servlets.rest.crowdsourcing;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.intranda.api.annotation.wa.WebAnnotation;
import de.unigoettingen.sub.commons.contentlib.servlet.rest.CORSBinding;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.dao.IDAO;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.exceptions.streams.Try;
import io.goobi.viewer.model.annotation.PersistentAnnotation;
import io.goobi.viewer.model.crowdsourcing.campaigns.Campaign;
import io.goobi.viewer.model.crowdsourcing.campaigns.CampaignItem;
import io.goobi.viewer.model.crowdsourcing.questions.Question;
import io.goobi.viewer.model.crowdsourcing.questions.QuestionType;
import io.goobi.viewer.model.crowdsourcing.questions.TargetSelector;
import io.goobi.viewer.model.iiif.presentation.builder.ManifestBuilder;
import io.goobi.viewer.servlets.rest.ViewerRestServiceBinding;
import io.goobi.viewer.servlets.utils.ServletUtils;

/**
 * @author florian
 *
 */
@Path("/crowdsourcing/campaign")
@ViewerRestServiceBinding
public class CampaignItemResource {

    private static final Logger logger = LoggerFactory.getLogger(CampaignItemResource.class);

    @Context
    private HttpServletRequest servletRequest;
    @Context
    private HttpServletResponse servletResponse;

    private final URI requestURI;

    public CampaignItemResource() {
        this.requestURI = URI.create(DataManager.getInstance().getConfiguration().getRestApiUrl());
    }

    @GET
    @Path("/{campaignId}/annotate/{pi}")
    @Produces({ MediaType.APPLICATION_JSON })
    @CORSBinding
    public CampaignItem getItemForManifest(@PathParam("campaignId") Long campaignId, @PathParam("pi") String pi) throws URISyntaxException {
        URI servletURI = URI.create(ServletUtils.getServletPathWithHostAsUrlFromRequest(servletRequest));
        URI manifestURI = new ManifestBuilder(servletURI, requestURI).getManifestURI(pi);

        //TODO: Create item from campaign
        CampaignItem item = new CampaignItem();
        item.setCampaign(new Campaign(Locale.GERMAN));
        item.getCampaign().setId(42l);
        item.setSource(manifestURI);

        Question question = new Question(QuestionType.PLAINTEXT, TargetSelector.RECTANGLE, 0, item.getCampaign());
        question.setId(1l);
        question.setText("de", "Wählen Sie einen Bildbereich aus und geben Sie eine kurze Beschreibung dazu ein.");
        question.setText("en", "Select an area in the image and enter a short description about it.");
        item.addQuestion(question);

        Question comment = new Question(QuestionType.PLAINTEXT, TargetSelector.WHOLE_PAGE, 1, item.getCampaign());
        comment.setId(2l);
        comment.setText("de", "Hier ist Platz für Anmerkungen zu den Annotationen dieser Seite");
        comment.setText("en", "This is a space for notes about the annotations on this page");
        item.addQuestion(comment);

        return item;
    }

    /**
     * Takes a map of annotation target (canvas/manifest) ids and replaces all annotations for the given campaign, pi and targeted pages (if target is
     * canvas) with the ones contained in the map
     * 
     * @param map
     * @param campaignId
     * @param pi
     * @throws URISyntaxException
     * @throws DAOException
     */
    @PUT
    @Path("/{campaignId}/annotate/{pi}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @CORSBinding
    public void setAnnotationsForManifest(List<Entry> map, @PathParam("campaignId") Long campaignId, @PathParam("pi") String pi)
            throws URISyntaxException, DAOException {

        IDAO dao = DataManager.getInstance().getDao();
        Campaign campaign = dao.getCampaign(campaignId);

        for (Entry entry : map) {
            URI targetURI = URI.create(entry.getId());
            Integer pageOrder = PersistentAnnotation.parsePageOrder(targetURI);
            List<PersistentAnnotation> existingAnnotations = dao.getAnnotationsForCampaignAndTarget(campaign, pi, pageOrder);
            List<PersistentAnnotation> newAnnotations = entry.annotations.stream().map(PersistentAnnotation::new).collect(Collectors.toList());

            //delete existing annotations not in the new annotations list
            List persistenceExceptions = existingAnnotations.stream()
                    .filter(anno -> newAnnotations.stream().noneMatch(annoNew -> anno.getId().equals(annoNew.getId())))
                    .map(Try.lift(dao::deleteAnnotation))
                    .filter(t -> t.isException())
                    .map(t -> t.getException().get())
                    .collect(Collectors.toList());
            for (Object exception : persistenceExceptions) {
                logger.error("Error deleting annotation " + exception.toString());
            }

            //add entirely new annotations
            persistenceExceptions = newAnnotations.stream()
                    .filter(anno -> anno.getId() == null)
                    .map(Try.lift(dao::addAnnotation))
                    .filter(either -> either.isException())
                    .map(either -> either.getException().get())
                    .collect(Collectors.toList());
            for (Object exception : persistenceExceptions) {
                logger.error("Error adding annotation " + exception.toString());
            }

            //update changed annotations
            persistenceExceptions = newAnnotations.stream()
                    .filter(anno -> anno.getId() != null)
                    .map(Try.lift(dao::updateAnnotation))
                    .filter(either -> either.isException())
                    .map(either -> either.getException().get())
                    .collect(Collectors.toList());
            for (Object exception : persistenceExceptions) {
                logger.error("Error updating annotation " + exception.toString());
            }
        }
    }

    public static class Entry {
        private String id;
        private List<WebAnnotation> annotations;

        /**
         * @return the id
         */
        public String getId() {
            return id;
        }

        /**
         * @param id the id to set
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * @return the annotations
         */
        public List<WebAnnotation> getAnnotations() {
            return annotations;
        }

        /**
         * @param annotations the annotations to set
         */
        public void setAnnotations(List<WebAnnotation> annotations) {
            this.annotations = annotations;
        }
    }

}
