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
package io.goobi.viewer.model.cms;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.OneToMany;
import javax.persistence.OrderBy;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.Transient;

import org.apache.commons.collections.comparators.NullComparator;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.persistence.annotations.PrivateOwned;
import org.jdom2.JDOMException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unigoettingen.sub.commons.contentlib.exceptions.ContentNotFoundException;
import de.unigoettingen.sub.commons.contentlib.exceptions.IllegalRequestException;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.FileTools;
import io.goobi.viewer.controller.Helper;
import io.goobi.viewer.controller.TEITools;
import io.goobi.viewer.controller.XmlTools;
import io.goobi.viewer.exceptions.CmsElementNotFoundException;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.exceptions.IndexUnreachableException;
import io.goobi.viewer.exceptions.PresentationException;
import io.goobi.viewer.exceptions.ViewerConfigurationException;
import io.goobi.viewer.managedbeans.CmsBean;
import io.goobi.viewer.managedbeans.CmsMediaBean;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.messages.ViewerResourceBundle;
import io.goobi.viewer.model.cms.CMSContentItem.CMSContentItemType;
import io.goobi.viewer.model.cms.CMSPageLanguageVersion.CMSPageStatus;
import io.goobi.viewer.model.cms.itemfunctionality.SearchFunctionality;
import io.goobi.viewer.model.glossary.GlossaryManager;
import io.goobi.viewer.model.viewer.CollectionView;
import io.goobi.viewer.servlets.rest.cms.CMSContentResource;
import io.goobi.viewer.servlets.rest.dao.TileGridResource;

@Entity
@Table(name = "cms_pages")
public class CMSPage implements Comparable<CMSPage> {

    /** Logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(CMSPage.class);

    public static final String GLOBAL_LANGUAGE = "global";
    public static final String CLASSIFICATION_OVERVIEWPAGE = "overviewpage";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cms_page_id")
    private Long id;

    @Column(name = "template_id", nullable = false)
    private String templateId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_created", nullable = false)
    private Date dateCreated;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "date_updated")
    private Date dateUpdated;

    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Column(name = "page_sorting", nullable = true)
    private Long pageSorting = null;

    @Column(name = "use_default_sidebar", nullable = false)
    private boolean useDefaultSidebar = false;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "owner_page_id")
    @PrivateOwned
    private List<CMSProperty> properties = new ArrayList<>();

    @Column(name = "persistent_url", nullable = true)
    private String persistentUrl;

    @Column(name = "related_pi", nullable = true)
    private String relatedPI;

    @Column(name = "subtheme_discriminator", nullable = true)
    private String subThemeDiscriminatorValue = null;

    @OneToMany(mappedBy = "ownerPage", fetch = FetchType.EAGER, cascade = { CascadeType.ALL })
    @OrderBy("order")
    @PrivateOwned
    private List<CMSSidebarElement> sidebarElements = new ArrayList<>();

    @Transient
    private List<CMSSidebarElement> unusedSidebarElements;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "cms_page_cms_categories", joinColumns = @JoinColumn(name = "page_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    private List<CMSCategory> categories = new ArrayList<>();
    
    @OneToMany(mappedBy = "ownerPage", fetch = FetchType.EAGER, cascade = { CascadeType.ALL })
    @PrivateOwned
    private List<CMSPageLanguageVersion> languageVersions = new ArrayList<>();

    /**
     * The id of the parent page. This is usually the id (as String) of the parent cms page, or NULL if the parent page is the start page The system
     * could be extended to set any page type name as parent page (so this page is a breadcrumb-child of e.g. "image view")
     */
    @Column(name = "parent_page")
    private String parentPageId = null;

    /**
     * whether the url to this page may contain additional path parameters at its end while still pointing to this page Should be true if this is a
     * search page, because search parameters are introduced to the url for an actual search Should not be true if this overrides a default page, but
     * should only do so if no parameters are present (for example if parameters indicate a search on the default search page)
     * 
     */
    @Column(name = "may_contain_url_parameters")
    private boolean mayContainUrlParameters = true;

    /**
     * A html class name to be applied to the DOM element containing the page html
     */
    @Column(name = "wrapper_element_class")
    private String wrapperElementClass = "";

    @Transient
    private String sidebarElementString = null;

    @Transient
    PageValidityStatus validityStatus;

    @Transient
    private int listPage = 1;
    
    @Transient 
    private List<Selectable<CMSCategory>> selectableCategories = null;

    /**
     * @deprecated static pages are now stored in a separate table. This only remains for backwards compability
     */
    @Deprecated
    @Column(name = "static_page", nullable = true)
    private String staticPageName;

