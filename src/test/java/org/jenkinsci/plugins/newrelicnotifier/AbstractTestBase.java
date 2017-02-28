package org.jenkinsci.plugins.newrelicnotifier;

/*
 * #%L
 * New Relic Deployment Notifier Plugin
 * %%
 * Copyright (C) 2016 - 2017 Mads Mohr Christensen
 * %%
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
 * #L%
 */

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import jenkins.model.Jenkins;
import org.apache.http.HttpStatus;
import org.jenkinsci.plugins.newrelicnotifier.api.NewRelicClientImpl;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

/**
 * Created by mmchr on 2/28/17.
 */
public abstract class AbstractTestBase {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @ClassRule
    public static WireMockRule mockNewRelicApi = new WireMockRule(options().dynamicPort());

    private String username = "username";
    protected String password = "apiKey";
    protected String credentialsId = "NewRelicCredential";

    protected final static String applicationResponse = "{\"applications\":[{\"id\":\"applicationId\",\"name\":\"test\"}]}";

    @Before
    public void setupCredentials() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                UsernamePasswordCredentialsImpl credentials = new UsernamePasswordCredentialsImpl(
                        CredentialsScope.GLOBAL, credentialsId, "test", username, password);

                CredentialsProvider.lookupStores(story.j.getInstance()).iterator().next()
                        .addCredentials(Domain.global(), credentials);
            }
        });
    }

    @Before
    public void setupMock() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                NewRelicDeploymentNotifier.DescriptorImpl descriptor = Jenkins.getInstance().getDescriptorByType(
                        NewRelicDeploymentNotifier.DescriptorImpl.class
                );
                descriptor.getClient().setApiEndpoint("http://127.0.0.1:" + mockNewRelicApi.port());

                // application list
                mockNewRelicApi.stubFor(get(urlEqualTo(NewRelicClientImpl.APPLICATIONS_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo(password))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(aResponse()
                            .withStatus(HttpStatus.SC_OK)
                            .withBody(applicationResponse)));

                // deployment notification
                mockNewRelicApi.stubFor(post(urlEqualTo(NewRelicClientImpl.DEPLOYMENT_ENDPOINT))
                        .withHeader("X-Api-Key", equalTo(password))
                        .withHeader("Accept", equalTo("application/json"))
                        .willReturn(aResponse()
                                .withStatus(HttpStatus.SC_CREATED)));
            }
        });
    }
}
