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
package de.intranda.digiverso.presentation.model.iiif.presentation.builder;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.intranda.api.iiif.IIIFUrlResolver;
import de.intranda.api.iiif.image.ImageInformation;
import de.intranda.api.iiif.presentation.AbstractPresentationModelElement;
import de.intranda.api.iiif.presentation.Collection;
import de.intranda.api.iiif.presentation.IPresentationModelElement;
import de.intranda.api.iiif.presentation.Manifest;
import de.intranda.api.iiif.presentation.content.ImageContent;
import de.intranda.api.iiif.presentation.content.LinkingContent;
import de.intranda.api.iiif.presentation.enums.Format;
import de.intranda.api.iiif.presentation.enums.ViewingHint;
import de.intranda.digiverso.presentation.controller.DataManager;
import de.intranda.digiverso.presentation.exceptions.DAOException;
import de.intranda.digiverso.presentation.exceptions.IndexUnreachableException;
import de.intranda.digiverso.presentation.exceptions.PresentationException;
import de.intranda.digiverso.presentation.exceptions.ViewerConfigurationException;
import de.intranda.digiverso.presentation.managedbeans.ImageDeliveryBean;
import de.intranda.digiverso.presentation.managedbeans.utils.BeanUtils;
import de.intranda.digiverso.presentation.model.viewer.StructElement;
import de.intranda.metadata.multilanguage.MultiLanguageMetadataValue;
import de.intranda.metadata.multilanguage.SimpleMetadataValue;
import de.unigoettingen.sub.commons.contentlib.imagelib.ImageFileFormat;
import de.unigoettingen.sub.commons.contentlib.imagelib.ImageType.Colortype;
import de.unigoettingen.sub.commons.contentlib.imagelib.transform.RegionRequest;
import de.unigoettingen.sub.commons.contentlib.imagelib.transform.Rotation;
import de.unigoettingen.sub.commons.contentlib.imagelib.transform.Scale;

/**
 * @author Florian Alpers
 *
 */
public class ManifestBuilder extends AbstractBuilder {

    private static final Logger logger = LoggerFactory.getLogger(ManifestBuilder.class);
    protected final ImageDeliveryBean imageDelivery = BeanUtils.getImageDeliveryBean();
    private BuildMode buildMode = BuildMode.IIIF;

    /**
     * @param request
     * @throws URISyntaxException
     */
    public ManifestBuilder(HttpServletRequest request) throws URISyntaxException {
        super(request);
    }

    /**
     * @param servletUri
     * @param requestURI
     */
    public ManifestBuilder(URI servletUri, URI requestURI) {
        super(servletUri, requestURI);
    }

    /**
     * @param pi
     * @param baseUrl
     * @return
     * @throws URISyntaxException
     * @throws IndexUnreachableException
     * @throws PresentationException
     * @throws DAOException
     * @throws ViewerConfigurationException
     */
    public IPresentationModelElement generateManifest(StructElement ele)
            throws URISyntaxException, PresentationException, IndexUnreachableException, ViewerConfigurationException, DAOException {

        final AbstractPresentationModelElement manifest;

        if (ele.isAnchor()) {
            manifest = new Collection(getManifestURI(ele.getPi()));
            manifest.setViewingHint(ViewingHint.multipart);
        } else {
            manifest = new Manifest(getManifestURI(ele.getPi()));
        }

        populate(ele, manifest);

        return manifest;
    }