    public CMSPage() {
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    @Override
    public int compareTo(CMSPage o) {
        if (o == null || o.getId() == null) {
            return -1;
        }
        if (id == null) {
            return 1;
        }

        return id.compareTo(o.getId());
    }

    /**
     * creates a deep copy of the original CMSPage. Only copies persisted properties and performs initialization for them
     * 
     * @param original
     */
    public CMSPage(CMSPage original) {
        if (original.id != null) {
            this.id = new Long(original.id);
        }
        this.templateId = original.templateId;
        this.dateCreated = new Date(original.dateCreated.getTime());
        this.dateUpdated = new Date(original.dateUpdated.getTime());
        this.published = original.published;
        if (original.pageSorting != null) {
            this.pageSorting = new Long(original.pageSorting);
        }
        this.useDefaultSidebar = original.useDefaultSidebar;
        this.persistentUrl = original.persistentUrl;
        this.relatedPI = original.relatedPI;
        this.subThemeDiscriminatorValue = original.subThemeDiscriminatorValue;
        this.categories = new ArrayList<>(original.categories);
        this.parentPageId = original.parentPageId;
        this.mayContainUrlParameters = original.mayContainUrlParameters;
        this.wrapperElementClass = original.wrapperElementClass;

        if (original.properties != null) {
            this.properties = new ArrayList<>(original.properties.size());
            for (CMSProperty property : original.properties) {
                CMSProperty copy = new CMSProperty(property);
                this.properties.add(copy);
            }
        }

        if (original.sidebarElements != null) {
            this.sidebarElements = new ArrayList<>(original.sidebarElements.size());
            for (CMSSidebarElement sidebarElement : original.sidebarElements) {
                CMSSidebarElement copy = CMSSidebarElement.copy(sidebarElement, this);
                this.sidebarElements.add(copy);
            }
        }

        if (original.languageVersions != null) {
            this.languageVersions = new ArrayList<>(original.languageVersions.size());
            for (CMSPageLanguageVersion language : original.languageVersions) {
                CMSPageLanguageVersion copy = new CMSPageLanguageVersion(language, this);
                this.languageVersions.add(copy);
            }
        }
    }

    public boolean saveSidebarElements() {
        logger.trace("selected elements:{}\n", sidebarElementString);
        if (sidebarElementString != null) {
            List<CMSSidebarElement> selectedElements = new ArrayList<>();
            String[] ids = sidebarElementString.split("\\&?item=");
            for (int i = 0; i < ids.length; ++i) {
                if (StringUtils.isBlank(ids[i])) {
                    continue;
                }

                CMSSidebarElement element = getAvailableSidebarElement(ids[i]);
                if (element != null) {
                    // element.setType(ids[i]);
                    element.setValue("bds");
                    element.setOrder(i);
                    //		    element.setId(null);
                    element.setOwnerPage(this);
                    element.serialize();
                    selectedElements.add(element);
                }
            }
            setSidebarElements(selectedElements);
            return true;
        }

        return false;
    }

    public void resetItemData() {
        logger.trace("Resetting item data");
        for (CMSPageLanguageVersion lv : getLanguageVersions()) {
            for (CMSContentItem ci : lv.getContentItems()) {
                ci.resetData();
            }
        }
    }

    /**
     * @param string
     * @return
     */
    private CMSSidebarElement getAvailableSidebarElement(String id) {
        for (CMSSidebarElement visibleElement : getSidebarElements()) {
            if (Integer.toString(visibleElement.getSortingId()).equals(id)) {
                return visibleElement;
            }
        }
        for (CMSSidebarElement unusedElement : getUnusedSidebarElements()) {
            if (Integer.toString(unusedElement.getSortingId()).equals(id)) {
                return unusedElement;
            }
        }
        return null;
    }

    public List<CMSSidebarElement> getUnusedSidebarElements() {
        if (unusedSidebarElements == null) {
            createUnusedSidebarElementList();
        }
        return unusedSidebarElements;
    }

    /**
     *
     */
    private void createUnusedSidebarElementList() {
        unusedSidebarElements = CMSSidebarManager.getAvailableSidebarElements();
        Iterator<CMSSidebarElement> unusedIterator = unusedSidebarElements.iterator();

        while (unusedIterator.hasNext()) {
            CMSSidebarElement unusedElement = unusedIterator.next();
            for (CMSSidebarElement visibleElement : getSidebarElements()) {
                if (visibleElement.equals(unusedElement)) {
                    unusedIterator.remove();
                    break;
                }
            }
        }

    }

    public void addSidebarElement(CMSSidebarElement element) {
        if (element != null) {
            sidebarElements.add(element);
        }
    }

    /**
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * @return the templateId
     */
    public String getTemplateId() {
        return templateId;
    }

    /**
     * @param templateId the templateId to set
     */
    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    /**
     * @return the dateCreated
     */
    public Date getDateCreated() {
        return dateCreated;
    }

    /**
     * @param dateCreated the dateCreated to set
     */
    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }

    /**
     * @return the dateUpdated
     */
    public Date getDateUpdated() {
        return dateUpdated;
    }

