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
package io.goobi.viewer.model.cms.pages;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.collections4.comparators.NullComparator;
import org.apache.commons.compress.harmony.unpack200.bytecode.forms.ThisInitMethodRefForm;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.persistence.annotations.PrivateOwned;

import de.intranda.metadata.multilanguage.IMetadataValue;
import io.goobi.viewer.controller.DataFileTools;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.FileTools;
import io.goobi.viewer.controller.PrettyUrlTools;
import io.goobi.viewer.dao.converter.TranslatedTextConverter;
import io.goobi.viewer.exceptions.DAOException;
import io.goobi.viewer.exceptions.IndexUnreachableException;
import io.goobi.viewer.exceptions.PresentationException;
import io.goobi.viewer.exceptions.ViewerConfigurationException;
import io.goobi.viewer.managedbeans.utils.BeanUtils;
import io.goobi.viewer.model.cms.CMSCategory;
import io.goobi.viewer.model.cms.CMSProperty;
import io.goobi.viewer.model.cms.CMSSlider;
import io.goobi.viewer.model.cms.CMSStaticPage;
import io.goobi.viewer.model.cms.CMSTemplateManager;
import io.goobi.viewer.model.cms.Selectable;
import io.goobi.viewer.model.cms.itemfunctionality.SearchFunctionality;
import io.goobi.viewer.model.cms.pages.content.CMSComponent;
import io.goobi.viewer.model.cms.pages.content.CMSContent;
import io.goobi.viewer.model.cms.pages.content.CMSPageContentManager;
import io.goobi.viewer.model.cms.pages.content.PersistentCMSComponent;
import io.goobi.viewer.model.cms.pages.content.TranslatableCMSContent;
import io.goobi.viewer.model.cms.pages.content.types.CMSHtmlTextContent;
import io.goobi.viewer.model.cms.pages.content.types.CMSImageContent;
import io.goobi.viewer.model.cms.pages.content.types.CMSSearchContent;
import io.goobi.viewer.model.cms.pages.content.types.CMSTextContent;
import io.goobi.viewer.model.cms.widgets.CustomSidebarWidget;
import io.goobi.viewer.model.cms.widgets.WidgetDisplayElement;
import io.goobi.viewer.model.cms.widgets.embed.CMSSidebarElement;
import io.goobi.viewer.model.cms.widgets.embed.CMSSidebarElementAutomatic;
import io.goobi.viewer.model.cms.widgets.embed.CMSSidebarElementCustom;
import io.goobi.viewer.model.cms.widgets.embed.CMSSidebarElementDefault;
import io.goobi.viewer.model.maps.GeoMap;
import io.goobi.viewer.model.misc.Harvestable;
import io.goobi.viewer.model.translations.IPolyglott;
import io.goobi.viewer.model.translations.TranslatedText;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * <p>
 * CMSPage class.
 * </p>
 */
@Entity
@Table(name = "cms_pages")
public class CMSPage implements Comparable<CMSPage>, Harvestable, IPolyglott, Serializable {

    private static final long serialVersionUID = -3601192218326197746L;

    /** Logger for this class. */
    private static final Logger logger = LogManager.getLogger(CMSPage.class);

    /** Constant <code>GLOBAL_LANGUAGE="global"</code> */
    public static final String GLOBAL_LANGUAGE = "global";
    /** Constant <code>CLASSIFICATION_OVERVIEWPAGE="overviewpage"</code> */
    public static final String CLASSIFICATION_OVERVIEWPAGE = "overviewpage";
    public static final String TOPBAR_SLIDER_ID = "topbar_slider";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cms_page_id")
    private Long id;

    @Column(name = "date_created", nullable = false)
    private LocalDateTime dateCreated;

    @Column(name = "date_updated")
    private LocalDateTime dateUpdated;

    @Column(name = "publication_status")
    @Enumerated(EnumType.STRING)
    private PublicationStatus publicationStatus = PublicationStatus.PRIVATE;

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

    @Column(name = "use_as_default_record_view", nullable = false, columnDefinition = "boolean default false")
    private boolean useAsDefaultRecordView = false;

    @Column(name = "subtheme_discriminator", nullable = true)
    private String subThemeDiscriminatorValue = "";

