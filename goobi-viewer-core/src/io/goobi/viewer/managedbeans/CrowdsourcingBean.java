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
package io.goobi.viewer.managedbeans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.context.FacesContext;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang.StringUtils;
import org.apache.solr.client.solrj.response.FacetField.Count;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ocpsoft.pretty.PrettyContext;
import com.ocpsoft.pretty.faces.url.URL;

import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.Helper;
import io.goobi.viewer.controller.SolrConstants;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.exceptions.IndexUnreachableException;
import io.goobi.viewer.exceptions.PresentationException;
import io.goobi.viewer.exceptions.RecordNotFoundException;
import io.goobi.viewer.exceptions.ViewerConfigurationException;
import io.goobi.viewer.managedbeans.tabledata.TableDataFilter;
import io.goobi.viewer.managedbeans.tabledata.TableDataProvider;
import io.goobi.viewer.managedbeans.tabledata.TableDataProvider.SortOrder;
import io.goobi.viewer.managedbeans.tabledata.TableDataSource;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.messages.Messages;
import io.goobi.viewer.messages.ViewerResourceBundle;
import io.goobi.viewer.model.annotation.PersistentAnnotation;
import io.goobi.viewer.model.crowdsourcing.campaigns.Campaign;
import io.goobi.viewer.model.crowdsourcing.campaigns.Campaign.CampaignVisibility;
import io.goobi.viewer.model.crowdsourcing.campaigns.CampaignRecordStatistic.CampaignRecordStatus;
import io.goobi.viewer.model.crowdsourcing.questions.Question;
import io.goobi.viewer.model.security.user.User;

@Named
@SessionScoped
public class CrowdsourcingBean implements Serializable {

    private static final long serialVersionUID = -6452528640177147828L;

    private static final Logger logger = LoggerFactory.getLogger(CrowdsourcingBean.class);

    private static final int DEFAULT_ROWS_PER_PAGE = 15;

    @Inject
    private NavigationHelper navigationHelper;
    @Inject
    private UserBean userBean;

    private TableDataProvider<Campaign> lazyModelCampaigns;
    private TableDataProvider<PersistentAnnotation> lazyModelAnnotations;

    /**
     * The campaign selected in backend
     */
    private Campaign selectedCampaign;
    /**
     * The campaign being annotated/reviewed
     */
    private Campaign targetCampaign = null;
    /**
     * The identifier (PI) of the work currently targeted by this campaign
     */
    private String targetIdentifier;
    /**
     * true if the campaign is an existing campaign currently edited in the viewer-backend; false otherwise
     */
    private boolean editMode = false;

    /**
     * Initialize all campaigns as lazily loaded list
     */
    @PostConstruct
    public void init() {
        if (lazyModelCampaigns == null) {
            lazyModelCampaigns = new TableDataProvider<>(new TableDataSource<Campaign>() {

                private Optional<Long> numCreatedPages = Optional.empty();

                @Override
                public List<Campaign> getEntries(int first, int pageSize, String sortField, SortOrder sortOrder, Map<String, String> filters) {
                    try {
                        if (StringUtils.isBlank(sortField)) {
                            sortField = "id";
                        }

                        List<Campaign> ret =
                                DataManager.getInstance().getDao().getCampaigns(first, pageSize, sortField, sortOrder.asBoolean(), filters);
                        return ret;
                    } catch (DAOException e) {
                        logger.error("Could not initialize lazy model: {}", e.getMessage());
                    }

                    return Collections.emptyList();
                }

                @Override
                public long getTotalNumberOfRecords(Map<String, String> filters) {
                    if (!numCreatedPages.isPresent()) {
                        try {
                            numCreatedPages = Optional.ofNullable(DataManager.getInstance().getDao().getCampaignCount(filters));
                        } catch (DAOException e) {
                            logger.error("Unable to retrieve total number of campaigns", e);
                        }
                    }
                    return numCreatedPages.orElse(0l);
                }

                @Override
                public void resetTotalNumberOfRecords() {
                    numCreatedPages = Optional.empty();
                }
            });
            lazyModelCampaigns.setEntriesPerPage(DEFAULT_ROWS_PER_PAGE);
            //            lazyModelCampaigns.addFilter("CMSPageLanguageVersion", "title_menuTitle");
            //            lazyModelCampaigns.addFilter("classifications", "classification");
        }

        if (lazyModelAnnotations == null) {
            lazyModelAnnotations = new TableDataProvider<>(new TableDataSource<PersistentAnnotation>() {

                private Optional<Long> numCreatedPages = Optional.empty();

                @Override
                public List<PersistentAnnotation> getEntries(int first, int pageSize, String sortField, SortOrder sortOrder,
                        Map<String, String> filters) {
                    try {
                        if (StringUtils.isBlank(sortField)) {
                            sortField = "id";
                        }

                        List<PersistentAnnotation> ret =
                                DataManager.getInstance().getDao().getAnnotations(first, pageSize, sortField, sortOrder.asBoolean(), filters);
                        return ret;
                    } catch (DAOException e) {
                        logger.error("Could not initialize lazy model: {}", e.getMessage());
                    }

                    return Collections.emptyList();
                }

                @Override
                public long getTotalNumberOfRecords(Map<String, String> filters) {
                    if (!numCreatedPages.isPresent()) {
                        try {
                            numCreatedPages = Optional.ofNullable(DataManager.getInstance().getDao().getAnnotationCount(filters));
                        } catch (DAOException e) {
                            logger.error("Unable to retrieve total number of campaigns", e);
                        }
                    }
                    return numCreatedPages.orElse(0l);
                }

                @Override
                public void resetTotalNumberOfRecords() {
                    numCreatedPages = Optional.empty();
                }
            });
            lazyModelAnnotations.setEntriesPerPage(DEFAULT_ROWS_PER_PAGE);
            //            lazyModelCampaigns.addFilter("CMSPageLanguageVersion", "title_menuTitle");
            //            lazyModelCampaigns.addFilter("classifications", "classification");
        }
    }

