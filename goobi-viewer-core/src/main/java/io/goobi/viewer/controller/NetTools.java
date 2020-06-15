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
package io.goobi.viewer.controller;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.goobi.viewer.exceptions.HTTPException;

/**
 * Utility methods for HTTP operations, mail, etc.
 *
 */
public class NetTools {

    private static final Logger logger = LoggerFactory.getLogger(NetTools.class);

    private static final int HTTP_TIMEOUT = 30000;
    /** Constant <code>ADDRESS_LOCALHOST_IPV4="127.0.0.1"</code> */
    public static final String ADDRESS_LOCALHOST_IPV4 = "127.0.0.1";
    /** Constant <code>ADDRESS_LOCALHOST_IPV6="0:0:0:0:0:0:0:1"</code> */
    public static final String ADDRESS_LOCALHOST_IPV6 = "0:0:0:0:0:0:0:1";

    /**
     * <p>
     * callUrlGET.
     * </p>
     *
     * @param url a {@link java.lang.String} object.
     * @return A String array with two elements. The first contains the HTTP status code, the second either the requested data (if status code is 200)
     *         or the error message.
     */
    public static String[] callUrlGET(String url) {
        logger.debug("callUrlGET: {}", url);
        String[] ret = new String[2];
        try (CloseableHttpClient httpClient = HttpClients.custom().build()) {
            HttpGet httpGet = new HttpGet(url);
            try (CloseableHttpResponse response = httpClient.execute(httpGet); StringWriter writer = new StringWriter()) {
                ret[0] = String.valueOf(response.getStatusLine().getStatusCode());
                switch (response.getStatusLine().getStatusCode()) {
                    case HttpServletResponse.SC_OK:
                        HttpEntity httpEntity = response.getEntity();
                        httpEntity.getContentLength();
                        IOUtils.copy(httpEntity.getContent(), writer, StringTools.DEFAULT_ENCODING);
                        ret[1] = writer.toString();
                        break;
                    case 401:
                        logger.warn("Error code: {}", response.getStatusLine().getStatusCode());
                        ret[1] = response.getStatusLine().getReasonPhrase();
                        break;
                    default:
                        logger.warn("Error code: {}", response.getStatusLine().getStatusCode());
                        ret[1] = response.getStatusLine().getReasonPhrase();
                        break;
                }
            }
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }

        return ret;
    }

