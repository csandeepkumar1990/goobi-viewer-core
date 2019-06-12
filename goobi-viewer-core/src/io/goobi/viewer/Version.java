
package io.goobi.viewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;


public class Version {
    public final static String VERSION;
    public final static String BUILDVERSION;
    public final static String BUILDDATE;

    static {
        String manifest = getManifestStringFromJar();
        if (StringUtils.isNotBlank(manifest)) {
            VERSION = getInfo("ApplicationName", manifest) + " " + getInfo("version", manifest);
            BUILDDATE = getInfo("Implementation-Build-Date", manifest);
            BUILDVERSION = getInfo("Implementation-Version", manifest);
        } else {
            VERSION = "unknown";
            BUILDDATE = new Date().toString();
            BUILDVERSION = "unknown";
        }
    }

    private static String getManifestStringFromJar() {
        Class clazz = Version.class;
        String className = clazz.getSimpleName() + ".class";
        String classPath = clazz.getResource(className).toString();
        String value = null;
        String manifestPath;
        if (classPath.startsWith("jar")) {
            manifestPath = classPath.substring(0, classPath.lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF";
        } else {
            manifestPath = classPath.substring(0, classPath.lastIndexOf("classes") + 7) + "/META-INF/MANIFEST.MF";
        }
        try (InputStream inputStream = new URL(manifestPath).openStream()) {
            StringWriter writer = new StringWriter();
            IOUtils.copy(inputStream, writer, "utf-8");
            String manifestString = writer.toString();
            value = manifestString;
        } catch (MalformedURLException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
        return value;
    }

    private static String getInfo(String label, String infoText) {
        String regex = label + ": *(\\S*)";
        Matcher matcher = Pattern.compile(regex).matcher(infoText);
        if (matcher.find()) {
            return matcher.group(1);
        } else {
            return "?";
        }
    }

}