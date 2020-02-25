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
package io.goobi.viewer.model.iiif.presentation.builder;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.intranda.api.annotation.IAnnotation;
import de.intranda.api.annotation.oa.Motivation;
import de.intranda.api.annotation.oa.OpenAnnotation;
import de.intranda.api.iiif.presentation.AnnotationList;
import de.intranda.api.iiif.presentation.Layer;
import de.intranda.api.iiif.presentation.content.LinkingContent;
import de.intranda.api.iiif.presentation.enums.AnnotationType;
import de.intranda.api.iiif.presentation.enums.DcType;
import de.intranda.api.iiif.presentation.enums.Format;
import io.goobi.viewer.exceptions.IndexUnreachableException;
import io.goobi.viewer.exceptions.PresentationException;
import io.goobi.viewer.messages.ViewerResourceBundle;
import io.goobi.viewer.servlets.rest.content.ContentResource;

/**
 * <p>
 * LayerBuilder class.
 * </p>
 *
 * @author Florian Alpers
 */
public class LayerBuilder extends AbstractBuilder {

    private static final Logger logger = LoggerFactory.getLogger(LayerBuilder.class);

    /**
     * <p>
     * Constructor for LayerBuilder.
     * </p>
     *
     * @param request a {@link javax.servlet.http.HttpServletRequest} object.
     */
    public LayerBuilder(HttpServletRequest request) {
        super(request);
    }

    /**
     * <p>
     * Constructor for LayerBuilder.
     * </p>
     *
     * @param servletUri a {@link java.net.URI} object.
     * @param requestURI a {@link java.net.URI} object.
     */
    public LayerBuilder(URI servletUri, URI requestURI) {
        super(servletUri, requestURI);
    }

    /**
     * <p>
     * createAnnotationLayer.
     * </p>
     *
     * @param pi a {@link java.lang.String} object.
     * @param type a {@link de.intranda.api.iiif.presentation.enums.AnnotationType} object.
     * @param motivation a {@link java.lang.String} object.
     * @param fileGetter a {@link java.util.function.BiFunction} object.
     * @param linkGetter a {@link java.util.function.BiFunction} object.
     * @return a {@link de.intranda.api.iiif.presentation.Layer} object.
     * @throws io.goobi.viewer.exceptions.PresentationException if any.
     * @throws io.goobi.viewer.exceptions.IndexUnreachableException if any.
     * @throws java.io.IOException if any.
     * @throws java.net.URISyntaxException if any.
     */
    public Layer createAnnotationLayer(String pi, AnnotationType type, String motivation, BiFunction<String, String, List<Path>> fileGetter,
            BiFunction<String, String, URI> linkGetter) throws PresentationException, IndexUnreachableException, IOException, URISyntaxException {
        List<Path> files = ContentResource.getTEIFiles(pi);
        //        List<Path> files = fileGetter.apply(pi, ContentResource.getDataRepository(pi));
        List<IAnnotation> annotations = new ArrayList<>();
        for (Path path : files) {
            Optional<String> language = ContentResource.getLanguage(path.getFileName().toString());
            language.ifPresent(lang -> {
                URI link = linkGetter.apply(pi, lang);
                URI annotationURI = getAnnotationListURI(pi, type);
                OpenAnnotation anno = createAnnotation(annotationURI, link, type.getFormat(), type.getDcType(), type, motivation);
                annotations.add(anno);
            });
        }
        URI annoListURI = getAnnotationListURI(pi, type);
        AnnotationList annoList = createAnnotationList(annotations, annoListURI, type);
        Layer layer = generateLayer(pi, Collections.singletonMap(type, Collections.singletonList(annoList)), type);
        return layer;
    }