    /**
     * 
     * @param visibility
     * @return  The total number of campaigns of a certain {@link CampaignVisibility}
     * @throws DAOException
     */
    public long getCampaignCount(CampaignVisibility visibility) throws DAOException {
        Map<String, String> filters = visibility != null ? Collections.singletonMap("visibility", visibility.name()) : null;
        return DataManager.getInstance().getDao().getCampaignCount(filters);
    }

    /**
     * Filter the loaded campaigns by {@link CampaignVisibility}
     * 
     * @param visibility
     * @return
     */
    public String filterCampaignsAction(CampaignVisibility visibility) {
        lazyModelCampaigns.resetFilters();
        if (visibility != null) {
            lazyModelCampaigns.addFilter(new TableDataFilter("visibility", visibility.name()));
        }

        return "";
    }

    /**
     * @return  A list of all locales supported by this viewer application
     */
    public static List<Locale> getAllLocales() {
        List<Locale> list = new LinkedList<>();
        list.add(ViewerResourceBundle.getDefaultLocale());
        if (FacesContext.getCurrentInstance() != null && FacesContext.getCurrentInstance().getApplication() != null) {
            Iterator<Locale> iter = FacesContext.getCurrentInstance().getApplication().getSupportedLocales();
            while (iter.hasNext()) {
                Locale locale = iter.next();
                if (!list.contains(locale)) {
                    list.add(locale);
                }
            }
        }
        return list;
    }

    /**
     * Sets a new {@link Campaign} as the {@link #selectedCampaign} and returns a pretty url to the view for creating a new campaign
     * @return
     */
    public String createNewCampaignAction() {
        selectedCampaign = new Campaign(ViewerResourceBundle.getDefaultLocale());
        return "pretty:adminCrowdAddCampaign";
    }

    /**
     * Sets the given {@link Campaign} as the {@link #selectedCampaign} and returns a pretty url to the view for editing this campaign
     * @return
     */
    public String editCampaignAction(Campaign campaign) {
        selectedCampaign = campaign;
        return "pretty:adminCrowdEditCampaign";
    }

    /**
     * Delete the given {@link Campaign} from the database and the loaded list of campaigns
     * 
     * @param campaign
     * @return
     * @throws DAOException
     */
    public String deleteCampaignAction(Campaign campaign) throws DAOException {
        if (campaign != null) {
            if (DataManager.getInstance().getDao().deleteCampaign(campaign)) {
                Messages.info("admin__crowdsoucing_campaign_deleteSuccess");
                lazyModelCampaigns.update();
            }
        }

        return "";
    }

    /**
     * Add a new {@link Question} to the selected campaign
     * 
     * @return
     */
    public String addNewQuestionAction() {
        if (selectedCampaign != null) {
            selectedCampaign.getQuestions().add(new Question(selectedCampaign));
            selectedCampaign.setDirty(true);
        }

        return "";
    }