    /**
     * <p>
     * getWebContentGET.
     * </p>
     *
     * @param urlString a {@link java.lang.String} object.
     * @return a {@link java.lang.String} object.
     * @throws org.apache.http.client.ClientProtocolException if any.
     * @throws java.io.IOException if any.
     * @throws io.goobi.viewer.exceptions.HTTPException if any.
     */
    public static String getWebContentGET(String urlString) throws ClientProtocolException, IOException, HTTPException {
        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setSocketTimeout(HTTP_TIMEOUT)
                .setConnectTimeout(HTTP_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_TIMEOUT)
                .build();
        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build()) {
            HttpGet get = new HttpGet(urlString);
            try (CloseableHttpResponse response = httpClient.execute(get); StringWriter writer = new StringWriter()) {
                int code = response.getStatusLine().getStatusCode();
                if (code == HttpStatus.SC_OK) {
                    return EntityUtils.toString(response.getEntity(), StringTools.DEFAULT_ENCODING);
                    // IOUtils.copy(response.getEntity().getContent(), writer);
                    // return writer.toString();
                }
                logger.trace("{}: {}", code, response.getStatusLine().getReasonPhrase());
                throw new HTTPException(code, response.getStatusLine().getReasonPhrase());
            }
        }
    }

    /**
     * <p>
     * getWebContentPOST.
     * </p>
     *
     * @param url a {@link java.lang.String} object.
     * @param params a {@link java.util.Map} object.
     * @param cookies a {@link java.util.Map} object.
     * @return a {@link java.lang.String} object.
     * @throws org.apache.http.client.ClientProtocolException if any.
     * @throws java.io.IOException if any.
     * @throws io.goobi.viewer.exceptions.HTTPException if any.
     */
    public static String getWebContentPOST(String url, Map<String, String> params, Map<String, String> cookies)
            throws ClientProtocolException, IOException, HTTPException {
        if (url == null) {
            throw new IllegalArgumentException("url may not be null");
        }

        logger.trace("url: {}", url);
        List<NameValuePair> nameValuePairs = null;
        if (params == null) {
            nameValuePairs = new ArrayList<>(0);
        } else {
            nameValuePairs = new ArrayList<>(params.size());
            for (String key : params.keySet()) {
                // logger.trace("param: {}:{}", key, params.get(key)); // TODO do not log passwords!
                nameValuePairs.add(new BasicNameValuePair(key, params.get(key)));
            }
        }
        HttpClientContext context = null;
        CookieStore cookieStore = new BasicCookieStore();
        if (cookies != null && !cookies.isEmpty()) {
            context = HttpClientContext.create();
            for (String key : cookies.keySet()) {
                // logger.trace("cookie: {}:{}", key, cookies.get(key)); // TODO do not log passwords!
                BasicClientCookie cookie = new BasicClientCookie(key, cookies.get(key));
                cookie.setPath("/");
                cookie.setDomain("0.0.0.0");
                cookieStore.addCookie(cookie);
            }
            context.setCookieStore(cookieStore);
        }

        RequestConfig defaultRequestConfig = RequestConfig.custom()
                .setSocketTimeout(HTTP_TIMEOUT)
                .setConnectTimeout(HTTP_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_TIMEOUT)
                .build();
        try (CloseableHttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(defaultRequestConfig).build()) {
            HttpPost post = new HttpPost(url);
            Charset.forName(StringTools.DEFAULT_ENCODING);
            post.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            try (CloseableHttpResponse response = (context == null ? httpClient.execute(post) : httpClient.execute(post, context));
                    StringWriter writer = new StringWriter()) {
                int code = response.getStatusLine().getStatusCode();
                if (code == HttpStatus.SC_OK) {
                    logger.trace("{}: {}", code, response.getStatusLine().getReasonPhrase());
                    return EntityUtils.toString(response.getEntity(), StringTools.DEFAULT_ENCODING);
                    //                    IOUtils.copy(response.getEntity().getContent(), writer, DEFAULT_ENCODING);
                    //                    return writer.toString();
                }
                logger.trace("{}: {}\n{}", code, response.getStatusLine().getReasonPhrase(),
                        IOUtils.toString(response.getEntity().getContent(), StringTools.DEFAULT_ENCODING));
                throw new HTTPException(code, response.getStatusLine().getReasonPhrase());
            }
        }
    }

    /**
     * Sends an email to with the given subject and body to the given recipient list.
     *
     * @param recipients a {@link java.util.List} object.
     * @param subject a {@link java.lang.String} object.
     * @param body a {@link java.lang.String} object.
     * @return a boolean.
     * @throws java.io.UnsupportedEncodingException if any.
     * @throws javax.mail.MessagingException if any.
     */
    public static boolean postMail(List<String> recipients, String subject, String body) throws UnsupportedEncodingException, MessagingException {
        return postMail(recipients, subject, body, DataManager.getInstance().getConfiguration().getSmtpServer(),
                DataManager.getInstance().getConfiguration().getSmtpUser(), DataManager.getInstance().getConfiguration().getSmtpPassword(),
                DataManager.getInstance().getConfiguration().getSmtpSenderAddress(), DataManager.getInstance().getConfiguration().getSmtpSenderName(),
                DataManager.getInstance().getConfiguration().getSmtpSecurity());
    }

    /**
     * Sends an email to with the given subject and body to the given recipient list using the given SMTP parameters.
     *
     * @param recipients
     * @param subject
     * @param body
     * @param smtpServer
     * @param smtpUser
     * @param smtpPassword
     * @param smtpSenderAddress
     * @param smtpSenderName
     * @param smtpSecurity
     * @throws MessagingException
     * @throws UnsupportedEncodingException
     */
    private static boolean postMail(List<String> recipients, String subject, String body, String smtpServer, final String smtpUser,
            final String smtpPassword, String smtpSenderAddress, String smtpSenderName, String smtpSecurity)
            throws MessagingException, UnsupportedEncodingException {
        if (recipients == null) {
            throw new IllegalArgumentException("recipients may not be null");
        }
        if (subject == null) {
            throw new IllegalArgumentException("subject may not be null");
        }
        if (body == null) {
            throw new IllegalArgumentException("body may not be null");
        }
        if (smtpServer == null) {
            throw new IllegalArgumentException("smtpServer may not be null");
        }
        if (smtpSenderAddress == null) {
            throw new IllegalArgumentException("smtpSenderAddress may not be null");
        }
        if (smtpSenderName == null) {
            throw new IllegalArgumentException("smtpSenderName may not be null");
        }
        if (smtpSecurity == null) {
            throw new IllegalArgumentException("smtpSecurity may not be null");
        }

        if (StringUtils.isNotEmpty(smtpPassword) && StringUtils.isEmpty(smtpUser)) {
            logger.warn("stmpPassword is configured but smtpUser is not, ignoring smtpPassword.");
        }

        boolean debug = false;
        boolean auth = true;
        if (StringUtils.isEmpty(smtpUser)) {
            auth = false;
        }
        String security = DataManager.getInstance().getConfiguration().getSmtpSecurity();
        Properties props = new Properties();
        switch (security.toUpperCase()) {
            case "STARTTLS":
                logger.debug("Using STARTTLS");
                props.setProperty("mail.transport.protocol", "smtp");
                props.setProperty("mail.smtp.port", "25");
                props.setProperty("mail.smtp.host", smtpServer);
                props.setProperty("mail.smtp.ssl.trust", "*");
                props.setProperty("mail.smtp.starttls.enable", "true");
                props.setProperty("mail.smtp.starttls.required", "true");
                break;
            case "SSL":
                logger.debug("Using SSL");
                props.setProperty("mail.transport.protocol", "smtp");
                props.setProperty("mail.smtp.host", smtpServer);
                props.setProperty("mail.smtp.port", "465");
                props.setProperty("mail.smtp.ssl.enable", "true");
                props.setProperty("mail.smtp.ssl.trust", "*");
                break;
            default:
                logger.debug("Using no SMTP security");
                props.setProperty("mail.transport.protocol", "smtp");
                props.setProperty("mail.smtp.port", "25");
                props.setProperty("mail.smtp.host", smtpServer);
        }
        props.setProperty("mail.smtp.connectiontimeout", "15000");
        props.setProperty("mail.smtp.timeout", "15000");
        props.setProperty("mail.smtp.auth", String.valueOf(auth));
        // logger.trace(props.toString());

        Session session;
        if (auth) {
            // with authentication
            session = Session.getInstance(props, new javax.mail.Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(smtpUser, smtpUser);
                }
            });
        } else {
            // w/o authentication
            session = Session.getInstance(props, null);
        }
        session.setDebug(debug);

        Message msg = new MimeMessage(session);
        InternetAddress addressFrom = new InternetAddress(smtpSenderAddress, smtpSenderName);
        msg.setFrom(addressFrom);
        InternetAddress[] addressTo = new InternetAddress[recipients.size()];
        int i = 0;
        for (String recipient : recipients) {
            addressTo[i] = new InternetAddress(recipient);
            i++;
        }
        msg.setRecipients(Message.RecipientType.TO, addressTo);
        // Optional : You can also set your custom headers in the Email if you
        // Want
        // msg.addHeader("MyHeaderName", "myHeaderValue");
        msg.setSubject(subject);
        {
            // Message body
            MimeBodyPart messagePart = new MimeBodyPart();
            messagePart.setText(body, "utf-8");
            // messagePart.setHeader("Content-Type", "text/plain; charset=\"utf-8\"");
            messagePart.setHeader("Content-Type", "text/html; charset=\"utf-8\"");
            MimeMultipart multipart = new MimeMultipart();
            multipart.addBodyPart(messagePart);
            msg.setContent(multipart);
        }
        msg.setSentDate(new Date());
        Transport.send(msg);

        return true;
    }

    /**
     * Returns the remote IP address of the given HttpServletRequest. If multiple addresses are found in x-forwarded-for, the last in the list is
     * returned.
     *
     * @param request a {@link javax.servlet.http.HttpServletRequest} object.
     * @return a {@link java.lang.String} object.
     */
    public static String getIpAddress(HttpServletRequest request) {
        String address = ADDRESS_LOCALHOST_IPV4;
        if (request != null) {
            //            if (logger.isTraceEnabled()) {
            //                Enumeration<String> headerNames = request.getHeaderNames();
            //                while (headerNames.hasMoreElements()) {
            //                    String headerName = headerNames.nextElement();
            //                    logger.trace("request header '{}':'{}'", headerName, request.getHeader(headerName));
            //                }
            //            }

            // Prefer address from x-forwarded-for
            address = request.getHeader("x-forwarded-for");
            if (address == null) {
                address = request.getHeader("X-Forwarded-For");
            }
            if (address == null) {
                address = request.getRemoteAddr();
            }
        }

        if (address == null) {
            address = ADDRESS_LOCALHOST_IPV4;
            logger.warn("Could not extract remote IP address, using localhost.");
        }

        // logger.trace("Pre-parsed IP address(es): {}", address);
        return

        parseMultipleIpAddresses(address);
    }

    /**
     * <p>
     * parseMultipleIpAddresses.
     * </p>
     *
     * @param address a {@link java.lang.String} object.
     * @should filter multiple addresses correctly
     * @return a {@link java.lang.String} object.
     */
    protected static String parseMultipleIpAddresses(String address) {
        if (address == null) {
            throw new IllegalArgumentException("address may not be null");
        }

        if (address.contains(",")) {
            String[] addressSplit = address.split(",");
            if (addressSplit.length > 0) {
                address = addressSplit[addressSplit.length - 1].trim();
            }
        }

        // logger.trace("Parsed IP address: {}", address);
        return address;
    }

    /**
     * Replaces most of the given email address with asterisks.
     * 
     * @param email
     * @return Scrambled email address
     * @should modify string correctly
     */
    public static String scrambleEmailAddress(String email) {
        if (StringUtils.isEmpty(email)) {
            return email;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < email.length(); ++i) {
            if (i > 2 && i < email.length() - 3) {
                sb.append('*');
            } else {
                sb.append(email.charAt(i));
            }
        }

        return sb.toString();
    }

    /**
     * Replaces most the last two segments of the given IPv4 address with placeholders.
     * 
     * @param address
     * @return Scrambled IP address
     * @should modify string correctly
     */
    public static String scrambleIpAddress(String address) {
        if (StringUtils.isEmpty(address)) {
            return address;
        }

        String[] addressSplit = address.split("[.]");
        if (addressSplit.length == 4) {
            return addressSplit[0] + "." + addressSplit[1] + ".X.X";
        }

        return address;
    }

    /**
     * 
     * @param address
     * @return
     */
    public static boolean isIpAddressLocalhost(String address) {
        if (address == null) {
            return false;
        }

        return ADDRESS_LOCALHOST_IPV6.equals(address) || ADDRESS_LOCALHOST_IPV4.equals(address);
    }
}
