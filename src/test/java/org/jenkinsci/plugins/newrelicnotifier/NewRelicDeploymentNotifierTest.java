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

import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import org.jenkinsci.plugins.newrelicnotifier.api.NewRelicClientImpl;
import org.junit.Test;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;

public class NewRelicDeploymentNotifierTest extends AbstractTestBase{

    @Test
    public void missingNotifications() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                FreeStyleProject p = story.j.createFreeStyleProject();
                List<DeploymentNotificationBean> notifications = new ArrayList<>();

                NewRelicDeploymentNotifier notifier = new NewRelicDeploymentNotifier(notifications);

                p.getPublishersList().add(notifier);
                FreeStyleBuild build = p.scheduleBuild2(0).get();

                story.j.assertBuildStatus(Result.FAILURE, build);
                story.j.assertLogContains("FATAL: Missing notifications!", build);
            }
        });
    }

    @Test
    public void freestyleProjectNotifier() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                FreeStyleProject p = story.j.createFreeStyleProject();

                List<DeploymentNotificationBean> notifications = new ArrayList<>();
                DeploymentNotificationBean notificationBean = new DeploymentNotificationBean(
                        credentialsId, "applicationId", "description", "revision", "changelog", "user"
                );
                notifications.add(notificationBean);

                NewRelicDeploymentNotifier notifier = new NewRelicDeploymentNotifier(notifications);

                p.getPublishersList().add(notifier);
                FreeStyleBuild b = p.scheduleBuild2(0).get();
                story.j.assertBuildStatus(Result.SUCCESS, b);

                verify(postRequestedFor(urlMatching(NewRelicClientImpl.DEPLOYMENT_ENDPOINT))
                        .withHeader("X-Api-Key", matching(password))
                        .withHeader("Accept", matching("application/json"))
                );
            }
        });
    }
}
