package io.goobi.viewer.managedbeans;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.commons.io.FileUtils;
import org.jdom2.Document;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.goobi.viewer.controller.Configuration;
import io.goobi.viewer.controller.XmlTools;


class AdminDeveloperBeanTest {

    Configuration config;
    AdminDeveloperBean bean;
    Path configPath = Path.of("src/test/resources/localConfig");
    Path zipPath = Path.of("src/test/resources/output/viewer_dump");

    @BeforeEach
    public void setUp() throws IOException {
        config = Mockito.mock(Configuration.class);
        Mockito.when(config.getTheme()).thenReturn("reference");
        Mockito.when(config.getConfigLocalPath()).thenReturn(configPath.toAbsolutePath().toString());
        bean = new AdminDeveloperBean(config, "viewer");
        if(!Files.exists(zipPath)) {
            Files.createDirectories(zipPath);
        }
    }
    
    @Test
    void test_createZipFile() throws IOException, InterruptedException, JDOMException {
        byte[] zipBytes = bean.createDeveloperArchive(f -> {});
        assertTrue(zipBytes != null && zipBytes.length > 0);
        
    }
    
//    @Test
    void test_createSqlDump() throws IOException, InterruptedException {
        String dump = bean.createSqlDump();
        System.out.println(dump);
        File sqlFile = new File("/opt/digiverso/viewer/config/sql/test.sql");
        FileUtils.write(sqlFile, dump, "utf-8");
        int nullIndex = dump.indexOf("\0");
        assertEquals(-1, nullIndex);
        
    }
    
    @Test
    void test_createConfigDocument() throws IOException, JDOMException {
        Path viewerConfigPath = Path.of("src/test/resources/config_viewer_developer.xml");
        Document doc = bean.createDeveloperViewerConfig(viewerConfigPath);
        assertEquals("https://example.com/solr/collection2", XmlTools.evaluateToFirstString("//config/urls/solr", doc, Collections.emptyList()).orElse(""));
        assertEquals("https://example.com/viewer/api/v1/", XmlTools.evaluateToFirstString("//config/urls/iiif", doc, Collections.emptyList()).orElse(""));
        assertEquals("true", XmlTools.evaluateToFirstAttributeString("//config/urls/iiif/@useForCmsMedia", doc, Collections.emptyList()).orElse(""));
        assertEquals("http://localhost:8080/viewer/api/v1/", XmlTools.evaluateToFirstString("//config/urls/rest", doc, Collections.emptyList()).orElse(""));

    }

    @AfterEach
    void after() throws IOException {
        if(Files.isDirectory(zipPath)) {
            FileUtils.cleanDirectory(zipPath.toFile());
        } else {
            Files.createDirectories(zipPath);
        }
    }
}