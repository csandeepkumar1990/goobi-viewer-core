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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.dao.IDAO;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.exceptions.ViewerConfigurationException;
import io.goobi.viewer.managedbeans.tabledata.TableDataProvider;
import io.goobi.viewer.managedbeans.tabledata.TableDataSource;
import io.goobi.viewer.managedbeans.tabledata.TableDataProvider.SortOrder;
import io.goobi.viewer.messages.Messages;
import io.goobi.viewer.model.annotation.PersistentAnnotation;
import io.goobi.viewer.model.crowdsourcing.campaigns.Campaign;
import io.goobi.viewer.model.crowdsourcing.questions.Question;
import io.goobi.viewer.model.misc.SelectionManager;

/**
 * @author florian
 *
 */
@Named
@ViewScoped
public class AnnotationBean implements Serializable {
    
    private static final long serialVersionUID = 8377250065305331020L;

    private static final Logger logger = LoggerFactory.getLogger(AnnotationBean.class);

    private static final int DEFAULT_ROWS_PER_PAGE = 15;
    
    @Inject
    protected CrowdsourcingBean crowdsourcingBean;
    
    private TableDataProvider<PersistentAnnotation> lazyModelAnnotations;
    
    private final SelectionManager<Long> exportSelection = new SelectionManager<>();
    
    private String ownerCampaignId = "";
    
    private String targetRecordPI = "";


    @PostConstruct
    public void init() {
        if (lazyModelAnnotations == null) {
            lazyModelAnnotations = new TableDataProvider<>(new TableDataSource<PersistentAnnotation>() {

                private Optional<Long> numCreatedPages = Optional.empty();

                @Override
                public List<PersistentAnnotation> getEntries(int first, int pageSize, String sortField, SortOrder sortOrder,
                        Map<String, String> filters) {
                    try {
                        if (StringUtils.isBlank(sortField)) {
                            sortField = "id";
                            sortOrder = SortOrder.DESCENDING;
                        }
                        filters.putAll(getFilters());
                        List<PersistentAnnotation> ret =
                                DataManager.getInstance().getDao().getAnnotations(first, pageSize, sortField, sortOrder.asBoolean(), filters);
                        return ret;
                    } catch (DAOException e) {
                        logger.error("Could not initialize lazy model: {}", e.getMessage());
                    }

                    return Collections.emptyList();
                }

                /**
                 * @param filters
                 */
                public Map<String, String> getFilters() {
                    Map<String, String> filters = new HashMap<>();
                    if (StringUtils.isNotEmpty(getOwnerCampaignId())) {
                        filters.put("generatorId", getOwnerCampaignId());
                    }
                    if (StringUtils.isNotEmpty(getTargetRecordPI())) {
                        filters.put("targetPI", getTargetRecordPI());
                    }
                    
                    return filters;
                }

                @Override
                public long getTotalNumberOfRecords(Map<String, String> filters) {
                    if (!numCreatedPages.isPresent()) {
                        try {
                            filters.putAll(getFilters());
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
            lazyModelAnnotations.setFilters("targetPI_body");
        }
    }

    /**
     * <p>
     * Getter for the field <code>lazyModelAnnotations</code>.
     * </p>
     *
     * @return the lazyModelAnnotations
     */
    public TableDataProvider<PersistentAnnotation> getLazyModelAnnotations() {
        return lazyModelAnnotations;
    }
    
    /**
     * @return the ownerCampaignId
     */
    public String getOwnerCampaignId() {
        return ownerCampaignId;
    }
    
    /**
     * @param ownerCampaignId the ownerCampaignId to set
     */
    public void setOwnerCampaignId(String ownerCampaignId) {
        this.ownerCampaignId = ownerCampaignId;
    }
    
    /**
     * @return the ownerRecordPI
     */
    public String getTargetRecordPI() {
        return targetRecordPI;
    }
    
    /**
     * @param ownerRecordPI the ownerRecordPI to set
     */
    public void setTargetRecordPI(String targetRecordPI) {
        this.targetRecordPI = targetRecordPI;
    }
    
    /**
     * @return the exportSelection
     */
    public SelectionManager<Long> getExportSelection() {
        return exportSelection;
    }
    

    /**
     * Deletes given annotation.
     *
     * @param annotation a {@link io.goobi.viewer.model.annotation.PersistentAnnotation} object.
     * @return empty string
     * @throws io.goobi.viewer.exceptions.DAOException if any.
     */
    public String deleteAnnotationAction(PersistentAnnotation annotation) throws DAOException {
        if (annotation == null) {
            return "";
        }

        try {
            if (annotation.delete()) {
                Messages.info("admin__crowdsoucing_annotation_deleteSuccess");
                crowdsourcingBean.getLazyModelCampaigns().update();
            }
        } catch (ViewerConfigurationException e) {
            logger.error(e.getMessage());
            Messages.error(e.getMessage());
        }

        return "";
    }

    
    public Optional<Campaign> getOwningCampaign(PersistentAnnotation anno) {
        try {            
            IDAO dao = DataManager.getInstance().getDao();
            if(anno.getGeneratorId() != null) {
                Question question = dao.getQuestion(anno.getGeneratorId());
                if(question != null) {
                    return Optional.ofNullable(question.getOwner());
                }
            }
        } catch(DAOException e) {
            logger.error(e.toString(), e);
        }
        return Optional.empty();
    }
}
