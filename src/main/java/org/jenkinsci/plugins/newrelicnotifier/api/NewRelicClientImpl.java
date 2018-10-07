/*
 * The MIT License
 *
 * Copyright (c) 2015, Mads Mohr Christensen
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.newrelicnotifier.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import hudson.ProxyConfiguration;
import hudson.model.BuildListener;
import jenkins.model.Jenkins;
import org.apache.http.*;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.message.BasicNameValuePair;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.*;
import java.nio.charset.Charset;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;

/**
 * REST client implementation for the New Relic API.
 */
public class NewRelicClientImpl implements NewRelicClient {

    private static final String API_URL = "https://api.newrelic.com";

    private static final String DEPLOYMENT_ENDPOINT = "/deployments.json";

    private static final String APPLICATIONS_ENDPOINT = "/v2/applications.json";

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Application> getApplications(String apiKey) throws IOException {
        List<Application> result = new ArrayList<>();
        URI url = null;
        try {
            url = new URI(API_URL + APPLICATIONS_ENDPOINT);
        } catch (URISyntaxException e) {
            // no need to handle this
        }
        HttpGet request = new HttpGet(url);
        setHeaders(request, apiKey);
        CloseableHttpClient client = getHttpClient(url);
        ResponseHandler<ApplicationList> rh = new ResponseHandler<ApplicationList>() {
            @Override
            public ApplicationList handleResponse(HttpResponse response) throws IOException {
                StatusLine statusLine = response.getStatusLine();
                if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
                    throw new HttpResponseException(
                            statusLine.getStatusCode(),
                            statusLine.getReasonPhrase()
                    );
                }
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    throw new ClientProtocolException("Response contains no content");
                }
                Gson gson = new GsonBuilder().create();
                Reader reader = new InputStreamReader(entity.getContent(), Charset.forName("UTF-8"));
                return gson.fromJson(reader, ApplicationList.class);
            }
        };
        try {
            ApplicationList response = client.execute(request, rh);
            result.addAll(response.getApplications());
        } finally {
            if (client != null) {
                client.close();
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean sendNotification(String apiKey, String applicationId, String description, String revision, String changelog, String user, BuildListener listener) throws IOException {
        URI url = null;
        try {
            String appUrl = "/v2/applications/" + applicationId;
            url = new URI(API_URL + appUrl + DEPLOYMENT_ENDPOINT);
        } catch (URISyntaxException e) {
            // no need to handle this
        }

        // Check lengths of the parameters to ensure they don't exceed. More info over here - https://docs.newrelic.com/docs/apm/new-relic-apm/maintenance/record-deployments#deployment_limits
        if (revision == null || revision.length() >= 127) {
            throw new InvalidParameterException("The length of Revision should be less than 127 characters.");
        } else if (changelog.length() >= 65535) {
            throw new InvalidParameterException("The length of changelog should be less than 65535.");
        } else if (description.length() >= 65535) {
            throw new InvalidParameterException("The length of description should be less than 65535.");
        } else if (user.length() >= 31) {
            throw new InvalidParameterException("The length of user should be less than 31.");
        }

        HttpPost request = new HttpPost(url);
        request.setHeader("X-Api-Key", apiKey);
        request.setHeader("Content-Type", "application/json");

        JsonObject deployment = new JsonObject();
        JsonObject deploymentProperties = new JsonObject();
        deploymentProperties.addProperty("revision", revision);
        deploymentProperties.addProperty("changelog", changelog);
        deploymentProperties.addProperty("description", description);
        deploymentProperties.addProperty("user", user);

        deployment.add("deployment", deploymentProperties);
        
        String deploymentJsonString = deployment.toString().replace("\\\\", "");
        //deploymentJsonString = deploymentJsonString.replace("\\\\", "\\"); // This is because the new API supports Newlines and stringifying a JSON object escapes the newlines which make them useless in the Changelog section.
        listener.getLogger().println(deploymentJsonString);
        StringEntity entity = new StringEntity(deploymentJsonString,ContentType.APPLICATION_JSON);
        request.setEntity(entity);
        CloseableHttpClient client = getHttpClient(url);
        boolean result = false;
        try {
            CloseableHttpResponse response = client.execute(request);
            result = HttpStatus.SC_CREATED == response.getStatusLine().getStatusCode();
        } finally {
            if (client != null) {
                client.close();
            }
        }
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getApiEndpoint() {
        return API_URL;
    }

    protected CloseableHttpClient getHttpClient(URI url) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        Jenkins instance = Jenkins.getInstance();

        if (instance != null) {
            ProxyConfiguration proxyConfig = instance.proxy;
            if (proxyConfig != null) {
                Proxy proxy = proxyConfig.createProxy(url.getHost());
                if (proxy != null && proxy.type() == Proxy.Type.HTTP) {
                    SocketAddress addr = proxy.address();
                    if (addr != null && addr instanceof InetSocketAddress) {
                        InetSocketAddress proxyAddr = (InetSocketAddress) addr;
                        HttpHost proxyHost = new HttpHost(proxyAddr.getAddress().getHostAddress(), proxyAddr.getPort());
                        DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxyHost);
                        builder = builder.setRoutePlanner(routePlanner);

                        String proxyUser = proxyConfig.getUserName();
                        if (proxyUser != null) {
                            String proxyPass = proxyConfig.getPassword();
                            CredentialsProvider cred = new BasicCredentialsProvider();
                            cred.setCredentials(new AuthScope(proxyHost),
                                    new UsernamePasswordCredentials(proxyUser, proxyPass));
                            builder = builder
                                    .setDefaultCredentialsProvider(cred)
                                    .setProxyAuthenticationStrategy(new ProxyAuthenticationStrategy());
                        }
                    }
                }
            }
        }

        return builder.build();
    }

    private void setHeaders(HttpRequest request, String apiKey) {
        request.addHeader("X-Api-Key", apiKey);
        request.addHeader("Accept", "application/json");
    }

    private class ApplicationList {
        private List<Application> applications;

        public ApplicationList(List<Application> applications) {
            this.applications = applications;
        }

        public List<Application> getApplications() {
            return applications;
        }
    }
}
