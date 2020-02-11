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
package io.goobi.viewer.model.security;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.JoinTable;
import javax.persistence.ManyToMany;
import javax.persistence.Table;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.exceptions.DAOException;

/**
 * This class describes license types for record access conditions and also system user roles (not to be confused with the class Role, however), also
 * known as core license types.
 */
@Entity
@Table(name = "license_types")
public class LicenseType implements IPrivilegeHolder {

    /** Logger for this class. */
    private static final Logger logger = LoggerFactory.getLogger(LicenseType.class);

    // When adding a new static license type name, update isStaticLicenseType()!
    /** Constant <code>LICENSE_TYPE_SET_REPRESENTATIVE_IMAGE="licenseType_setRepresentativeImage"</code> */
    public static final String LICENSE_TYPE_SET_REPRESENTATIVE_IMAGE = "licenseType_setRepresentativeImage";
    /** Constant <code>LICENSE_TYPE_DELETE_OCR_PAGE="licenseType_deleteOcrPage"</code> */
    public static final String LICENSE_TYPE_DELETE_OCR_PAGE = "licenseType_deleteOcrPage";
    private static final String LICENSE_TYPE_SET_REPRESENTATIVE_IMAGE_DESCRIPTION = "licenseType_setRepresentativeImage_desc";
    private static final String LICENSE_TYPE_DELETE_OCR_PAGE_DESCRIPTION = "licenseType_deleteOcrPage_desc";
    /** Constant <code>LICENSE_TYPE_CMS="licenseType_cms"</code> */
    public static final String LICENSE_TYPE_CMS = "licenseType_cms";
    private static final String LICENSE_TYPE_DESC_CMS = "licenseType_cms_desc";
    /** Constant <code>LICENSE_TYPE_CROWDSOURCING_CAMPAIGNS="licenseType_crowdsourcing_campaigns"</code> */
    public static final String LICENSE_TYPE_CROWDSOURCING_CAMPAIGNS = "licenseType_crowdsourcing_campaigns";
    private static final String LICENSE_TYPE_DESC_CROWDSOURCING_CAMPAIGNS = "licenseType_crowdsourcing_campaigns_desc";