    /**
     * Remove the given {@link Question} from the selected campaign
     * 
     * @return
     */
    public String removeQuestionAction(Question question) {
        if (selectedCampaign != null && question != null) {
            selectedCampaign.getQuestions().remove(question);
            selectedCampaign.setDirty(true);
        }

        return "";
    }

    /**
     * Resets dateStart + dateEnd to null.
     * 
     * @return
     */
    public String resetDurationAction() {
        if (selectedCampaign != null) {
            selectedCampaign.setDateStart(null);
            selectedCampaign.setDateEnd(null);
        }

        return "";
    }

    /**
     * @return  All campaigns from the database
     * @throws DAOException
     */
    public List<Campaign> getAllCampaigns() throws DAOException {
        List<Campaign> pages = DataManager.getInstance().getDao().getAllCampaigns();
        return pages;
    }

    /**
     * 
     * @param visibility
     * @return  All camapaigns of the given {@link CampaignVisibility} from the database
     * @throws DAOException
     */
    public List<Campaign> getAllCampaigns(CampaignVisibility visibility) throws DAOException {
        List<Campaign> pages = DataManager.getInstance()
                .getDao()
                .getAllCampaigns()
                .stream()
                .filter(camp -> visibility.equals(camp.getVisibility()))
                .collect(Collectors.toList());
        return pages;
    }

    /**
     * Returns the list of campaigns that are visible to the given user.
     * 
     * @param user
     * @return list of campaigns visible to the given user; only public campaigns if user is null
     * @throws DAOException
     */
    public List<Campaign> getAllowedCampaigns(User user) throws DAOException {
        logger.trace("getAllowedCampaigns");
        List<Campaign> allCampaigns = getAllCampaigns();
        if (allCampaigns.isEmpty()) {
            logger.trace("No campaigns found");
            return Collections.emptyList();
        }
        // Logged in
        if (user != null) {
            return user.getAllowedCrowdsourcingCampaigns(allCampaigns);
        }

        // Not logged in - only public campaigns
        List<Campaign> ret = new ArrayList<>(allCampaigns.size());
        for (Campaign campaign : allCampaigns) {
            if (CampaignVisibility.PUBLIC.equals(campaign.getVisibility())) {
                ret.add(campaign);
            }
        }

        return ret;
    }

    /**
     * Check if the given user is allowed access to the given campaign from a rights management standpoint alone. If the user is null, access is
     * granted for public campaigns only, otherwise access is granted if the user has the appropriate rights
     * 
     * @param user
     * @param campaign
     * @return
     * @throws DAOException
     */
    public boolean isAllowed(User user, Campaign campaign) throws DAOException {
        if (CampaignVisibility.PUBLIC.equals(campaign.getVisibility())) {
            return true;
        } else if (user != null) {
            return !user.getAllowedCrowdsourcingCampaigns(Collections.singletonList(campaign)).isEmpty();
        } else {
            return false;
        }
    }

    /**
     * Adds the current page to the database, if it doesn't exist or updates it otherwise
     *
     * @throws DAOException
     * @throws IndexUnreachableException
     * @throws PresentationException
     *
     */
    public void saveSelectedCampaign() throws DAOException, PresentationException, IndexUnreachableException {
        logger.trace("saveSelectedCampaign");
        try {
            if (userBean == null || !userBean.getUser().isSuperuser()) {
                // Only authorized admins may save
                return;
            }
            if (selectedCampaign == null) {
                return;
            }

            // Save
            boolean success = false;
            Date now = new Date();
            if (selectedCampaign.getDateCreated() == null) {
                selectedCampaign.setDateCreated(now);
            }
            selectedCampaign.setDateUpdated(now);
            if (selectedCampaign.getId() != null) {
                success = DataManager.getInstance().getDao().updateCampaign(selectedCampaign);
            } else {
                success = DataManager.getInstance().getDao().addCampaign(selectedCampaign);
            }
            if (success) {
                selectedCampaign.setDirty(false);
                Messages.info("admin__crowdsourcing_campaign_save_success");
                setSelectedCampaign(selectedCampaign);
                lazyModelCampaigns.update();
                // Update the map of active campaigns for record identifiers (in case a new Solr query changes the set)
                updateActiveCampaigns();
            } else {
                Messages.error("admin__crowdsourcing_campaign_save_failure");
            }
        } finally {
        }
    }