    /**
     * @param ele
     * @param manifest
     * @throws ViewerConfigurationException
     * @throws IndexUnreachableException
     * @throws DAOException
     * @throws PresentationException
     */
    public void populate(StructElement ele, final AbstractPresentationModelElement manifest)
            throws ViewerConfigurationException, IndexUnreachableException, DAOException, PresentationException {
        manifest.setAttribution(getAttribution());
        manifest.setLabel(new SimpleMetadataValue(ele.getLabel()));
        getDescription(ele).ifPresent(desc -> manifest.setDescription(desc));
        
        addMetadata(manifest, ele);

        try {
            String thumbUrl = imageDelivery.getThumbs().getThumbnailUrl(ele);
            if (StringUtils.isNotBlank(thumbUrl)) {
                ImageContent thumb = new ImageContent(new URI(thumbUrl));
                manifest.setThumbnail(thumb);
                if(IIIFUrlResolver.isIIIFImageUrl(thumbUrl)) {
                    String imageInfoURI = IIIFUrlResolver.getIIIFImageBaseUrl(thumbUrl);
                    thumb.setService(new ImageInformation(imageInfoURI));
                }
            }
        } catch (URISyntaxException e) {
            logger.warn("Unable to retrieve thumbnail url", e);
        }

        if (getBuildMode().equals(BuildMode.IIIF)) {
            Optional<String> logoUrl = getLogoUrl();
            if (!logoUrl.isPresent()) {
                logoUrl = BeanUtils.getImageDeliveryBean().getFooter().getWatermarkUrl(Optional.empty(), Optional.ofNullable(ele), Optional.empty());
            }
            logoUrl.ifPresent(url -> {
                try {
                    ImageContent logo = new ImageContent(new URI(url));
                    manifest.setLogo(logo);
                } catch (URISyntaxException e) {
                    logger.warn("Unable to retrieve logo url", e);
                }
            });

            String navDateField = DataManager.getInstance().getConfiguration().getIIIFNavDateField();
            if (StringUtils.isNotBlank(navDateField) && StringUtils.isNotBlank(ele.getMetadataValue(navDateField))) {
                try {
                    String eleValue = ele.getMetadataValue(navDateField);
                    LocalDate date = LocalDate.parse(eleValue);
                    manifest.setNavDate(Date.from(Instant.from(date.atStartOfDay(ZoneId.of("Z")))));
                } catch (NullPointerException | DateTimeParseException e) {
                    logger.warn("Unable to parse {} as Date", ele.getMetadataValue(navDateField));
                }
            }

            if(ele.isLidoRecord()) {
                /*LIDO*/
                try {
                    LinkingContent resolver = new LinkingContent(new URI(getLidoResolverUrl(ele)));
                    resolver.setFormat(Format.TEXT_XML);
                    resolver.setLabel(new SimpleMetadataValue("LIDO"));
                    manifest.addSeeAlso(resolver);
                } catch (URISyntaxException e) {
                    logger.error("Unable to retrieve lido resolver url for {}", ele);
                }
            } else {                
                /*METS/MODS*/
                try {
                    LinkingContent metsResolver = new LinkingContent(new URI(getMetsResolverUrl(ele)));
                    metsResolver.setFormat(Format.TEXT_XML);
                    metsResolver.setLabel(new SimpleMetadataValue("METS/MODS"));
                    manifest.addSeeAlso(metsResolver);
                } catch (URISyntaxException e) {
                    logger.error("Unable to retrieve mets resolver url for {}", ele);
                }
            }
            

            /*VIEWER*/
            try {
                LinkingContent viewerPage = new LinkingContent(new URI(getServletURI() + ele.getUrl()));
                viewerPage.setLabel(new SimpleMetadataValue("goobi viewer"));
                manifest.addRendering(viewerPage);
            } catch (URISyntaxException e) {
                logger.error("Unable to retrieve viewer url for {}", ele);
            }
            
            /*CMS pages*/
            DataManager.getInstance().getDao().getCMSPagesForRecord(ele.getPi(), null).stream().filter(page -> page.isPublished()).forEach(page -> {
                try {
                    LinkingContent cmsPage = new LinkingContent(new URI(page.getUrl()));
                    cmsPage.setLabel(new MultiLanguageMetadataValue(page.getLanguageVersions().stream()
                            .filter(lang -> StringUtils.isNotBlank(lang.getTitle()))
                            .collect(Collectors.toMap(lang -> lang.getLanguage(), lang -> lang.getTitle()))));
                    cmsPage.setFormat(Format.TEXT_HTML);
                    manifest.addRelated(cmsPage);
                } catch (URISyntaxException e) {
                    logger.error("Unable to retrieve viewer url for {}", ele);
                }                
            });

            if (manifest instanceof Manifest) {
                /*PDF*/
                try {
                    String pdfDownloadUrl = BeanUtils.getImageDeliveryBean().getPdf().getPdfUrl(ele, manifest.getLabel().getValue().orElse(null));
                    LinkingContent pdfDownload = new LinkingContent(new URI(pdfDownloadUrl));
                    pdfDownload.setFormat(Format.APPLICATION_PDF);
                    pdfDownload.setLabel(new SimpleMetadataValue("PDF"));
                    manifest.addRendering(pdfDownload);
                } catch (URISyntaxException e) {
                    logger.error("Unable to retrieve pdf download url for {}", ele);
                }

            }
        }
    }

