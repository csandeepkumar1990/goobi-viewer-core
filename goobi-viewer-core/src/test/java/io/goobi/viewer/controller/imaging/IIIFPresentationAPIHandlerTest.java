package io.goobi.viewer.controller.imaging;

import java.net.URISyntaxException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import io.goobi.viewer.controller.Configuration;
import io.goobi.viewer.controller.ConfigurationTest;
import io.goobi.viewer.controller.DataManager;
import io.goobi.viewer.controller.imaging.IIIFPresentationAPIHandler;

public class IIIFPresentationAPIHandlerTest {

    private IIIFPresentationAPIHandler handler;

    @Before
    public void setUp() throws Exception {
        DataManager.getInstance().injectConfiguration(new Configuration("src/test/resources/config_viewer.test.xml"));
        handler = new IIIFPresentationAPIHandler("", DataManager.getInstance().getConfiguration());
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testGetManifestUrl() throws URISyntaxException {
        Assert.assertEquals(ConfigurationTest.APPLICATION_ROOT_URL + "rest/iiif/manifests/PI-SAMPLE/manifest/", handler.getManifestUrl("PI-SAMPLE"));
    }

    @Test
    public void testGetCollectionUrl() throws URISyntaxException {
        Assert.assertEquals(ConfigurationTest.APPLICATION_ROOT_URL + "rest/iiif/collections/DC/", handler.getCollectionUrl());

    }

    @Test
    public void testGetCollectionUrlString() throws URISyntaxException {
        Assert.assertEquals(ConfigurationTest.APPLICATION_ROOT_URL + "rest/iiif/collections/DC/", handler.getCollectionUrl("DC"));

    }

    @Test
    public void testGetCollectionUrlStringString() throws URISyntaxException {
        Assert.assertEquals(ConfigurationTest.APPLICATION_ROOT_URL + "rest/iiif/collections/DC/sonstige.ocr/",
                handler.getCollectionUrl("DC", "sonstige.ocr"));

    }

    @Test
    public void testGetLayerUrl() throws URISyntaxException {
        Assert.assertEquals(ConfigurationTest.APPLICATION_ROOT_URL + "rest/iiif/manifests/PI-SAMPLE/layer/FULLTEXT/",
                handler.getLayerUrl("PI-SAMPLE", "fulltext"));

    }

    @Test
    public void testGetAnnotationsUrl() throws URISyntaxException {
        Assert.assertEquals("http://localhost:8080/viewer/rest/iiif/manifests/PI-SAMPLE/list/12/PDF/",
                handler.getAnnotationsUrl("PI-SAMPLE", 12, "pdf"));

    }

    @Test
    public void testGetCanvasUrl() throws URISyntaxException {
        Assert.assertEquals("http://localhost:8080/viewer/rest/iiif/manifests/PI-SAMPLE/canvas/12/", handler.getCanvasUrl("PI-SAMPLE", 12));

    }

    @Test
    public void testGetRangeUrl() throws URISyntaxException {
        Assert.assertEquals("http://localhost:8080/viewer/rest/iiif/manifests/PI-SAMPLE/range/LOG_0007/",
                handler.getRangeUrl("PI-SAMPLE", "LOG_0007"));

    }

}