    @OneToMany(mappedBy = "ownerPage", fetch = FetchType.EAGER, cascade = { CascadeType.ALL })
    @OrderBy("order")
    @PrivateOwned
    private List<CMSSidebarElement> sidebarElements = new ArrayList<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "cms_page_cms_categories", joinColumns = @JoinColumn(name = "page_id"), inverseJoinColumns = @JoinColumn(name = "category_id"))
    private List<CMSCategory> categories = new ArrayList<>();

    @Column(name = "title", nullable = false)
    @Convert(converter = TranslatedTextConverter.class)
    private TranslatedText title = new TranslatedText();

    @Column(name = "menu_title", nullable = true)
    @Convert(converter = TranslatedTextConverter.class)
    private TranslatedText menuTitle = new TranslatedText();

    @Column(name = "preview_text", nullable = true)
    @Convert(converter = TranslatedTextConverter.class)
    private TranslatedText previewText = new TranslatedText();

    @JoinColumn(name = "slider_id")
    private CMSSlider topbarSlider = null;
    
    @OneToMany(mappedBy = "ownerPage", fetch = FetchType.EAGER, cascade = { CascadeType.ALL, CascadeType.REMOVE })
    @PrivateOwned
    private List<PersistentCMSComponent> persistentComponents = new ArrayList<>();
    
    /**
     * A {@link CMSPageTemplate} used to create this page. Must be null if the page hasn't been created using a template.
     * Used to apply user privileges for templates to pages derived from that template as well as determining if a page may be edited by a user 
     */
    @JoinColumn(name = "template_id")
    private CMSPageTemplate template = null;

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
    private List<Selectable<CMSCategory>> selectableCategories = null;
    
    @Transient
    private Locale selectedLocale = IPolyglott.getCurrentLocale();

    @Transient
    private List<CMSComponent> cmsComponents = new ArrayList<>();
    
    /**
     * <p>
     * Constructor for CMSPage.
     * </p>
     */
    public CMSPage() {
        this.dateCreated = LocalDateTime.now();
    }

    /**
     * creates a deep copy of the original CMSPage. Only copies persisted properties and performs initialization for them
     *
     * @param original a {@link io.goobi.viewer.model.cms.pages.CMSPage} object.
     */
    public CMSPage(CMSPage original) {
        if (original.id != null) {
            this.id = original.id;
        }
        
        this.title = new TranslatedText(original.title);
        this.menuTitle = new TranslatedText(original.menuTitle);
        this.previewText = new TranslatedText(original.previewText);
        this.topbarSlider = original.topbarSlider;
        this.dateCreated = original.dateCreated;
        this.dateUpdated = original.dateUpdated;
        this.publicationStatus = original.publicationStatus;
        this.template = original.template;
        if (original.pageSorting != null) {
            this.pageSorting = original.pageSorting;
        }
        this.useDefaultSidebar = original.useDefaultSidebar;
        this.persistentUrl = original.persistentUrl;
        this.relatedPI = original.relatedPI;
        this.subThemeDiscriminatorValue = original.subThemeDiscriminatorValue;
        this.categories = new ArrayList<>(original.categories);
        this.parentPageId = original.parentPageId;
        this.mayContainUrlParameters = original.mayContainUrlParameters;
        this.wrapperElementClass = original.wrapperElementClass;
        this.useAsDefaultRecordView = original.useAsDefaultRecordView;

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
                copy.setOwnerPage(this);
                this.sidebarElements.add(copy);
            }
        }

        for (PersistentCMSComponent component : original.persistentComponents) {
            PersistentCMSComponent copy = new PersistentCMSComponent(component);
            copy.setOwnerPage(this);
            this.persistentComponents.add(copy);
        }
        initialiseCMSComponents();
    }
    

    
    /**
     * creates a CMSPage from a {@link CMSPageTemplate}. Only copies persisted properties and performs initialization for them
     *
     * @param original a {@link io.goobi.viewer.model.cms.pages.CMSPageTemplate} object.
     */
    public CMSPage(CMSPageTemplate original) {

        this.dateCreated = LocalDateTime.now();
        this.useDefaultSidebar = original.isUseDefaultSidebar();
        this.subThemeDiscriminatorValue = original.getSubThemeDiscriminatorValue();
        this.categories = new ArrayList<>(original.getCategories());
        this.wrapperElementClass = original.getWrapperElementClass();
        this.template = original;

        if (original.getSidebarElements() != null) {
            this.sidebarElements = new ArrayList<>(original.getSidebarElements().size());
            for (CMSSidebarElement sidebarElement : original.getSidebarElements()) {
                CMSSidebarElement copy = CMSSidebarElement.copy(sidebarElement, this);
                copy.setOwnerPage(this);
                copy.setOwnerTemplate(null);
                this.sidebarElements.add(copy);
            }
        }

        
        for (PersistentCMSComponent component : original.getPersistentComponents()) {
            PersistentCMSComponent copy = new PersistentCMSComponent(component);
            copy.setOwnerPage(this);
            this.persistentComponents.add(copy);
        }
        initialiseCMSComponents();
    }
    
    private void initialiseCMSComponents() {
        this.cmsComponents = new ArrayList<>();
        CMSPageContentManager contentManager = CMSTemplateManager.getInstance().getContentManager();
        for (PersistentCMSComponent component : this.persistentComponents) {
            CMSComponent comp = contentManager.getComponent(component.getTemplateFilename()).map(c -> new CMSComponent(c, Optional.of(component))).orElse(null);
            if(comp != null) {                
                this.cmsComponents.add(comp);
            }
        }
        //sort components and normalize order attributes
        Collections.sort(this.cmsComponents);
        for (int i = 0; i < this.cmsComponents.size(); i++) {
            this.cmsComponents.get(i).setOrder(i+1);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CMSPage other = (CMSPage) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        return true;
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    /** {@inheritDoc} */
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
     * <p>
     * addSidebarElement.
     * </p>
     *
     * @param element a {@link io.goobi.viewer.model.cms.widgets.CMSSidebarElement} object.
     */
    public void addSidebarElement(CMSSidebarElement element) {
        if (element != null) {
            sidebarElements.add(element);
        }
    }

    /**
     * <p>
     * Getter for the field <code>id</code>.
     * </p>
     *
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * <p>
     * Setter for the field <code>id</code>.
     * </p>
     *
     * @param id the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * <p>
     * Getter for the field <code>dateCreated</code>.
     * </p>
     *
     * @return the dateCreated
     */
    public LocalDateTime getDateCreated() {
        return dateCreated;
    }

    /**
     * <p>
     * Setter for the field <code>dateCreated</code>.
     * </p>
     *
     * @param dateCreated the dateCreated to set
     */
    public void setDateCreated(LocalDateTime dateCreated) {
        this.dateCreated = dateCreated;
    }

    /** {@inheritDoc} */
    @Override
    public LocalDateTime getDateUpdated() {
        return dateUpdated;
    }

    /**
     * <p>
     * Setter for the field <code>dateUpdated</code>.
     * </p>
     *
     * @param dateUpdated the dateUpdated to set
     */
    public void setDateUpdated(LocalDateTime dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    /**
     * <p>
     * isPublished.
     * </p>
     *
     * @return the published
     */
    public boolean isPublished() {
        return publicationStatus.PUBLISHED.equals(this.publicationStatus);
    }

    /**
     * <p>
     * Setter for the field <code>published</code>.
     * </p>
     *
     * @param published the published to set
     */
    public void setPublished(boolean published) {
        this.publicationStatus = published ? PublicationStatus.PUBLISHED : PublicationStatus.PRIVATE;
    }

    /**
     * <p>
     * isUseDefaultSidebar.
     * </p>
     *
     * @return the useDefaultSidebar
     */
    public boolean isUseDefaultSidebar() {
        return useDefaultSidebar;
    }

    /**
     * <p>
     * Setter for the field <code>useDefaultSidebar</code>.
     * </p>
     *
     * @param useDefaultSidebar the useDefaultSidebar to set
     */
    public void setUseDefaultSidebar(boolean useDefaultSidebar) {
        this.useDefaultSidebar = useDefaultSidebar;
    }

    /**
     * <p>
     * Getter for the field <code>sidebarElements</code>.
     * </p>
     *
     * @return the sidebarElements
     */
    public List<CMSSidebarElement> getSidebarElements() {
        return sidebarElements;
    }

    /**
     * <p>
     * Setter for the field <code>sidebarElements</code>.
     * </p>
     *
     * @param sidebarElements the sidebarElements to set
     */
    public void setSidebarElements(List<CMSSidebarElement> sidebarElements) {
        this.sidebarElements = sidebarElements;

    }

    public void addToSidebar(List<WidgetDisplayElement> widgets) {
        for (WidgetDisplayElement displayWidget : widgets) {
            CMSSidebarElement element = null;
            switch (displayWidget.getGenerationType()) {
                case DEFAULT:
                    element = new CMSSidebarElementDefault(displayWidget.getContentType(), this);
                    break;
                case AUTOMATIC:
                    try {
                        GeoMap map = DataManager.getInstance().getDao().getGeoMap(displayWidget.getId());
                        element = new CMSSidebarElementAutomatic(map, this);
                    } catch (DAOException e) {
                        logger.error("Unable to add widget: Cannot load geomap id=" + displayWidget.getId());
                    }
                    break;
                case CUSTOM:
                    try {
                        CustomSidebarWidget widget = DataManager.getInstance().getDao().getCustomWidget(displayWidget.getId());
                        element = new CMSSidebarElementCustom(widget, this);
                    } catch (DAOException e) {
                        logger.error("Unable to add widget: Cannot load geomap id=" + displayWidget.getId());
                    }
                    break;
            }
            if (element != null) {
                this.sidebarElements.add(element);
            }
        }
    }

    public void moveUpSidebarElement(CMSSidebarElement element) {
        int currentIndex = this.sidebarElements.indexOf(element);
        if (currentIndex > 0) {
            int newIndex = currentIndex - 1;
            CMSSidebarElement removed = this.sidebarElements.remove(currentIndex);
            this.sidebarElements.add(newIndex, element);
        }
    }

    public void moveDownSidebarElement(CMSSidebarElement element) {
        int currentIndex = this.sidebarElements.indexOf(element);
        if (currentIndex > -1 && currentIndex < this.sidebarElements.size() - 1) {
            int newIndex = currentIndex + 1;
            CMSSidebarElement removed = this.sidebarElements.remove(currentIndex);
            this.sidebarElements.add(newIndex, element);
        }
    }

    public void removeSidebarElement(CMSSidebarElement element) {
        this.sidebarElements.remove(element);
    }

    public boolean containsSidebarElement(WidgetDisplayElement widget) {
        switch (widget.getGenerationType()) {
            case DEFAULT:
                return this.sidebarElements.stream().anyMatch(ele -> ele.getContentType().equals(widget.getContentType()));
            case AUTOMATIC:
                return this.sidebarElements.stream()
                        .anyMatch(ele -> ele.getContentType().equals(widget.getContentType())
                                && Objects.equals(((CMSSidebarElementAutomatic) ele).getMap().getId(), widget.getId()));
            case CUSTOM:
                return this.sidebarElements.stream()
                        .anyMatch(ele -> ele.getContentType().equals(widget.getContentType())
                                && Objects.equals(((CMSSidebarElementCustom) ele).getWidget().getId(), widget.getId()));
            default:
                return false;
        }

    }

    /**
     * <p>
     * Getter for the field <code>categories</code>.
     * </p>
     *
     * @return the classifications
     */
    public List<CMSCategory> getCategories() {
        return categories;
    }

    /**
     * <p>
     * Setter for the field <code>categories</code>.
     * </p>
     *
     * @param categories a {@link java.util.List} object.
     */
    public void setCategories(List<CMSCategory> categories) {
        this.categories = categories;
    }

    /**
     * <p>
     * addCategory.
     * </p>
     *
     * @param category a {@link io.goobi.viewer.model.cms.CMSCategory} object.
     */
    public void addCategory(CMSCategory category) {
        if (category != null && !categories.contains(category)) {
            categories.add(category);
        }
    }

    /**
     * <p>
     * removeCategory.
     * </p>
     *
     * @param category a {@link io.goobi.viewer.model.cms.CMSCategory} object.
     */
    public void removeCategory(CMSCategory category) {
        categories.remove(category);
    }

    /**
     * <p>
     * Getter for the field <code>sidebarElementString</code>.
     * </p>
     *
     * @return the sidebarElementString
     */
    public String getSidebarElementString() {
        return sidebarElementString;
    }

    /**
     * <p>
     * Setter for the field <code>sidebarElementString</code>.
     * </p>
     *
     * @param sidebarElementString the sidebarElementString to set
     */
    public void setSidebarElementString(String sidebarElementString) {
        logger.trace("setSidebarElementString: {}", sidebarElementString);
        this.sidebarElementString = sidebarElementString;
    }

    /**
     * <p>
     * getTitle.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getTitle() {
        return this.title.getTextOrDefault();
    }

    /**
     * <p>
     * getTitle.
     * </p>
     *
     * @param locale a {@link java.util.Locale} object.
     * @return a {@link java.lang.String} object.
     */
    public String getTitle(Locale locale) {
        return this.title.getText(locale);
    }

    public IMetadataValue getTitleTranslations() {
        return this.title;
    }

    public IMetadataValue getPreviewTranslations() {
        return this.previewText;
    }

    /**
     * <p>
     * getMenuTitle.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getMenuTitle() {
        return this.menuTitle.getTextOrDefault();
    }

    /**
     * <p>
     * getMenuTitle.
     * </p>
     *
     * @param locale a {@link java.util.Locale} object.
     * @return a {@link java.lang.String} object.
     */
    public String getMenuTitle(Locale locale) {
        return this.menuTitle.getText(locale);
    }

    /**
     * <p>
     * getMenuTitleOrTitle.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getMenuTitleOrTitle() {
        if (this.menuTitle.isEmpty()) {
            return this.title.getTextOrDefault();
        } else {
            return this.menuTitle.getTextOrDefault();
        }
    }

    /**
     * <p>
     * getMenuTitleOrTitle.
     * </p>
     *
     * @param locale a {@link java.util.Locale} object.
     * @return a {@link java.lang.String} object.
     */
    public String getMenuTitleOrTitle(Locale locale) {
        return this.menuTitle.getValue(locale).orElse(this.title.getText(locale));
    }
    
    public TranslatedText getMenuTitleTranslations() {
        return this.menuTitle;
    }

    /**
     * <p>
     * Getter for the field <code>pageSorting</code>.
     * </p>
     *
     * @return a {@link java.lang.Long} object.
     */
    public Long getPageSorting() {
        return pageSorting;
    }

    /**
     * <p>
     * Setter for the field <code>pageSorting</code>.
     * </p>
     *
     * @param pageSorting a {@link java.lang.Long} object.
     */
    public void setPageSorting(Long pageSorting) {
        this.pageSorting = pageSorting;
    }

    /**
     * <p>
     * Getter for the field <code>subThemeDiscriminatorValue</code>.
     * </p>
     *
     * @return the subThemeDiscriminatorValue
     */
    public String getSubThemeDiscriminatorValue() {
        return subThemeDiscriminatorValue;
    }

    /**
     * <p>
     * Setter for the field <code>subThemeDiscriminatorValue</code>.
     * </p>
     *
     * @param subThemeDiscriminatorValue the subThemeDiscriminatorValue to set
     */
    public void setSubThemeDiscriminatorValue(String subThemeDiscriminatorValue) {
        this.subThemeDiscriminatorValue = subThemeDiscriminatorValue == null ? "" : subThemeDiscriminatorValue;
    }

    /**
     * <p>
     * getPageUrl.
     * </p>
     *
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
     * <p>
     * getPreviewContent.
     * </p>
     *
     * @return the first TEXT or HTML contentItem with preview="true"
     */
    public String getPreviewContent() {
        return this.previewText.getTextOrDefault();
    }

    /**
     * Gets the pagination number for this page's main list if it contains one
     *
     * @return a int.
     */
    public int getListPage() {
        return this.cmsComponents.stream().findAny().map(c -> c.getListPage()).orElse(1);
    }

    /**
     * Sets the pagination number for this page's main list if it contains one
     *
     * @param listPage a int.
     */
    public void setListPage(int listPage) {
        this.cmsComponents.forEach(c -> c.setListPage(listPage));
    }

    /**
     * <p>
     * Getter for the field <code>persistentUrl</code>.
     * </p>
     *
     * @return the persistentUrl
     */
    public String getPersistentUrl() {
        return persistentUrl;
    }

    /**
     * <p>
     * Setter for the field <code>persistentUrl</code>.
     * </p>
     *
     * @param persistentUrl the persistentUrl to set
     */
    public void setPersistentUrl(String persistentUrl) {
        persistentUrl = StringUtils.removeStart(persistentUrl, "/");
        persistentUrl = StringUtils.removeEnd(persistentUrl, "/");
        this.persistentUrl = persistentUrl.trim();
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
     * <p>
     * getRelativeUrlPath.
     * </p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getRelativeUrlPath() {
        return getRelativeUrlPath(true);
    }

    /**
     * <p>
     * getRelativeUrlPath.
     * </p>
     *
     * @param pretty a boolean.
     * @return a {@link java.lang.String} object.
     */
    public String getRelativeUrlPath(boolean pretty) {

        if (pretty) {
            try {
                Optional<CMSStaticPage> staticPage = DataManager.getInstance().getDao().getStaticPageForCMSPage(this).stream().findFirst();
                if (staticPage.isPresent()) {
                    return staticPage.get().getPageName() + "/";
                }
            } catch (DAOException e) {
                logger.error(e.toString(), e);
            }
            if (StringUtils.isNotBlank(getPersistentUrl())) {
                return getPersistentUrl() + "/";
            }
            if (StringUtils.isNotBlank(getRelatedPI())) {
                return "page/" + getRelatedPI() + "/" + getId() + "/";
            }
        }
        return "cms/" + getId() + "/";
    }

    /**
     * <p>
     * isHasSidebarElements.
     * </p>
     *
     * @return a boolean.
     */
    public boolean isHasSidebarElements() {
        if (!isUseDefaultSidebar()) {
            return getSidebarElements() != null && !getSidebarElements().isEmpty();
        }
        return true;
    }

    /**
     * <p>
     * Setter for the field <code>parentPageId</code>.
     * </p>
     *
     * @param parentPageId the parentPageId to set
     */
    public void setParentPageId(String parentPageId) {
        this.parentPageId = parentPageId;
    }

    /**
     * <p>
     * Getter for the field <code>parentPageId</code>.
     * </p>
     *
     * @return the parentPageId
     */
    public String getParentPageId() {
        return parentPageId;
    }

    /**
     * <p>
     * isMayContainUrlParameters.
     * </p>
     *
     * @return the mayContainUrlParameters
     */
    public boolean isMayContainUrlParameters() {
        return mayContainUrlParameters;
    }

    /**
     * <p>
     * Setter for the field <code>mayContainUrlParameters</code>.
     * </p>
     *
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
     * <p>
     * Getter for the field <code>relatedPI</code>.
     * </p>
     *
     * @return the relatedPI
     */
    public String getRelatedPI() {
        return relatedPI;
    }

    /**
     * <p>
     * Setter for the field <code>relatedPI</code>.
     * </p>
     *
     * @param relatedPI the relatedPI to set
     */
    public void setRelatedPI(String relatedPI) {
        this.relatedPI = relatedPI;
    }

    /**
     * @return the useAsDefaultRecordView
     */
    public boolean isUseAsDefaultRecordView() {
        logger.trace("isUseAsDefaultRecordView: {}", useAsDefaultRecordView);
        return useAsDefaultRecordView;
    }

    /**
     * @param useAsDefaultRecordView the useAsDefaultRecordView to set
     */
    public void setUseAsDefaultRecordView(boolean useAsDefaultRecordView) {
        this.useAsDefaultRecordView = useAsDefaultRecordView;
    }

    /**
     * Returns the property with the given key or else creates a new one with that key and returns it
     *
     * @param key a {@link java.lang.String} object.
     * @return the property with the given key or else creates a new one with that key and returns it
     * @throws java.lang.ClassCastException if the returned property has the wrong generic type.
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
    /** {@inheritDoc} */
    @Override
    public String toString() {
        String title = this.getTitle();
        if (StringUtils.isBlank(title)) {
            return "ID: " + this.getId() + " (no title)";

        }
        return title;
    }


    /**
     * <p>
     * Getter for the field <code>wrapperElementClass</code>.
     * </p>
     *
     * @return the {@link #wrapperElementClass}
     */
    public String getWrapperElementClass() {
        return wrapperElementClass;
    }

    /**
     * <p>
     * Setter for the field <code>wrapperElementClass</code>.
     * </p>
     *
     * @param wrapperElementClass the {@link #wrapperElementClass} to set
     */
    public void setWrapperElementClass(String wrapperElementClass) {
        this.wrapperElementClass = wrapperElementClass;
    }


    /**
     * Deletes exported HTML/TEXT fragments from a related record's data folder. Should be called when deleting this CMS page.
     *
     * @return Number of deleted files
     * @throws io.goobi.viewer.exceptions.ViewerConfigurationException if any.
     */
    public int deleteExportedTextFiles() throws ViewerConfigurationException {
        if (StringUtils.isEmpty(relatedPI)) {
            logger.trace("No related PI - nothing to delete");
            return 0;
        }

        if (DataManager.getInstance().getConfiguration().getCmsTextFolder() == null) {
            throw new ViewerConfigurationException("cmsTextFolder is not configured");
        }

        int count = 0;
        try {
            Set<Path> filesToDelete = new HashSet<>();
            Path cmsTextFolder = DataFileTools.getDataFolder(relatedPI, DataManager.getInstance().getConfiguration().getCmsTextFolder());
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

            for (PersistentCMSComponent component : persistentComponents) {
                for (CMSContent content : component.getContentItems()) {
                    if(content instanceof CMSTextContent || content instanceof CMSHtmlTextContent || content instanceof CMSImageContent) {
                        String baseFileName = id + "-" + content.getComponentId() + ".";
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
     * @param outputFolderPath a {@link java.lang.String} object.
     * @param namingScheme a {@link java.lang.String} object.
     * @return a {@link java.util.List} object.
     * @throws java.io.IOException if any.
     */
    public List<File> exportTexts(String outputFolderPath, String namingScheme) throws IOException {
        
        List<CMSContent> contents = this.persistentComponents.stream()
        .flatMap(p -> p.getContentItems().stream())
        .collect(Collectors.toList());
        
        List<File> ret = new ArrayList<>();
        for (CMSContent cmsContent : contents) {
            try {
                ret.addAll(cmsContent.exportHtmlFragment(outputFolderPath, namingScheme));
            } catch (IOException | ViewerConfigurationException e) {
                logger.error("Error writing html file for cms content " + cmsContent.getId());
            }
        }
        return ret;
    }

    /**
     * Retrieve all categories fresh from the DAO and write them to this depending on the state of the selectableCategories list. Saving the
     * categories from selectableCategories directly leads to ConcurrentModificationexception when persisting page
     */
    public void writeSelectableCategories() {
        if (selectableCategories == null) {
            return;
        }

        try {
            List<CMSCategory> allCats = DataManager.getInstance().getDao().getAllCategories();
            List<CMSCategory> tempCats = new ArrayList<>();
            for (CMSCategory cat : allCats) {
                if (this.categories.contains(cat) && selectableCategories.stream().noneMatch(s -> s.getValue().equals(cat))) {
                    tempCats.add(cat);
                } else if (selectableCategories.stream().anyMatch(s -> s.getValue().equals(cat) && s.isSelected())) {
                    tempCats.add(cat);
                }
            }
            this.categories = tempCats;
        } catch (DAOException e) {
            logger.error(e.toString(), e);
        }
    }

    /**
     * <p>
     * Getter for the field <code>selectableCategories</code>.
     * </p>
     *
     * @return the selectableCategories
     * @throws io.goobi.viewer.exceptions.DAOException if any.
     */
    public List<Selectable<CMSCategory>> getSelectableCategories() throws DAOException {
        if (selectableCategories == null) {
            List<CMSCategory> allowedCategories = BeanUtils.getCmsBean().getAllowedCategories(BeanUtils.getUserBean().getUser());
            selectableCategories =
                    allowedCategories.stream().map(cat -> new Selectable<>(cat, this.categories.contains(cat))).collect(Collectors.toList());
        }
        return selectableCategories;
    }

    public void resetSelectableCategories() {
        this.selectableCategories = null;
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.misc.Harvestable#getPi()
     */
    /** {@inheritDoc} */
    @Override
    public String getPi() {
        return getRelatedPI();
    }

    /**
     * @return
     */
    public CMSSlider getTopbarSlider() {
        return this.topbarSlider;
    }

    public void setTopbarSlider(CMSSlider topbarSlider) {
        this.topbarSlider = topbarSlider;
    }
    
    public Long getTopbarSliderId() {
        return Optional.ofNullable(this.topbarSlider).map(CMSSlider::getId).orElse(null);
    }
    
    public void setTopbarSliderId(Long id) throws DAOException {
        setTopbarSlider(DataManager.getInstance().getDao().getSlider(id));
    }

    public String getAdminBackendUrl() {
        String prettyId = "adminCmsEditPage";
        return PrettyUrlTools.getAbsolutePageUrl(prettyId, this.getId());
    }

    public List<PersistentCMSComponent> getPersistentComponents() {
        return persistentComponents;
    }
    
    public List<CMSComponent> getComponents() {
        if(this.cmsComponents.size() != this.persistentComponents.size()) {
            initialiseCMSComponents();
        }
        return this.cmsComponents;
    }
    

    public CMSComponent getAsCMSComponent(PersistentCMSComponent p) {
        return this.cmsComponents.stream()
                .filter(c -> c.getPersistentComponent() == p)
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("Component " + p.getId() + " is not registered in page"));
    }

    public boolean removeComponent(PersistentCMSComponent component) {
        this.cmsComponents.remove(getAsCMSComponent(component));
        return this.persistentComponents.remove(component);
    }
    
    public boolean removeComponent(CMSComponent component) {
        this.persistentComponents.remove(component.getPersistentComponent());
        return this.cmsComponents.remove(component);
    }
    
    public PersistentCMSComponent addComponent(String filename) throws IllegalArgumentException {
        CMSPageContentManager contentManager = CMSTemplateManager.getInstance().getContentManager();
        return addComponent(contentManager.getComponent(filename).orElseThrow(() -> new IllegalArgumentException("No component configured with filename " + filename)));
    }

    PersistentCMSComponent addComponent(CMSComponent template) {
        PersistentCMSComponent persistentComponent = new PersistentCMSComponent(template);
        persistentComponent.setOrder(getHighestComponentOrder() + 1);
        persistentComponent.setOwnerPage(this);
        this.persistentComponents.add(persistentComponent);
        CMSComponent cmsComponent = new CMSComponent(template, Optional.of(persistentComponent));
        this.cmsComponents.add(cmsComponent);
        return persistentComponent;
    }

    private int getHighestComponentOrder() {
        return this.persistentComponents.stream()
                .mapToInt(PersistentCMSComponent::getOrder)
                .max().orElse(0);
    }

    @Override
    public boolean isComplete(Locale locale) {
        Locale defaultLocale = IPolyglott.getDefaultLocale();
        return this.title.isComplete(locale, defaultLocale, true) &&
                this.previewText.isComplete(locale, defaultLocale, false) &&
                this.menuTitle.isComplete(locale, defaultLocale, false) &&
                this.cmsComponents.stream()
                .flatMap(comp -> comp.getTranslatableContentItems().stream())
                .allMatch(content -> ((TranslatableCMSContent)content.getContent()).getText().isComplete(locale, defaultLocale, content.isRequired()));
    }

    @Override
    public boolean isValid(Locale locale) {
        return this.title.isValid(locale) &&
                this.cmsComponents.stream()
                .flatMap(comp -> comp.getTranslatableContentItems().stream())
                .filter(content -> content.isRequired())
                .allMatch(content -> ((TranslatableCMSContent)content.getContent()).getText().isValid(locale));

    }

    @Override
    public boolean isEmpty(Locale locale) {
        return !isValid(locale);
    }

    @Override
    public Locale getSelectedLocale() {
        return this.title.getSelectedLocale();
    }

    @Override
    public void setSelectedLocale(Locale locale) {
        this.title.setSelectedLocale(locale);
        this.menuTitle.setSelectedLocale(locale);
        this.previewText.setSelectedLocale(locale);
        this.persistentComponents.forEach(comp -> comp.setSelectedLocale(locale));
    }

    public boolean hasSearchFunctionality() {
        return this.persistentComponents.stream().flatMap(c -> c.getContentItems().stream())
                .anyMatch(content -> content instanceof CMSSearchContent);
    }

    public Optional<SearchFunctionality> getSearch() {
        return this.persistentComponents.stream().flatMap(c -> c.getContentItems().stream())
                .filter(content -> content instanceof CMSSearchContent)
                .map(content -> ((CMSSearchContent)content).getSearch())
                .findAny();
    }

    public Optional<CMSPageTemplate> getTemplate() {
        return Optional.ofNullable(this.template);
    }
    
    /**
     * Set the order attribute of the {@link PersistentCMSComponent} belonging to the given {@link CMSComponent}
     * to the given order value. Also, sets the order value of all Components which previously had the given order
     * to the order value of the given component
     * @param component
     * @param order
     * @return
     */
    public void setComponentOrder(CMSComponent component, int order) {
        PersistentCMSComponent persistentComponent = component.getPersistentComponent();
        Integer currentOrder = persistentComponent.getOrder();
        this.getComponents().stream().filter(c -> Integer.compare(c.getOrder(), order) == 0)
        .forEach(comp -> {
            comp.setOrder(currentOrder);
        });
        persistentComponent.setOrder(order);
        Collections.sort(this.cmsComponents);
    }
    
    public void incrementOrder(CMSComponent component) {
        this.setComponentOrder(component, component.getOrder()+1);
    }
    
    public void decrementOrder(CMSComponent component) {
        this.setComponentOrder(component, component.getOrder()-1);
    }
}