    /**
     *
     * @return root URL for the permalink value
     */
    public String getCampaignsRootUrl() {
        return navigationHelper.getApplicationUrl() + "campaigns/";
    }

    /**
     * @return the lazyModelCampaigns
     */
    public TableDataProvider<Campaign> getLazyModelCampaigns() {
        return lazyModelCampaigns;
    }

    /**
     * @return the lazyModelAnnotations
     */
    public TableDataProvider<PersistentAnnotation> getLazyModelAnnotations() {
        return lazyModelAnnotations;
    }

    /**
     * Deletes given annotation.
     * 
     * @param annotation
     * @return empty string
     * @throws DAOException
     */
    public String deleteAnnotationAction(PersistentAnnotation annotation) throws DAOException {
        if (annotation != null) {
            if (DataManager.getInstance().getDao().deleteAnnotation(annotation)) {
                try {
                    if (annotation.deleteExportedTextFiles() > 0) {
                        try {
                            Helper.reIndexRecord(annotation.getTargetPI());
                            logger.debug("Re-indexing record: {}", annotation.getTargetPI());
                        } catch (RecordNotFoundException e) {
                            logger.error(e.getMessage());
                        }
                    }
                } catch (ViewerConfigurationException e) {
                    logger.error(e.getMessage());
                    Messages.error(e.getMessage());
                }

                Messages.info("admin__crowdsoucing_annotation_deleteSuccess");
                lazyModelCampaigns.update();
            }
        }

        return "";
    }

    /**
     * @return the selectedCampaign
     */
    public Campaign getSelectedCampaign() {
        return selectedCampaign;
    }

    /**
     * @param selectedCampaign the selectedCampaign to set
     */
    public void setSelectedCampaign(Campaign selectedCampaign) {
        this.selectedCampaign = selectedCampaign;
    }

    /**
     * @return the editMode
     */
    public boolean isEditMode() {
        return editMode;
    }

    /**
     * @param editMode the editMode to set
     */
    public void setEditMode(boolean editMode) {
        this.editMode = editMode;
    }

    /**
     * 
     * @return  The id of the {@link CrowdsourcingBean#selectedCampaign} as String
     */
    public String getSelectedCampaignId() {
        Long id = Optional.ofNullable(getSelectedCampaign()).map(Campaign::getId).orElse(null);
        return id == null ? null : id.toString();
    }

    /**
     *  Set the  {@link CrowdsourcingBean#selectedCampaign} by a String containing the campaign id
     * 
     * @param id
     * @throws DAOException
     */
    public void setSelectedCampaignId(String id) throws DAOException {
        if (id != null) {
            Campaign campaign = DataManager.getInstance().getDao().getCampaign(Long.parseLong(id));
            setSelectedCampaign(campaign);
        } else {
            setSelectedCampaign(null);
        }
    }

    /**
     * @return the {@link #targetCampaign}
     */
    public Campaign getTargetCampaign() {
        return targetCampaign;
    }

    /**
     * @param targetCampaign the targetCampaign to set
     */
    public void setTargetCampaign(Campaign targetCampaign) {
        if (this.targetCampaign != targetCampaign) {
            resetTarget();
        }
        this.targetCampaign = targetCampaign;
    }

    /**
     * @return the identifier of the {@link #targetCampaign}
     */
    public String getTargetCampaignId() {
        Long id = Optional.ofNullable(getTargetCampaign()).map(Campaign::getId).orElse(null);
        return id == null ? null : id.toString();
    }

    /**
     * @param targetCampaign the targetCampaign to set
     * @throws DAOException
     * @throws NumberFormatException
     */
    public void setTargetCampaignId(String id) throws NumberFormatException, DAOException {
        if (id != null) {
            Campaign campaign = DataManager.getInstance().getDao().getCampaign(Long.parseLong(id));
            setTargetCampaign(campaign);
        } else {
            setTargetCampaign(null);
        }
    }

    /**
     * Sets the {@link #targetIdentifier} to a random identifier/pi for the {@link #targetCampaign} which is eligible for annotating
     * 
     * @throws PresentationException
     * @throws IndexUnreachableException
     */
    public void setRandomIdentifierForAnnotation() throws PresentationException, IndexUnreachableException {
        if (getTargetCampaign() != null) {
            String pi = getTargetCampaign().getRandomizedTarget(CampaignRecordStatus.ANNOTATE, getTargetIdentifier());
            setTargetIdentifier(pi);

        }
    }

