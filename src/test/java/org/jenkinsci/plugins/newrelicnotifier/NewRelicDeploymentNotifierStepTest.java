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

import org.jenkinsci.plugins.newrelicnotifier.api.NewRelicClientImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.StepConfigTester;
import org.junit.Test;
import org.junit.runners.model.Statement;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class NewRelicDeploymentNotifierStepTest extends AbstractTestBase {

    @Test
    public void configRoundTrip() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                NewRelicDeploymentNotifierStep step1 = new NewRelicDeploymentNotifierStep(credentialsId, "applicationId");
                step1.setChangelog("changelog");
                step1.setDescription("description");
                step1.setRevision("revision");
                step1.setUser("user");

                NewRelicDeploymentNotifierStep step2 = new StepConfigTester(story.j).configRoundTrip(step1);
                story.j.assertEqualDataBoundBeans(step1, step2);

                verify(getRequestedFor(urlMatching(NewRelicClientImpl.APPLICATIONS_ENDPOINT))
                        .withHeader("X-Api-Key", matching(password))
                        .withHeader("Accept", matching("application/json"))
                );
            }
        });
    }

    @Test
    public void testSendNotification() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "node() {\n" +
                        "  notifyNewRelic apiKey: '"+credentialsId+"', applicationId: 'applicationId'\n" +
                        "}", true
                ));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));

                verify(postRequestedFor(urlMatching(NewRelicClientImpl.DEPLOYMENT_ENDPOINT))
                        .withHeader("X-Api-Key", matching(password))
                        .withHeader("Accept", matching("application/json"))
                );
            }
        });
    }

}
