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
package de.intranda.digiverso.presentation.servlets.rest.iiif.image;

import java.awt.Dimension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Context;
import javax.ws.rs.ext.Provider;

import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.intranda.digiverso.presentation.controller.DataManager;
import de.intranda.digiverso.presentation.model.viewer.PageType;
import de.unigoettingen.sub.commons.contentlib.exceptions.IllegalRequestException;
import de.unigoettingen.sub.commons.contentlib.exceptions.ServiceNotAllowedException;
import de.unigoettingen.sub.commons.contentlib.imagelib.transform.Scale;
import de.unigoettingen.sub.commons.contentlib.imagelib.transform.Scale.AbsoluteScale;
import de.unigoettingen.sub.commons.contentlib.imagelib.transform.Scale.RelativeScale;
import de.unigoettingen.sub.commons.contentlib.servlet.model.iiif.ImageInformation;
import de.unigoettingen.sub.commons.contentlib.servlet.model.iiif.ImageProfile;
import de.unigoettingen.sub.commons.contentlib.servlet.model.iiif.ImageTile;
import de.unigoettingen.sub.commons.contentlib.servlet.rest.ContentServerImageInfoBinding;

@Provider
@ContentServerImageInfoBinding
public class ImageSizeInformationFilter implements ContainerResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(ImageSizeInformationFilter.class);

    @Context
    private HttpServletRequest servletRequest;

    private boolean fullscreen = false;
    private boolean crowdsourcing = false;

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) throws IOException {
        Object responseObject = response.getEntity();
        if (responseObject instanceof ImageInformation) {

            String uri = request.getUriInfo().getPath();

            fullscreen = uri.startsWith("fullscreen");
            crowdsourcing = uri.startsWith("crowdsourcing");

			try {
				List<Integer> imageSizes = getImageSizesFromConfig();
				setImageSizes((ImageInformation) responseObject, imageSizes);
				List<ImageTile> tileSizes;
				tileSizes = getTileSizesFromConfig();
				setTileSizes((ImageInformation) responseObject, tileSizes);
				setMaxImageSizes((ImageInformation) responseObject);
			} catch (ConfigurationException e) {
				logger.error(e.toString(), e);
			}
        }
    }
    
    private static void setMaxImageSizes(ImageInformation info){
        Optional<ImageProfile> profile = info.getProfiles().stream()
                .filter(p -> p instanceof ImageProfile)
                .map(p -> (ImageProfile)p)
                .findFirst();
        profile.ifPresent(p -> {            
            int maxWidth = DataManager.getInstance().getConfiguration().getViewerMaxImageWidth();
            int maxHeight = DataManager.getInstance().getConfiguration().getViewerMaxImageHeight();
            if(maxWidth > 0) {
                p.setMaxWidth(maxWidth);
            }
            if(maxHeight > 0) {
                p.setMaxHeight(maxHeight);
            }
        });
    }

    /**
     * @param responseObject
     * @param imageSizes
     */
    private static void setImageSizes(ImageInformation imageInfo, List<Integer> imageSizes) {

        List<Dimension> dimensions = new ArrayList<>();
        for (Integer size : imageSizes) {
            dimensions.add(new Dimension(size, size));
        }
        if (dimensions.isEmpty()) {
            dimensions.add(new Dimension(imageInfo.getWidth(), imageInfo.getHeight()));
        }
        imageInfo.setSizesFromDimensions(dimensions);
    }

    /**
     * @param responseObject
     * @return
     * @throws ConfigurationException 
     */
    private List<Integer> getImageSizesFromConfig() throws ConfigurationException {
    	PageType pageType = PageType.viewImage;
        if (fullscreen) {
        	pageType = PageType.viewFullscreen;
        } else if (crowdsourcing) {
        	pageType = PageType.editContent;
        }
        List<String> sizeStrings = DataManager.getInstance().getConfiguration().getImageViewZoomScales(pageType, null);
        List<Integer> sizes = new ArrayList<>();
        for (String string : sizeStrings) {
            try {
                int size = Integer.parseInt(string);
                sizes.add(size);
            } catch (NullPointerException | NumberFormatException e) {
                logger.warn("Cannot parse " + string + " as int");
            }
        }
        return sizes;
    }

    /**
     * @return
     * @throws ConfigurationException 
     */
    private List<ImageTile> getTileSizesFromConfig() throws ConfigurationException {
        PageType pageType = PageType.viewImage;
        if (fullscreen) {
        	pageType = PageType.viewFullscreen;
        } else if (crowdsourcing) {
        	pageType = PageType.editContent;
        }
        Map<Integer, List<Integer>> configSizes = DataManager.getInstance().getConfiguration().getTileSizes(pageType, null);
        List<ImageTile> tiles = new ArrayList<>();
        for (Integer size : configSizes.keySet()) {
            ImageTile tile = new ImageTile(size, size, configSizes.get(size));
            tiles.add(tile);
        }
        return tiles;
    }

    /**
     * @param responseObject
     * @param tileSizes
     */
    private static void setTileSizes(ImageInformation imageInfo, List<ImageTile> tileSizes) {
        imageInfo.setTiles(tileSizes);
    }

}