    //    private static final String CONDITIONS_QUERY = "QUERY:\\{(.*?)\\}";
    private static final String CONDITIONS_FILENAME = "FILENAME:\\{(.*)\\}";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "license_type_id")
    private Long id;

    // Field length had to be limited to 180 chars because InnoDB only supports 767 bytes per index,
    // and the unique index will require 255*n bytes (where n depends on the charset)
    @Column(name = "name", nullable = false, unique = true, columnDefinition = "VARCHAR(180)")
    private String name;
    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;
    @Column(name = "conditions")
    private String conditions;
    @Column(name = "open_access")
    private boolean openAccess = false;
    @Column(name = "core")
    private boolean core = false;

    /** Privileges that everyone else has (users without this license, users that are not logged in). */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "license_type_privileges", joinColumns = @JoinColumn(name = "license_type_id"))
    @Column(name = "privilege_name")
    private Set<String> privileges = new HashSet<>();

    /** Other license types for which a user may have privileges that this license type does not grant. */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "license_types_overriding", joinColumns = @JoinColumn(name = "license_type_id"),
            inverseJoinColumns = @JoinColumn(name = "overriding_license_type_id"))
    private Set<LicenseType> overridingLicenseTypes = new HashSet<>();

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#hashCode()
     */
    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (name == null ? 0 : name.hashCode());
        return result;
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#equals(java.lang.Object)
     */
    /** {@inheritDoc} */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        LicenseType other = (LicenseType) obj;
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        return true;
    }

    /**
     * <p>Getter for the field <code>id</code>.</p>
     *
     * @return the id
     */
    public Long getId() {
        return id;
    }

    /**
     * <p>Setter for the field <code>id</code>.</p>
     *
     * @param id the id to set
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * <p>Getter for the field <code>name</code>.</p>
     *
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * <p>Setter for the field <code>name</code>.</p>
     *
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * <p>Getter for the field <code>description</code>.</p>
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * <p>Setter for the field <code>description</code>.</p>
     *
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * <p>getProcessedConditions.</p>
     *
     * @should replace NOW/YEAR with the current year if not using a date field
     * @return a {@link java.lang.String} object.
     */
    public String getProcessedConditions() {
        String conditions = this.conditions;

        conditions = getQueryConditions(conditions);

        if (conditions.contains("NOW/YEAR") && !conditions.contains("DATE_")) {
            // Hack for getting the current year as a number for non-date Solr fields
            conditions = conditions.replace("NOW/YEAR", String.valueOf(Calendar.getInstance().get(Calendar.YEAR)));
        }

        return conditions.trim();
    }

    /**
     * <p>getFilenameConditions.</p>
     *
     * @return a {@link java.lang.String} object.
     */
    public String getFilenameConditions() {
        return getFilenameConditions(this.conditions);
    }

    /**
     * Get the conditions referring to a SOLR query. This is either the substring in {} after QUERY: or the entire string if neither QUERY:{...} nor
     * FILENAME:{...} is part of the given string
     * 
     * @param conditions
     * @return
     */
    private String getQueryConditions(String conditions) {
        String filenameConditions = getMatch(conditions, CONDITIONS_FILENAME);
        String queryConditions = conditions == null ? "" : conditions.replaceAll(CONDITIONS_FILENAME, "");
        if (StringUtils.isBlank(queryConditions) && StringUtils.isBlank(filenameConditions)) {
            return conditions == null ? "" : conditions;
        }
        return queryConditions;
    }

    /**
     * <p>getMatch.</p>
     *
     * @param conditions a {@link java.lang.String} object.
     * @param pattern a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     */
    public String getMatch(String conditions, String pattern) {
        if (StringUtils.isBlank(conditions)) {
            return "";
        }
        Matcher matcher = Pattern.compile(pattern).matcher(conditions);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Get the conditions referring to filename matching. This is either the substring in {} after FILENAME: or null if neither QUERY:{...} nor
     * FILENAME:{...} is part of the given string
     * 
     * @param conditions
     * @return
     */
    private String getFilenameConditions(String conditions) {
        String filenameConditions = getMatch(conditions, CONDITIONS_FILENAME);
        return filenameConditions;
    }

    /**
     * <p>isCmsType.</p>
     *
     * @return true if this license type has one of the static CMS type names; false otherwise
     */
    public boolean isCmsType() {
        if (name == null) {
            return false;
        }

        switch (name) {
            case LICENSE_TYPE_CMS:
                return true;
            default:
                return false;
        }
    }

    /**
     * <p>isCrowdsourcingType.</p>
     *
     * @return true if this license type has one of the static crowdsourcing type names; false otherwise
     */
    public boolean isCrowdsourcingType() {
        if (name == null) {
            return false;
        }

        switch (name) {
            case LICENSE_TYPE_CROWDSOURCING_CAMPAIGNS:
                return true;
            default:
                return false;
        }
    }

    /**
     * <p>Getter for the field <code>conditions</code>.</p>
     *
     * @return the conditions
     */
    public String getConditions() {
        return conditions;
    }

    /**
     * <p>Setter for the field <code>conditions</code>.</p>
     *
     * @param conditions the conditions to set
     */
    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    /**
     * <p>isOpenAccess.</p>
     *
     * @return the openAccess
     */
    public boolean isOpenAccess() {
        return openAccess;
    }

    /**
     * <p>Setter for the field <code>openAccess</code>.</p>
     *
     * @param openAccess the openAccess to set
     */
    public void setOpenAccess(boolean openAccess) {
        this.openAccess = openAccess;
    }

    /**
     * <p>isCore.</p>
     *
     * @return the core
     */
    public boolean isCore() {
        return core;
    }

    /**
     * <p>Setter for the field <code>core</code>.</p>
     *
     * @param core the core to set
     */
    public void setCore(boolean core) {
        this.core = core;
    }

    /**
     * <p>Getter for the field <code>privileges</code>.</p>
     *
     * @return the privileges
     */
    public Set<String> getPrivileges() {
        return privileges;
    }

    /**
     * <p>Setter for the field <code>privileges</code>.</p>
     *
     * @param privileges the privileges to set
     */
    public void setPrivileges(Set<String> privileges) {
        this.privileges = privileges;
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasPrivilege(String privilege) {
        return privileges.contains(privilege);
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivList() {
        return hasPrivilege(IPrivilegeHolder.PRIV_LIST);
    }

    /** {@inheritDoc} */
    @Override
    public void setPrivList(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_LIST);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_LIST);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivViewImages() {
        return hasPrivilege(IPrivilegeHolder.PRIV_VIEW_IMAGES);
    }

    /** {@inheritDoc} */
    @Override
    public void setPrivViewImages(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_VIEW_IMAGES);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_VIEW_IMAGES);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivViewThumbnails() {
        return hasPrivilege(IPrivilegeHolder.PRIV_VIEW_THUMBNAILS);
    }

    /** {@inheritDoc} */
    @Override
    public void setPrivViewThumbnails(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_VIEW_THUMBNAILS);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_VIEW_THUMBNAILS);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivViewFulltext() {
        return hasPrivilege(IPrivilegeHolder.PRIV_VIEW_FULLTEXT);
    }

    /** {@inheritDoc} */
    @Override
    public void setPrivViewFulltext(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_VIEW_FULLTEXT);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_VIEW_FULLTEXT);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivViewVideo() {
        return hasPrivilege(IPrivilegeHolder.PRIV_VIEW_VIDEO);
    }

    /** {@inheritDoc} */
    @Override
    public void setPrivViewVideo(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_VIEW_VIDEO);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_VIEW_VIDEO);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivViewAudio() {
        return hasPrivilege(IPrivilegeHolder.PRIV_VIEW_AUDIO);
    }

    /** {@inheritDoc} */
    @Override
    public void setPrivViewAudio(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_VIEW_AUDIO);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_VIEW_AUDIO);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivDownloadPdf() {
        return hasPrivilege(IPrivilegeHolder.PRIV_DOWNLOAD_PDF);
    }

    /** {@inheritDoc} */
    @Override
    public void setPrivDownloadPdf(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_DOWNLOAD_PDF);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_DOWNLOAD_PDF);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivDownloadPagePdf()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivDownloadPagePdf() {
        return hasPrivilege(IPrivilegeHolder.PRIV_DOWNLOAD_PAGE_PDF);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivDownloadPagePdf(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivDownloadPagePdf(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_DOWNLOAD_PAGE_PDF);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_DOWNLOAD_PAGE_PDF);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.user.IPrivilegeHolder#isPrivDownloadOriginalContent()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivDownloadOriginalContent() {
        return hasPrivilege(IPrivilegeHolder.PRIV_DOWNLOAD_ORIGINAL_CONTENT);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.user.IPrivilegeHolder#setPrivDownloadOriginalContent(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivDownloadOriginalContent(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_DOWNLOAD_ORIGINAL_CONTENT);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_DOWNLOAD_ORIGINAL_CONTENT);
        }

    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivDeleteOcrPage()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivDeleteOcrPage() {
        return hasPrivilege(IPrivilegeHolder.PRIV_DELETE_OCR_PAGE);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivDeleteOcrPage(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivDeleteOcrPage(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_DELETE_OCR_PAGE);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_DELETE_OCR_PAGE);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean isPrivSetRepresentativeImage() {
        return hasPrivilege(IPrivilegeHolder.PRIV_SET_REPRESENTATIVE_IMAGE);
    }

    /** {@inheritDoc} */
    @Override
    public void setPrivSetRepresentativeImage(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_SET_REPRESENTATIVE_IMAGE);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_SET_REPRESENTATIVE_IMAGE);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCmsPages()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCmsPages() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CMS_PAGES);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCmsPages(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCmsPages(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CMS_PAGES);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CMS_PAGES);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCmsAllSubthemes()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCmsAllSubthemes() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CMS_ALL_SUBTHEMES);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCmsAllSubthemes(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCmsAllSubthemes(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CMS_ALL_SUBTHEMES);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CMS_ALL_SUBTHEMES);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCmsAllCategories()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCmsAllCategories() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CMS_ALL_CATEGORIES);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCmsAllCategories(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCmsAllCategories(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CMS_ALL_CATEGORIES);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CMS_ALL_CATEGORIES);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCmsAllTemplates()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCmsAllTemplates() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CMS_ALL_TEMPLATES);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCmsAllTemplates(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCmsAllTemplates(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CMS_ALL_TEMPLATES);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CMS_ALL_TEMPLATES);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCmsMenu()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCmsMenu() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CMS_MENU);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCmsMenu(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCmsMenu(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CMS_MENU);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CMS_MENU);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCmsStaticPages()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCmsStaticPages() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CMS_STATIC_PAGES);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCmsStaticPages(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCmsStaticPages(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CMS_STATIC_PAGES);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CMS_STATIC_PAGES);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCmsCollections()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCmsCollections() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CMS_COLLECTIONS);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCmsCollections(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCmsCollections(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CMS_COLLECTIONS);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CMS_COLLECTIONS);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCmsCategories()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCmsCategories() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CMS_CATEGORIES);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCmsCategories(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCmsCategories(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CMS_CATEGORIES);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CMS_CATEGORIES);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCrowdsourcingAllCampaigns()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCrowdsourcingAllCampaigns() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CROWDSOURCING_ALL_CAMPAIGNS);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCrowdsourcingAllCampaigns(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCrowdsourcingAllCampaigns(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CROWDSOURCING_ALL_CAMPAIGNS);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CROWDSOURCING_ALL_CAMPAIGNS);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCrowdsourcingAnnotateCampaign()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCrowdsourcingAnnotateCampaign() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CROWDSOURCING_ANNOTATE_CAMPAIGN);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCrowdsourcingAnnotateCampaign(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCrowdsourcingAnnotateCampaign(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CROWDSOURCING_ANNOTATE_CAMPAIGN);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CROWDSOURCING_ANNOTATE_CAMPAIGN);
        }
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#isPrivCrowdsourcingReviewCampaign()
     */
    /** {@inheritDoc} */
    @Override
    public boolean isPrivCrowdsourcingReviewCampaign() {
        return hasPrivilege(IPrivilegeHolder.PRIV_CROWDSOURCING_REVIEW_CAMPAIGN);
    }

    /* (non-Javadoc)
     * @see io.goobi.viewer.model.security.IPrivilegeHolder#setPrivCrowdsourcingReviewCampaign(boolean)
     */
    /** {@inheritDoc} */
    @Override
    public void setPrivCrowdsourcingReviewCampaign(boolean priv) {
        if (priv) {
            privileges.add(IPrivilegeHolder.PRIV_CROWDSOURCING_REVIEW_CAMPAIGN);
        } else {
            privileges.remove(IPrivilegeHolder.PRIV_CROWDSOURCING_REVIEW_CAMPAIGN);
        }
    }

    /**
     * <p>Getter for the field <code>overridingLicenseTypes</code>.</p>
     *
     * @return the overridingLicenseTypes
     */
    public Set<LicenseType> getOverridingLicenseTypes() {
        return overridingLicenseTypes;
    }

    /**
     * <p>Setter for the field <code>overridingLicenseTypes</code>.</p>
     *
     * @param overridingLicenseTypes the overridingLicenseTypes to set
     */
    public void setOverridingLicenseTypes(Set<LicenseType> overridingLicenseTypes) {
        this.overridingLicenseTypes = overridingLicenseTypes;
    }

    /**
     * <p>addCoreLicenseTypesToDB.</p>
     *
     * @throws io.goobi.viewer.exceptions.DAOException if any.
     */
    public static void addCoreLicenseTypesToDB() throws DAOException {
        // Add the license type "may set representative image", if not yet in the database
        addCoreLicenseType(LICENSE_TYPE_SET_REPRESENTATIVE_IMAGE, LICENSE_TYPE_SET_REPRESENTATIVE_IMAGE_DESCRIPTION,
                IPrivilegeHolder.PRIV_SET_REPRESENTATIVE_IMAGE);
        // Add the license type "may delete ocr page", if not yet in the database
        addCoreLicenseType(LICENSE_TYPE_DELETE_OCR_PAGE, LICENSE_TYPE_DELETE_OCR_PAGE_DESCRIPTION, IPrivilegeHolder.PRIV_DELETE_OCR_PAGE);
        // Add CMS license types, if not yet in the database
        addCoreLicenseType(LICENSE_TYPE_CMS, LICENSE_TYPE_DESC_CMS, IPrivilegeHolder.PRIV_CMS_PAGES);
        // Add crowdsourcing license types, if not yet in the database
        addCoreLicenseType(LICENSE_TYPE_CROWDSOURCING_CAMPAIGNS, LICENSE_TYPE_DESC_CROWDSOURCING_CAMPAIGNS, new String[0]);
    }

    /**
     * 
     * @param licenseTypeName
     * @param licenseTypeDesc
     * @param privName
     * @throws DAOException
     */
    private static void addCoreLicenseType(String licenseTypeName, String licenseTypeDesc, String... privNames) throws DAOException {
        LicenseType licenseType = DataManager.getInstance().getDao().getLicenseType(licenseTypeName);
        if (licenseType != null) {
            // Set core=true
            if (!licenseType.isCore()) {
                logger.info("Adding core=true to license type '{}'...", licenseTypeName);
                licenseType.setCore(true);
                if (!DataManager.getInstance().getDao().updateLicenseType(licenseType)) {
                    logger.error("Could not update static license type '{}'.", licenseTypeName);
                }
            }
            return;
        }
        logger.info("License type '{}' does not exist yet, adding...", licenseTypeName);
        licenseType = new LicenseType();
        licenseType.setName(licenseTypeName);
        licenseType.setDescription(licenseTypeDesc);
        licenseType.setCore(true);
        if (privNames != null && privNames.length > 0) {
            for (String privName : privNames) {
                licenseType.getPrivileges().add(privName);
            }
        }
        if (!DataManager.getInstance().getDao().addLicenseType(licenseType)) {
            logger.error("Could not add static license type '{}'.", licenseTypeName);
        }
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    /** {@inheritDoc} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LicenceType: ").append(getName()).append(":\t");
        sb.append("openaccess: ").append(isOpenAccess());
        sb.append("\tconditions: ").append(conditions);
        sb.append("\n\t").append("Privileges: ").append(StringUtils.join(getPrivileges(), ", "));
        return sb.toString();
    }
}