    /**
     * @param dateUpdated the dateUpdated to set
     */
    public void setDateUpdated(Date dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    /**
     * @return the published
     */
    public boolean isPublished() {
        return published;
    }

    /**
     * @param published the published to set
     */
    public void setPublished(boolean published) {
        this.published = published;
    }

    /**
     * @return the useDefaultSidebar
     */
    public boolean isUseDefaultSidebar() {
        return useDefaultSidebar;
    }

    /**
     * @param useDefaultSidebar the useDefaultSidebar to set
     */
    public void setUseDefaultSidebar(boolean useDefaultSidebar) {
        this.useDefaultSidebar = useDefaultSidebar;
    }

    /**
     * @return the languageVersions
     */
    public List<CMSPageLanguageVersion> getLanguageVersions() {
        return languageVersions;
    }

    /**
     * @param languageVersions the languageVersions to set
     */
    public void setLanguageVersions(List<CMSPageLanguageVersion> languageVersions) {
        this.languageVersions = languageVersions;
    }

    /**
     * @return the sidebarElements
     */
    public List<CMSSidebarElement> getSidebarElements() {
        return sidebarElements;
    }

    /**
     * @param sidebarElements the sidebarElements to set
     */
    public void setSidebarElements(List<CMSSidebarElement> sidebarElements) {
        this.sidebarElements = sidebarElements;
        createUnusedSidebarElementList();

    }

    /**
     * @return the classifications
     */
    public List<CMSCategory> getCategories() {
        return categories;
    }

    /**
     * @param classifications the classifications to set
     */
    public void setCategories(List<CMSCategory> categories) {
        this.categories = categories;
    }

    public void addCategory(CMSCategory category) {
        if (category != null && !categories.contains(category)) {
        	categories.add(category);
        }
    }

    public void removeCategory(CMSCategory category) {
    	categories.remove(category);
    }

    /**
     * @return the sidebarElementString
     */
    public String getSidebarElementString() {
        return sidebarElementString;
    }

    /**
     * @param sidebarElementString the sidebarElementString to set
     */
    public void setSidebarElementString(String sidebarElementString) {
        logger.trace("setSidebarElementString: {}", sidebarElementString);
        this.sidebarElementString = sidebarElementString;
    }

    public boolean isLanguageComplete(Locale locale) {
        for (CMSPageLanguageVersion version : getLanguageVersions()) {
            try {
                if (version.getLanguage().equals(locale.getLanguage())) {
                    return version.getStatus().equals(CMSPageStatus.FINISHED);
                }
            } catch (NullPointerException e) {
            }
        }
        return false;
    }

    /**
     * 
     * @param itemId
     * @param language
     * @return The item in the language version for the given language
     * @throws CmsElementNotFoundException if no version exists for the given language
     */
    public CMSContentItem getContentItem(String itemId, String language) throws CmsElementNotFoundException {
        CMSPageLanguageVersion version = getBestLanguage(Locale.forLanguageTag(language));
        return version.getContentItem(itemId);
    }

    public CMSPageLanguageVersion getDefaultLanguage() throws CmsElementNotFoundException {
        CMSPageLanguageVersion version = null;
        String language = ViewerResourceBundle.getDefaultLocale().getLanguage();
        version = getLanguageVersion(language);
        if (version == null) {
            version = new CMSPageLanguageVersion();
        }
        return version;
    }

    public CMSPageLanguageVersion getLanguageVersion(Locale locale) throws CmsElementNotFoundException {
        String language = locale.getLanguage();
        return getLanguageVersion(language);
    }

    public CMSPageLanguageVersion getLanguageVersion(String language) throws CmsElementNotFoundException {
        for (CMSPageLanguageVersion version : getLanguageVersions()) {
            if (version.getLanguage().equals(language)) {
                return version;
            }
        }
        throw new CmsElementNotFoundException("No language version for " + language);
        //        synchronized (languageVersions) {
        //            try {
        // CMSPageLanguageVersion version = getTemplate().createNewLanguageVersion(this,
        // language);
        //                this.languageVersions.add(version);
        //                return version;
        //            } catch (NullPointerException | IllegalStateException e) {
        //                return null;
        //            }
        //        }
    }

    public String getTitle() {
        String title;
        try {
            title = getBestLanguage().getTitle();
        } catch (CmsElementNotFoundException e) {
            try {
                title = getBestLanguageIncludeUnfinished().getTitle();
            } catch (CmsElementNotFoundException e1) {
                title = "";
            }
        }
        return title;
    }

    public String getTitle(Locale locale) {
        try {
            return getLanguageVersion(locale.getLanguage()).getTitle();
        } catch (CmsElementNotFoundException e) {
            return getTitle();
        }
    }

    public String getMenuTitle() {
        String title;
        try {
            title = getBestLanguage().getMenuTitle();
        } catch (CmsElementNotFoundException e) {
            try {
                title = getBestLanguageIncludeUnfinished().getMenuTitle();
            } catch (CmsElementNotFoundException e1) {
                title = "";
            }
        }
        return title;
    }

    public String getMenuTitle(Locale locale) {
        try {
            return getLanguageVersion(locale.getLanguage()).getMenuTitle();
        } catch (CmsElementNotFoundException e) {
            return getMenuTitle();
        }
    }
    
    public String getMenuTitleOrTitle() {
        String title;
        try {
            title = getBestLanguage().getMenuTitleOrTitle();
        } catch (CmsElementNotFoundException e) {
            try {
                title = getBestLanguageIncludeUnfinished().getMenuTitleOrTitle();
            } catch (CmsElementNotFoundException e1) {
                title = "";
            }
        }
        return title;
    }

    public String getMenuTitleOrTitle(Locale locale) {
        try {
            return getLanguageVersion(locale.getLanguage()).getMenuTitleOrTitle();
        } catch (CmsElementNotFoundException e) {
            return getMenuTitle();
        }
    }

    public Long getPageSorting() {
        return pageSorting;
    }

    public void setPageSorting(Long pageSorting) {
        this.pageSorting = pageSorting;
    }

    /**
     * @return the subThemeDiscriminatorValue
     */
    public String getSubThemeDiscriminatorValue() {
        return subThemeDiscriminatorValue;
    }

    /**
     * @param subThemeDiscriminatorValue the subThemeDiscriminatorValue to set
     */
    public void setSubThemeDiscriminatorValue(String subThemeDiscriminatorValue) {
        this.subThemeDiscriminatorValue = subThemeDiscriminatorValue;
    }

    public String getMediaName(String contentId) {
        CMSMediaItemMetadata metadata = getMediaMetadata(contentId);
        return metadata == null ? "" : metadata.getName();
    }

    public String getMediaDescription(String contentId) {
        CMSMediaItemMetadata metadata = getMediaMetadata(contentId);
        return metadata == null ? "" : metadata.getDescription();
    }

    /**
     * @param itemId
     * @return The media item metadata object of the current language associated with the contentItem with the given itemId. May return null if no
     *         such item exists
     */
    public CMSMediaItemMetadata getMediaMetadata(String itemId) {
        CMSContentItem item;
        try {
            item = getContentItem(itemId);
        } catch (CmsElementNotFoundException e1) {
            item = null;
        }
        if (item != null && item.getMediaItem() != null) {
            return item.getMediaItem().getCurrentLanguageMetadata();
        }
        return null;
    }

    /**
     * @param itemId
     * @return The media item associated with the contentItem with the given itemId. May return null if no such item exists
     */
    public CMSMediaItem getMedia(String itemId) {
        CMSContentItem item;
        try {
            item = getContentItem(itemId);
        } catch (CmsElementNotFoundException e1) {
            item = null;
        }
        if (item != null && item.getMediaItem() != null) {
            return item.getMediaItem();
        }
        return null;
    }

    public Optional<CMSContentItem> getContentItemIfExists(String itemId) {
        try {
            return Optional.of(getContentItem(itemId));
        } catch (CmsElementNotFoundException e) {
            return Optional.empty();
        }
    }

    /**
     * Return the content item of the given id for the most suitable language using {@link #getBestLanguage()} and - failing that
     * {@link #getBestLanguageIncludeUnfinished()}.
     * 
     * @param itemId
     * @return
     * @throws CmsElementNotFoundException If absolutely no matching element was found
     */
    public CMSContentItem getContentItem(String itemId) throws CmsElementNotFoundException {
        CMSPageLanguageVersion language;
        CMSContentItem item = null;
        try {
            item = getBestLanguage().getContentItem(itemId);
        } catch (CmsElementNotFoundException e) {
            try {
                item = getDefaultLanguage().getContentItem(itemId);
            } catch (CmsElementNotFoundException e1) {
                item = getBestLanguageIncludeUnfinished().getContentItem(itemId);
            }
        }

        return item;
    }

    /**
     * Tries to find the best fitting {@link CMSPageLanguageVersion LanguageVersion} for the current locale. Returns the LanguageVersion for the given
     * locale if it exists has {@link CMSPageStatus} Finished. Otherwise returns the LanguageVersion of the viewer's default language if it exists and
     * is Finished, or failing that the first available (non-global) finished language version
     * 
     * @return
     * @throws CmsElementNotFoundException
     */
    private CMSPageLanguageVersion getBestLanguage() throws CmsElementNotFoundException {
        Locale currentLocale = CmsBean.getCurrentLocale();
        return getBestLanguage(currentLocale);
    }

    /**
     * Tries to find the best fitting {@link CMSPageLanguageVersion LanguageVersion} for the given locale. Returns the LanguageVersion for the given
     * locale if it exists has {@link CMSPageStatus} Finished. Otherwise returns the LanguageVersion of the viewer's default language if it exists and
     * is Finished, or failing that the first available (non-global) finished language version
     * 
     * @param locale The
     * @return
     * @throws CmsElementNotFoundException
     */
    private CMSPageLanguageVersion getBestLanguage(Locale locale) throws CmsElementNotFoundException {
        // logger.trace("getBestLanguage");
        CMSPageLanguageVersion language = getLanguageVersions().stream()
                .filter(l -> l.getStatus().equals(CMSPageStatus.FINISHED))
                .filter(l -> !l.getLanguage().equals(GLOBAL_LANGUAGE))
                .sorted(new CMSPageLanguageVersionComparator(locale, ViewerResourceBundle.getDefaultLocale()))
                .findFirst()
                .orElseThrow(() -> new CmsElementNotFoundException("No finished language version exists for page " + this));
        return language;
    }

    /**
     * Tries to find the best fitting {@link CMSPageLanguageVersion LanguageVersion} for the given locale, including unfinished versions. Returns the
     * LanguageVersion for the given locale if it exists. Otherwise returns the LanguageVersion of the viewer's default language if it exists, or
     * failing that the first available (non-global) language version
     * 
     * @param locale The
     * @return
     * @throws CmsElementNotFoundException
     */
    private CMSPageLanguageVersion getBestLanguageIncludeUnfinished(Locale locale) throws CmsElementNotFoundException {
        CMSPageLanguageVersion language = getLanguageVersions().stream()
                .filter(l -> !l.getLanguage().equals(GLOBAL_LANGUAGE))
                .sorted(new CMSPageLanguageVersionComparator(locale, ViewerResourceBundle.getDefaultLocale()))
                .sorted( (p1, p2) -> p2.getStatus().compareTo(p1.getStatus()))
                .findFirst()
                .orElseThrow(() -> new CmsElementNotFoundException("No language version exists for page " + this.getId()));
        return language;
    }

    /**
     * Tries to find the best fitting {@link CMSPageLanguageVersion LanguageVersion} for the current locale, including unfinished versions. Returns
     * the LanguageVersion for the given locale if it exists. Otherwise returns the LanguageVersion of the viewer's default language if it exists, or
     * failing that the first available (non-global) language version
     * 
     * @return
     * @throws CmsElementNotFoundException
     */
    private CMSPageLanguageVersion getBestLanguageIncludeUnfinished() throws CmsElementNotFoundException {
        Locale currentLocale = CmsBean.getCurrentLocale();
        return getBestLanguageIncludeUnfinished(currentLocale);
    }

    /**
     * @return the pretty url to this page (using alternative url if set)
     */
    public String getPageUrl() {
        return BeanUtils.getCmsBean().getUrl(this);
    }

    /**
     * Like getPageUrl() but does not require CmsBean (which is unavailable in different threads).
     * 
     * @return URL to this page
     */
    public String getUrl() {
        return new StringBuilder(BeanUtils.getServletPathWithHostAsUrlFromJsfContext()).append("/").append(getRelativeUrlPath(true)).toString();
    }

    /**
     * 
     * @param itemId
     * @return true if content item with the given item ID has content matching its type; false otherwisee
     */
    public boolean hasContent(String itemId) {
        CMSContentItem item;
        try {
            item = getContentItem(itemId);
        } catch (CmsElementNotFoundException e) {
            return false;
        }
        switch (item.getType()) {
            case TEXT:
            case HTML:
                return StringUtils.isNotBlank(item.getHtmlFragment());
            case MEDIA:
                return item.getMediaItem() != null && StringUtils.isNotBlank(item.getMediaItem().getFileName());
            case COMPONENT:
                return StringUtils.isNotBlank(item.getComponent());
            default:
                return false;
        }
    }

    public String getContent(String itemId) throws ViewerConfigurationException {
        return getContent(itemId, null, null);
    }

    public Optional<CMSMediaItem> getMediaItem(String itemId) {
        return getContentItemIfExists(itemId).map(content -> content.getMediaItem());
    }

    public Optional<CMSMediaItem> getMediaItem() {
        return getGlobalContentItems().stream()
                .filter(content -> CMSContentItemType.MEDIA.equals(content.getType()))
                .map(content -> content.getMediaItem())
                .filter(item -> item != null)
                .findFirst();
    }

    /**
     * Returns the content of the content item with the given item ID as a string. Depending on the content item's type, this can be either text, a
     * URL, a JSON object, etc.
     * 
     * @param itemId
     * @param width
     * @param height
     * @return the content of the content item with the given item ID as a string
     * @throws ViewerConfigurationException
     */
    public String getContent(String itemId, String width, String height) throws ViewerConfigurationException {
        logger.trace("Getting content {} from page {}", itemId, getId());
        CMSContentItem item;
        try {
            item = getContentItem(itemId);
        } catch (CmsElementNotFoundException e1) {
            logger.error("No content item of id {} found in page {}", itemId, this.getId());
            return "";
        }
        String contentString = "";
        switch (item.getType()) {
            case TEXT:
                contentString = item.getHtmlFragment();
                break;
            case HTML:
                contentString = CMSContentResource.getContentUrl(item);
                break;
            case MEDIA:
                String type = item.getMediaItem() != null ? item.getMediaItem().getContentType() : "";
                switch (type) {
                    case CMSMediaItem.CONTENT_TYPE_DOCX:
                    case CMSMediaItem.CONTENT_TYPE_HTML:
                    case CMSMediaItem.CONTENT_TYPE_RTF:
                        //                        contentString = CmsMediaBean.getMediaFileAsString(item.getMediaItem());
                        contentString = CmsMediaBean.getMediaUrl(item.getMediaItem(), null, null);
                        break;
                    case CMSMediaItem.CONTENT_TYPE_XML:
                        contentString = CmsMediaBean.getMediaFileAsString(item.getMediaItem());
                        try {
                            String format = XmlTools.determineFileFormat(contentString, Helper.DEFAULT_ENCODING);
                            if (format != null) {
                                switch (format.toLowerCase()) {
                                    case "tei":
                                        contentString = TEITools.convertTeiToHtml(contentString);
                                        break;
                                }

                            }
                        } catch (JDOMException | IOException e) {
                            logger.error(e.getMessage(), e);
                        }
                        break;
                    case CMSMediaItem.CONTENT_TYPE_PDF:
                        URI uri = URI.create(DataManager.getInstance().getConfiguration().getRestApiUrl() + "cms/media/get/" + item.getMediaItem().getId() + ".pdf");
                        return uri.toString();
                    default:
                        // Images
                        contentString = CmsMediaBean.getMediaUrl(item.getMediaItem(), width, height);
                }

                break;
            case COMPONENT:
                contentString = item.getComponent();
                break;
            case GLOSSARY:
                try {
                    contentString = new GlossaryManager().getGlossaryAsJson(item.getGlossaryName());
                } catch (ContentNotFoundException | IOException e) {
                    logger.error("Failed to load glossary " + item.getGlossaryName(), e);
                }
                break;
            default:
                contentString = "";
        }
        // logger.trace("Got content as string: {}", contentString);
        return contentString;
    }

    public List<CMSContentItem> getGlobalContentItems() {
        CMSPageLanguageVersion defaultVersion;
        try {
            defaultVersion = getLanguageVersion(GLOBAL_LANGUAGE);
        } catch (CmsElementNotFoundException e) {
            return Collections.emptyList();
        }
        List<CMSContentItem> items = defaultVersion.getContentItems();
        return items;
    }

    public List<CMSContentItem> getContentItems() {
        CMSPageLanguageVersion defaultVersion;
        try {
            defaultVersion = getLanguageVersion(CmsBean.getCurrentLocale().getLanguage());
        } catch (CmsElementNotFoundException e) {
            return new ArrayList<>();
        }
        List<CMSContentItem> items = defaultVersion.getCompleteContentItemList();
        return items;
    }

    public List<CMSContentItem> getContentItems(Locale locale) {
        if (locale != null) {
            CMSPageLanguageVersion version;
            try {
                version = getLanguageVersion(locale.getLanguage());
            } catch (CmsElementNotFoundException e) {
                return new ArrayList<>();
            }
            List<CMSContentItem> items = version.getCompleteContentItemList();
            return items;
        }
        return null;
    }

    private static CMSPageTemplate getTemplateById(String id) {
        return CMSTemplateManager.getInstance().getTemplate(id);
    }

    public CMSPageTemplate getTemplate() {
        return getTemplateById(getTemplateId());
    }

    /**
     * Gets the pagination number for this page's main list if it contains one
     *
     * @return
     */
    public int getListPage() {
        return listPage;
    }

    /**
     * Sets the pagination number for this page's main list if it contains one
     *
     * @param listPage
     */
    public void setListPage(int listPage) {
        resetItemData();
        this.listPage = listPage;
        this.getContentItems().forEach(item -> item.getFunctionality().setPageNo(listPage));

    }

    /**
     * @return the persistentUrl
     */
    public String getPersistentUrl() {
        return persistentUrl;
    }

    /**
     * @param persistentUrl the persistentUrl to set
     */
    public void setPersistentUrl(String persistentUrl) {
        persistentUrl = StringUtils.removeStart(persistentUrl, "/");
        persistentUrl = StringUtils.removeEnd(persistentUrl, "/");
        this.persistentUrl = persistentUrl.trim();
    }

    /**
     *
     */
    public void resetEditorItemVisibility() {
        if (getGlobalContentItems() != null) {
            for (CMSContentItem item : getGlobalContentItems()) {
                item.setVisible(false);
            }
        }
    }

    public static class PageComparator implements Comparator<CMSPage> {
        //null values are high
        NullComparator nullComparator = new NullComparator(true);

        @Override
        public int compare(CMSPage page1, CMSPage page2) {
            int value = nullComparator.compare(page1.getPageSorting(), page2.getPageSorting());
            if (value == 0) {
                value = nullComparator.compare(page1.getId(), page2.getId());
            }
            return value;
        }

    }

    /**
     * @return the staticPageName
     */
    @Deprecated
    public String getStaticPageName() {
        return staticPageName;
    }

    /**
     * @param staticPageName the staticPageName to set
     */
    @Deprecated
    public void setStaticPageName(String staticPageName) {
        this.staticPageName = staticPageName;
    }

    public String getTileGridUrl(String itemId) throws IllegalRequestException {
        CMSContentItem item;
        try {
            item = getContentItem(itemId);
        } catch (CmsElementNotFoundException e) {
            item = null;
        }
        if (item != null && item.getType().equals(CMSContentItemType.TILEGRID)) {
            StringBuilder sb = new StringBuilder(BeanUtils.getServletPathWithHostAsUrlFromJsfContext());
            sb.append("/rest/tilegrid/")
                    .append(CmsBean.getCurrentLocale().getLanguage())
                    .append("/")
                    .append(item.getNumberOfTiles())
                    .append("/")
                    .append(item.getNumberOfImportantTiles())
                    .append("/")
                    .append(item.getCategories().stream().map(CMSCategory::getName).collect(Collectors.joining(TileGridResource.TAG_SEPARATOR)))
                    .append("/");
            return sb.toString();
        }
        throw new IllegalRequestException("No tile grid item with id '" + itemId + "' found");
    }

    public String getRelativeUrlPath() {
        return getRelativeUrlPath(true);
    }

    /**
     * @return
     */
    public String getRelativeUrlPath(boolean pretty) {
        if (pretty && StringUtils.isNotBlank(getPersistentUrl())) {
            return getPersistentUrl() + "/";
        } else if (pretty) {
            try {
                Optional<CMSStaticPage> staticPage = DataManager.getInstance().getDao().getStaticPageForCMSPage(this).stream().findFirst();
                if (staticPage.isPresent()) {
                    return staticPage.get().getPageName() + "/";
                }
            } catch (DAOException e) {
                logger.error(e.toString(), e);
            }
        }
        if (StringUtils.isNotBlank(getRelatedPI())) {
            return "page/" + getRelatedPI() + "/" + getId() + "/";
        }

        return "cms/" + getId() + "/";
    }

    /**
     * TODO HTML/text content items are only added to the last language version in the list, not all of them
     * 
     * @param item
     */
    public void addContentItem(CMSContentItem templateItem) {
        synchronized (languageVersions) {
            List<CMSPageLanguageVersion> languages = new ArrayList<>(getLanguageVersions());
            for (CMSPageLanguageVersion language : languages) {
                CMSContentItem item = new CMSContentItem(templateItem, null);
                if (item.getType().equals(CMSContentItemType.HTML) || item.getType().equals(CMSContentItemType.TEXT)) {
                    if (!language.getLanguage().equals(CMSPage.GLOBAL_LANGUAGE)) {
                        language.addContentItem(item);
                    }
                } else {
                    if (language.getLanguage().equals(CMSPage.GLOBAL_LANGUAGE)) {
                        language.addContentItem(item);
                    }
                }
            }

            // getLanguageVersions().stream().filter(lang ->
            // !lang.getLanguage().equals(CMSPage.GLOBAL_LANGUAGE)).forEach(
            //                        lang -> lang.addContentItem(item));
            //            } else {
            //                getLanguageVersion(CMSPage.GLOBAL_LANGUAGE).addContentItem(item);
            //            }
        }
    }

    /**
     * @param itemId
     * @return
     */
    public boolean hasContentItem(final String itemId) {
        synchronized (languageVersions) {
            return languageVersions.stream()
                    .flatMap(lang -> lang.getContentItems().stream())
                    //                    .map(lang -> lang.getContentItem(itemId))
                    //                    .filter(item -> item != null)
                    .filter(item -> item.getItemId().equals(itemId))
                    .findAny()
                    .isPresent();
        }
    }

    /**
     * Returns the first found SearchFunctionality of any containted content items If no fitting item is found, a new default SearchFunctionality is
     * returned
     * 
     * @return SearchFunctionality, not null
     */
    public SearchFunctionality getSearch() {
        Optional<CMSContentItem> searchItem =
                getGlobalContentItems().stream().filter(item -> CMSContentItemType.SEARCH.equals(item.getType())).findFirst();
        if (searchItem.isPresent()) {
            return (SearchFunctionality) searchItem.get().getFunctionality();
        }
        logger.warn("Did not find search functionality in page " + this);
        return new SearchFunctionality("", getPageUrl());
    }

    public boolean hasSearchFunctionality() {
        Optional<CMSContentItem> searchItem =
                getGlobalContentItems().stream().filter(item -> CMSContentItemType.SEARCH.equals(item.getType())).findFirst();
        return searchItem.isPresent();

    }

    public boolean isHasSidebarElements() {
        if (!isUseDefaultSidebar()) {
            return getSidebarElements() != null && !getSidebarElements().isEmpty();
        }
        return true;
    }

    public void addLanguageVersion(CMSPageLanguageVersion version) {
        this.languageVersions.add(version);
        version.setOwnerPage(this);
    }

    /**
     * @param parentPageId the parentPageId to set
     */
    public void setParentPageId(String parentPageId) {
        this.parentPageId = parentPageId;
    }

    /**
     * @return the parentPageId
     */
    public String getParentPageId() {
        return parentPageId;
    }

    /**
     * @return the validityStatus
     */
    public PageValidityStatus getValidityStatus() {
        return validityStatus;
    }

    /**
     * @param validityStatus the validityStatus to set
     */
    public void setValidityStatus(PageValidityStatus validityStatus) {
        this.validityStatus = validityStatus;
    }

    /**
     * @return the mayContainUrlParameters
     */
    public boolean isMayContainUrlParameters() {
        return mayContainUrlParameters;
    }

    /**
     * @param mayContainUrlParameters the mayContainUrlParameters to set
     */
    public void setMayContainUrlParameters(boolean mayContainUrlParameters) {
        this.mayContainUrlParameters = mayContainUrlParameters;
    }

    //    /**
    // * @return true if this page's template is configured to follow urls which
    // contain additional parameters (e.g. search parameters)
    //     */
    //    public boolean mayContainURLParameters() {
    //        try {
    //            if (getTemplate() != null) {
    //                return getTemplate().isAppliesToExpandedUrl();
    //            }
    //            return false;
    //        } catch (IllegalStateException e) {
    //            logger.warn("Unable to acquire template", e);
    //            return false;
    //        }
    //    }

    /**
     * @return the relatedPI
     */
    public String getRelatedPI() {
        return relatedPI;
    }

    /**
     * @param relatedPI the relatedPI to set
     */
    public void setRelatedPI(String relatedPI) {
        this.relatedPI = relatedPI;
    }

    public CollectionView getCollection() throws PresentationException, IndexUnreachableException {
        return BeanUtils.getCmsBean().getCollection(this);
    }

    /**
     * Returns the property with the given key or else creates a new one with that key and returns it
     * 
     * @param key
     * @throws ClassCastException if the returned property has the wrong generic type
     * @return the property with the given key or else creates a new one with that key and returns it
     */
    public CMSProperty getProperty(String key) throws ClassCastException {
        CMSProperty property = this.properties.stream().filter(prop -> key.equalsIgnoreCase(prop.getKey())).findFirst().orElseGet(() -> {
            CMSProperty prop = new CMSProperty(key);
            this.properties.add(prop);
            return prop;
        });
        return property;
    }

    /*
     * (non-Javadoc)
     * 
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        try {
            String title = getBestLanguageIncludeUnfinished(Locale.ENGLISH).getTitle();
            if(StringUtils.isBlank(title)) {
                return "ID: " + this.getId() + " (no title)";

            } else return title;
        } catch (CmsElementNotFoundException e) {
            return "ID: " + this.getId() + " (no title)";
        }
    }

    /**
     * Remove any language versions without primary key (because h2 doesn't like that)
     */
    public void cleanup() {
        Iterator<CMSPageLanguageVersion> i = languageVersions.iterator();
        while (i.hasNext()) {
            CMSPageLanguageVersion langVersion = i.next();
            if (langVersion.getId() == null) {
                i.remove();
            }
        }

    }

    /**
     * Return true if the page has a {@link CMSPageLanguageVersion} for the given locale
     * 
     * @param locale
     * @return true if the page has a {@link CMSPageLanguageVersion} for the given locale, false otherwise
     */
    public boolean hasLanguageVersion(Locale locale) {
        return this.languageVersions.stream().anyMatch(l -> l.getLanguage().equals(locale.getLanguage()));
    }

    /**
     * Adds {@link CMSPageLanguageVersion}s for all given {@link Locale}s for which no language versions already exist
     * 
     * @param page
     * @param locales
     */
    public void createMissingLangaugeVersions(List<Locale> locales) {
        for (Locale locale : locales) {
            if (!hasLanguageVersion(locale)) {
                addLanguageVersion(new CMSPageLanguageVersion(locale.getLanguage()));
            }
        }

    }

    /**
     * @return the {@link #wrapperElementClass}
     */
    public String getWrapperElementClass() {
        return wrapperElementClass;
    }

    /**
     * @param wrapperElementClass the {@link #wrapperElementClass} to set
     */
    public void setWrapperElementClass(String wrapperElementClass) {
        this.wrapperElementClass = wrapperElementClass;
    }

    /**
     * @param itemId
     */
    public void removeContentItem(String itemId) {
        for (CMSPageLanguageVersion languageVersion : languageVersions) {
            CMSContentItem item;
            try {
                item = languageVersion.getContentItem(itemId);
                languageVersion.removeContentItem(item);
            } catch (CmsElementNotFoundException e) {
                // continue
            }
        }
    }

    /**
     * Deletes exported HTML/TEXT fragments from a related record's data folder. Should be called when deleting this CMS page.
     * 
     * @return Number of deleted files
     */
    public int deleteExportedTextFiles() {
        if (StringUtils.isEmpty(relatedPI)) {
            logger.trace("No related PI - nothing to delete");
            return 0;
        }

        int count = 0;
        try {
            Set<Path> filesToDelete = new HashSet<>();
            String dataRepository = DataManager.getInstance().getSearchIndex().findDataRepository(relatedPI);
            Path cmsTextFolder =
                    Paths.get(Helper.getRepositoryPath(dataRepository) + DataManager.getInstance().getConfiguration().getCmsTextFolder(), relatedPI);
            logger.trace("CMS text folder path: {}", cmsTextFolder.toAbsolutePath().toString());
            if (!Files.isDirectory(cmsTextFolder)) {
                logger.trace("CMS text folder not found - nothing to delete");
                return 0;
            }
            List<Path> cmsPageFiles = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(cmsTextFolder, id + "-*.*")) {
                for (Path file : stream) {
                    if (Files.isRegularFile(file)) {
                        cmsPageFiles.add(file);
                    }
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }

            // Collect files that match the page-contentid name pattern
            for (CMSPageLanguageVersion lv : getLanguageVersions()) {
                for (CMSContentItem ci : lv.getContentItems()) {
                    if (CMSContentItemType.HTML.equals(ci.getType()) || CMSContentItemType.TEXT.equals(ci.getType())
                            || CMSContentItemType.MEDIA.equals(ci.getType())) {
                        String baseFileName = id + "-" + ci.getItemId() + ".";
                        for (Path file : cmsPageFiles) {
                            if (file.getFileName().toString().startsWith(baseFileName)) {
                                filesToDelete.add(file);
                            }
                        }
                    }
                }
            }
            if (!filesToDelete.isEmpty()) {
                for (Path file : filesToDelete) {
                    try {
                        Files.delete(file);
                        count++;
                        logger.info("CMS text file deleted: {}", file.getFileName().toString());
                    } catch (IOException e) {
                        logger.error(e.getMessage());
                    }
                }
            }

            // Delete folder if empty
            try {
                if (FileTools.isFolderEmpty(cmsTextFolder)) {
                    Files.delete(cmsTextFolder);
                    logger.info("Empty CMS text folder deleted: {}", cmsTextFolder.toAbsolutePath());
                }
            } catch (IOException e) {
                logger.error(e.getMessage());
            }
        } catch (PresentationException e) {
            logger.error(e.getMessage(), e);
        } catch (IndexUnreachableException e) {
            logger.error(e.getMessage(), e);
        }

        return count;
    }

    /**
     * Exports text/html fragments from this page's content items for indexing.
     * 
     * @param hotfolderPath
     * @param namingScheme
     * @throws IOException
     */
    public void exportTexts(String hotfolderPath, String namingScheme) throws IOException {
        try {
            // Default language items
            CMSPageLanguageVersion defaultVersion = getDefaultLanguage();
            if (defaultVersion != null && !defaultVersion.getContentItems().isEmpty()) {
                for (CMSContentItem item : getDefaultLanguage().getContentItems()) {
                    exportItemText(item, hotfolderPath, namingScheme);
                }
            }

        } catch (CmsElementNotFoundException e) {
            logger.error(e.getMessage(), e);
        }
        // Global language items
        List<CMSContentItem> globalContentItems = getGlobalContentItems();
        if (!globalContentItems.isEmpty()) {
            for (CMSContentItem item : globalContentItems) {
                exportItemText(item, hotfolderPath, namingScheme);
            }
        }
    }

    /**
     * Exports the contents of the given content item into the given hotfolder path for indexing. Only media, html and text content items can
     * currently be exported.
     * 
     * @param item Content item to export
     * @param hotfolderPath Export path
     * @param namingScheme Naming scheme for export files and folders
     * @throws IOException
     */
    private void exportItemText(CMSContentItem item, String hotfolderPath, String namingScheme) throws IOException {
        if (item.getType() == null) {
            return;
        }
        switch (item.getType()) {
            case MEDIA:
            case HTML:
            case TEXT:
                item.exportHtmlFragment(id, hotfolderPath, namingScheme);
                break;
            default:
                break;
        }
    }
    
    /**
     * Retrieve all categories fresh from the DAO and write them to this depending on the state of the selectableCategories list.
     * Saving the categories from selectableCategories directly leads to ConcurrentModificationexception when persisting page
     */
    public void writeSelectableCategories() {
    	
    	if(selectableCategories != null) {
	    	try {
				List<CMSCategory> allCats = DataManager.getInstance().getDao().getAllCategories();
				List<CMSCategory> tempCats = new ArrayList<>();
				for (CMSCategory cat : allCats) {
					if(this.categories.contains(cat) && selectableCategories.stream().noneMatch(s -> s.getValue().equals(cat))) {
						tempCats.add(cat);
					} else if(selectableCategories.stream().anyMatch(s -> s.getValue().equals(cat) && s.isSelected())) {
						tempCats.add(cat);
					}
				}
				this.categories = tempCats;
	    	} catch (DAOException e) {
	    		logger.error(e.toString(), e);
			}
    	}
    }
    
    /**
     * @return the selectableCategories
     * @throws DAOException 
     */
    public List<Selectable<CMSCategory>> getSelectableCategories() throws DAOException {
        if(selectableCategories == null) {
            List<CMSCategory> allowedCategories = BeanUtils.getCmsBean().getAllowedCategories(BeanUtils.getUserBean().getUser());
            selectableCategories = allowedCategories.stream().map(cat -> new Selectable<CMSCategory>(cat, this.categories.contains(cat))).collect(Collectors.toList());
        }
        return selectableCategories;
    }

}