    /**
     * @param anchor
     * @param volumes
     */
    public void addVolumes(Collection anchor, List<StructElement> volumes) {
        for (StructElement volume : volumes) {
            try {
                IPresentationModelElement child = generateManifest(volume);
                if (child instanceof Manifest) {
                    //                    addBaseSequence((Manifest)child, volume, child.getId().toString());
                    anchor.addManifest((Manifest) child);
                }
            } catch (ViewerConfigurationException | URISyntaxException | PresentationException | IndexUnreachableException | DAOException e) {
                logger.error("Error creating child manigest for " + volume);
            }

        }
    }

    /**
     * @param manifest
     * @throws IndexUnreachableException
     * @throws PresentationException
     * @throws DAOException
     * @throws URISyntaxException
     */
    public void addAnchor(Manifest manifest, String anchorPI)
            throws PresentationException, IndexUnreachableException, URISyntaxException, DAOException {

        /*ANCHOR*/
        if (StringUtils.isNotBlank(anchorPI)) {
            manifest.addWithin(new Collection(getManifestURI(anchorPI)));
        }

    }

    /**
     * @param v1
     * @return
     */
    private static Integer getSortingNumber(StructElement volume) {
        String numSort = volume.getVolumeNoSort();
        if (StringUtils.isNotBlank(numSort)) {
            try {
                return Integer.parseInt(numSort);
            } catch (NumberFormatException e) {
                logger.error("Cannot read integer value from " + numSort);
            }
        }
        return -1;
    }

    /**
     * Retrieves the logo url configured in webapi.iiif.logo. If the configured value is an absulute http(s) url, this url will be returned. If it is
     * any other absolute url a contentserver link to that url will be returned. If it is a non-absolute url, it will be considered a filepath within
     * the static images folder of the viewer theme and the appropriate url will be returned
     * 
     * @return An optional containing the configured logo url, or an empty optional if no logo was configured
     * @throws ViewerConfigurationException
     */
    private Optional<String> getLogoUrl() throws ViewerConfigurationException {
        String urlString = DataManager.getInstance().getConfiguration().getIIIFLogo();
        if (urlString != null) {
            try {
                URI url = new URI(urlString);
                if (url.isAbsolute() && url.getScheme().toLowerCase().startsWith("http")) {
                    //fall through
                } else if (url.isAbsolute()) {
                    try {
                        urlString = imageDelivery.getIiif()
                                .getIIIFImageUrl(urlString, "-", RegionRequest.FULL.toString(), Scale.MAX.toString(), Rotation.NONE.toString(),
                                        Colortype.DEFAULT.toString(),
                                        ImageFileFormat.getMatchingTargetFormat(ImageFileFormat.getImageFileFormatFromFileExtension(url.getPath()))
                                                .toString());
                    } catch (NullPointerException e) {
                        logger.error("Value '{}' configured in webapi.iiif.logo is not a valid uri", urlString);
                        urlString = null;
                    }
                } else if (!StringUtils.isBlank(urlString)) {
                    urlString = imageDelivery.getThumbs().getThumbnailPath(urlString).toString();
                } else {
                    urlString = null;
                }
            } catch (URISyntaxException e) {
                logger.error("Value '{}' configured in webapi.iiif.logo is not a valid uri", urlString);
                urlString = null;
            }
        }
        return Optional.ofNullable(urlString);
    }

    /**
     * @return the buildMode
     */
    public BuildMode getBuildMode() {
        return buildMode;
    }

    /**
     * @param buildMode the buildMode to set
     */
    public ManifestBuilder setBuildMode(BuildMode buildMode) {
        this.buildMode = buildMode;
        return this;
    }

}