    /**
     * Sets the {@link #targetIdentifier} to a  random identifier/pi for the {@link #targetCampaign} which is eligible for reviewing
     * 
     * @throws PresentationException
     * @throws IndexUnreachableException
     */
    public void setRandomIdentifierForReview() throws PresentationException, IndexUnreachableException {
        if (getTargetCampaign() != null) {
            String pi = getTargetCampaign().getRandomizedTarget(CampaignRecordStatus.REVIEW, getTargetIdentifier());
            setTargetIdentifier(pi);

        }
    }

    /**
     * removes the target identifier (pi) from the bean, so that pi can be targeted again by random target resolution
     */
    public void resetTarget() {
        setTargetIdentifier(null);
    }

    /**
     * 
     * @return the pretty url to annotatate the {@link #targetIdentifier} by the {@link #targetCampaign}
     * 
     */
    public String forwardToAnnotationTarget() {
        return "pretty:crowdCampaignAnnotate2";
    }

    /**
     * 
     * @return the pretty url to review the {@link #targetIdentifier} for the {@link #targetCampaign}
     * 
     */
    public String forwardToReviewTarget() {
        return "pretty:crowdCampaignReview2";
    }

    /**
     * @return the PI of a work selected for editing
     */
    public String getTargetIdentifier() {
        return this.targetIdentifier;
    }

    /**
     * @return the PI of a work selected for editing or "-" if no targetIdentifier exists
     */
    public String getTargetIdentifierForUrl() {
        return StringUtils.isBlank(this.targetIdentifier) ? "-" : this.targetIdentifier;
    }

    public void setTargetIdentifierForUrl(String pi) {
        this.targetIdentifier = "-".equals(pi) ? null : pi;
    }

    /**
     * @param targetIdentifier the targetIdentifier to set
     */
    public void setTargetIdentifier(String targetIdentifier) {
        this.targetIdentifier = targetIdentifier;
    }

    /**
     * 
     * @param campaign
     * @return a pretty url to annotate a random work with the given {@link Campaign}
     */
    public String forwardToCrowdsourcingAnnotation(Campaign campaign) {
        setTargetCampaign(campaign);
        return "pretty:crowdCampaignAnnotate1";
    }

    /**
     * 
     * @param campaign
     * @return a pretty url to review a random work with the given {@link Campaign}
     */
    public String forwardToCrowdsourcingReview(Campaign campaign) {
        setTargetCampaign(campaign);
        return "pretty:crowdCampaignReview1";
    }

    /**
     * 
     * @param campaign
     * @param pi
     * @return  a pretty url to annotate the work with the given pi with the given {@link Campaign}
     */
    public String forwardToCrowdsourcingAnnotation(Campaign campaign, String pi) {
        setTargetCampaign(campaign);
        setTargetIdentifier(pi);
        return "pretty:crowdCampaignAnnotate2";
    }

    /**
     * 
     * @param campaign
     * @param pi
     * @return  a pretty url to review the work with the given pi with the given {@link Campaign}
     */
    public String forwardToCrowdsourcingReview(Campaign campaign, String pi) {
        setTargetCampaign(campaign);
        setTargetIdentifier(pi);
        return "pretty:crowdCampaignReview2";
    }

    /**
     * @param campaign  The campaign with which to annotate/review
     * @param status    if {@link CampaignRecordStatus#REVIEW}, return a url for reviewing, otherwise for annotating
     * @return  The pretty url to either review or annotate a random work with the given {@link Campaign}
     */
    public String getRandomItemUrl(Campaign campaign, CampaignRecordStatus status) {
        String mappingId = CampaignRecordStatus.REVIEW.equals(status) ? "crowdCampaignReview1" : "crowdCampaignAnnotate1";
        URL mappedUrl = PrettyContext.getCurrentInstance().getConfig().getMappingById(mappingId).getPatternParser().getMappedURL(campaign.getId());
        logger.debug("Mapped URL " + mappedUrl);
        return BeanUtils.getServletPathWithHostAsUrlFromJsfContext() + mappedUrl.toString();
    }

    /**
     * 
     * @return the {@link CampaignRecordStatus} of the {@link #targetCampaign} for the {@link #targetIdentifier}
     */
    public CampaignRecordStatus getTargetRecordStatus() {
        if (getTargetCampaign() != null && StringUtils.isNotBlank(getTargetIdentifier())) {
            return getTargetCampaign().getRecordStatus(getTargetIdentifier());
        }
        return null;
    }