    /**
     * <p>
     * createAnnotation.
     * </p>
     *
     * @param annotationId a {@link java.net.URI} object.
     * @param linkURI a {@link java.net.URI} object.
     * @param format a {@link de.intranda.api.iiif.presentation.enums.Format} object.
     * @param dcType a {@link de.intranda.api.iiif.presentation.enums.DcType} object.
     * @param annoType a {@link de.intranda.api.iiif.presentation.enums.AnnotationType} object.
     * @param motivation a {@link java.lang.String} object.
     * @return a {@link de.intranda.api.annotation.oa.OpenAnnotation} object.
     */
    public OpenAnnotation createAnnotation(URI annotationId, URI linkURI, Format format, DcType dcType, AnnotationType annoType, String motivation) {
        LinkingContent link = new LinkingContent(linkURI);
        if (format != null) {
            link.setFormat(format);
        }
        if (dcType != null) {
            link.setType(dcType);
        }
        if (annoType != null) {
            link.setLabel(ViewerResourceBundle.getTranslations(annoType.name()));
        }
        OpenAnnotation annotation = new OpenAnnotation(annotationId);
        if (motivation != null) {
            annotation.setMotivation(motivation);
        } else {
            annotation.setMotivation(Motivation.PAINTING);
        }
        annotation.setBody(link);
        return annotation;
    }

    /**
     * <p>
     * createAnnotationList.
     * </p>
     *
     * @param annotations a {@link java.util.List} object.
     * @param id a {@link java.net.URI} object.
     * @param type a {@link de.intranda.api.iiif.presentation.enums.AnnotationType} object.
     * @return a {@link de.intranda.api.iiif.presentation.AnnotationList} object.
     */
    public AnnotationList createAnnotationList(List<IAnnotation> annotations, URI id, AnnotationType type) {
        AnnotationList annoList = new AnnotationList(id);
        annoList.setLabel(ViewerResourceBundle.getTranslations(type.name()));
        for (IAnnotation annotation : annotations) {
            annoList.addResource(annotation);
        }
        return annoList;
    }

    /**
     * <p>
     * generateContentLayer.
     * </p>
     *
     * @param pi a {@link java.lang.String} object.
     * @param annoLists a {@link java.util.Map} object.
     * @param logId a {@link java.lang.String} object.
     * @return a {@link de.intranda.api.iiif.presentation.Layer} object.
     * @throws java.net.URISyntaxException if any.
     */
    public Layer generateContentLayer(String pi, Map<AnnotationType, List<AnnotationList>> annoLists, String logId) throws URISyntaxException {
        Layer layer = new Layer(getLayerURI(pi, logId));
        for (AnnotationType annoType : annoLists.keySet()) {
            AnnotationList content = new AnnotationList(getAnnotationListURI(pi, annoType));
            content.setLabel(ViewerResourceBundle.getTranslations(annoType.name()));
            annoLists.get(annoType)
                    .stream()
                    .filter(al -> al.getResources() != null)
                    .flatMap(al -> al.getResources().stream())
                    .forEach(annotation -> content.addResource(annotation));
            layer.addOtherContent(content);
        }
        return layer;
    }

    /**
     * <p>
     * generateLayer.
     * </p>
     *
     * @param pi a {@link java.lang.String} object.
     * @param annoLists a {@link java.util.Map} object.
     * @param annoType a {@link de.intranda.api.iiif.presentation.enums.AnnotationType} object.
     * @return a {@link de.intranda.api.iiif.presentation.Layer} object.
     * @throws java.net.URISyntaxException if any.
     */
    public Layer generateLayer(String pi, Map<AnnotationType, List<AnnotationList>> annoLists, AnnotationType annoType) throws URISyntaxException {
        Layer layer = new Layer(getLayerURI(pi, annoType));
        if (annoLists.get(annoType) != null) {
            annoLists.get(annoType).stream().forEach(al -> layer.addOtherContent(al));
        }
        return layer;
    }

    /**
     * <p>
     * mergeAnnotationLists.
     * </p>
     *
     * @param pi a {@link java.lang.String} object.
     * @param annoLists a {@link java.util.Map} object.
     * @return a {@link java.util.Map} object.
     * @throws java.net.URISyntaxException if any.
     */
    public Map<AnnotationType, AnnotationList> mergeAnnotationLists(String pi, Map<AnnotationType, List<AnnotationList>> annoLists)
            throws URISyntaxException {
        Map<AnnotationType, AnnotationList> map = new HashMap<>();
        for (AnnotationType annoType : annoLists.keySet()) {
            AnnotationList content = new AnnotationList(getAnnotationListURI(pi, annoType));
            content.setLabel(ViewerResourceBundle.getTranslations(annoType.name()));
            annoLists.get(annoType)
                    .stream()
                    .filter(al -> al.getResources() != null)
                    .flatMap(al -> al.getResources().stream())
                    .forEach(annotation -> content.addResource(annotation));
            map.put(annoType, content);
        }
        return map;
    }
}