    /**
     * 
     * @return the pretty URL to the crowdsourcing campaigns page if {@link UserBean#getUser()} is not eligible for viewing the {@link #targetCampaign}
     */
    public String handleInvalidTarget() {
        if (StringUtils.isBlank(getTargetIdentifier()) || "-".equals(getTargetIdentifier())) {
            return "pretty:crowdCampaigns";
        } else if (getTargetCampaign() == null) {
            return "pretty:crowdCampaigns";
        } else if (CampaignRecordStatus.FINISHED.equals(getTargetRecordStatus())) {
            return "pretty:crowdCampaigns";
        } else if (getTargetCampaign().isHasEnded() || !getTargetCampaign().isHasStarted()) {
            return "pretty:crowdCampaigns";
        } else
            try {
                if (userBean == null || !isAllowed(userBean.getUser(), getTargetCampaign())) {
                    return "pretty:crowdCampaigns";
                } else {
                    return "";
                }
            } catch (DAOException e) {
                logger.error(e.toString(), e);
                return "";
            }
    }

    /**
     * Returns a list of active campaigns for the given identifier that are visible to the current user.
     * 
     * @return List of campaigns
     * @throws IndexUnreachableException
     * @throws PresentationException
     * @throws DAOException
     */
    public List<Campaign> getActiveCampaignsForRecord(String pi) throws DAOException, PresentationException, IndexUnreachableException {
        logger.trace("getActiveCampaignsForRecord: {}", pi);
        if (pi == null) {
            return Collections.emptyList();
        }

        // If the map has not yet been initialized during the application's life cycle, make it so
        if (DataManager.getInstance().getRecordCampaignMap() == null) {
            updateActiveCampaigns();
        }

        List<Campaign> allActiveCampaigns = DataManager.getInstance().getRecordCampaignMap().get(pi);
        if (allActiveCampaigns == null || allActiveCampaigns.isEmpty()) {
            logger.trace("No campaigns found for {}", pi);
            return Collections.emptyList();
        }
        logger.trace("Found {} total campaigns for {}", allActiveCampaigns.size(), pi);

        if (userBean.isLoggedIn()) {
            return userBean.getUser().getAllowedCrowdsourcingCampaigns(allActiveCampaigns);
        }

        List<Campaign> ret = new ArrayList<>(allActiveCampaigns.size());
        for (Campaign campaign : allActiveCampaigns) {
            if (CampaignVisibility.PUBLIC.equals(campaign.getVisibility())) {
                ret.add(campaign);
            }
        }

        logger.trace("Returning {} public campaigns for {}", ret.size(), pi);
        return ret;
    }

    /**
     * Searches for all identifiers that are encompassed by the Solr query of each active campaign and initializes and fills a map of active campaigns
     * for each identifier. Should be called once after the application first starts (or upon first access) or when a campaign has been updated.
     * 
     * @throws DAOException
     * @throws PresentationException
     * @throws IndexUnreachableException
     */
    public void updateActiveCampaigns() throws DAOException, PresentationException, IndexUnreachableException {
        logger.trace("updateActiveCampaigns");
        DataManager.getInstance().setRecordCampaignMap(new HashMap<>());

        List<Campaign> allCampaigns = getAllCampaigns();
        if (allCampaigns.isEmpty()) {
            return;
        }

        for (Campaign campaign : allCampaigns) {
            // Skip inactive campaigns
            if (!CampaignVisibility.PUBLIC.equals(campaign.getVisibility()) && !CampaignVisibility.RESTRICTED.equals(campaign.getVisibility())) {
                continue;
            }
            QueryResponse qr = DataManager.getInstance()
                    .getSearchIndex()
                    .searchFacetsAndStatistics(campaign.getSolrQuery(), Collections.singletonList(SolrConstants.PI_TOPSTRUCT), 1, false);
            if (qr.getFacetField(SolrConstants.PI_TOPSTRUCT) != null) {
                for (Count count : qr.getFacetField(SolrConstants.PI_TOPSTRUCT).getValues()) {
                    String pi = count.getName();
                    List<Campaign> list = DataManager.getInstance().getRecordCampaignMap().get(pi);
                    if (list == null) {
                        list = new ArrayList<>();
                        DataManager.getInstance().getRecordCampaignMap().put(pi, list);
                    }
                    if (!list.contains(campaign)) {
                        list.add(campaign);
                    }
                }
            }
        }
        logger.trace("Added {} identifiers to the map.", DataManager.getInstance().getRecordCampaignMap().size());
    }
}