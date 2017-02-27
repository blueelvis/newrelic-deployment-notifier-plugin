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

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.*;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Notifies a New Relic instance about deployment.
 */
public class NewRelicDeploymentNotifierStep extends Step {

    private final String apiKey;
    private final String applicationId;
    private String description;
    private String revision;
    private String changelog;
    private String user;

    @DataBoundConstructor
    public NewRelicDeploymentNotifierStep(String apiKey, String applicationId) {
        super();
        this.apiKey = apiKey;
        this.applicationId = applicationId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = Util.fixEmptyAndTrim(description);
    }

    public String getRevision() {
        return revision;
    }

    @DataBoundSetter
    public void setRevision(String revision) {
        this.revision = Util.fixEmptyAndTrim(revision);
    }

    public String getChangelog() {
        return changelog;
    }

    @DataBoundSetter
    public void setChangelog(String changelog) {
        this.changelog = Util.fixEmptyAndTrim(changelog);
    }

    public String getUser() {
        return user;
    }

    @DataBoundSetter
    public void setUser(String user) {
        this.user = Util.fixEmptyAndTrim(user);
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new Execution(context, this);
    }

    protected List<DeploymentNotificationBean> getNotifications() {
        List<DeploymentNotificationBean> notifications = new ArrayList<>();
        notifications.add(new DeploymentNotificationBean(
                getApiKey(), getApplicationId(), getDescription(), getRevision(), getChangelog(), getUser()
        ));
        return notifications;
    }

    public static class Execution extends SynchronousNonBlockingStepExecution<Void> {
        private static final long serialVersionUID = 1L;

        private transient final NewRelicDeploymentNotifierStep step;

        protected Execution(@Nonnull StepContext context, NewRelicDeploymentNotifierStep step) {
            super(context);
            this.step = step;
        }

        @Override
        protected Void run() throws Exception {
            NewRelicDeploymentNotifier notifier = new NewRelicDeploymentNotifier(step.getNotifications());
            notifier.perform(
                    getContext().get(Run.class),
                    getContext().get(FilePath.class),
                    getContext().get(Launcher.class),
                    getContext().get(TaskListener.class)
            );
            return null;
        }
    }

    @Extension(optional = true)
    public static class DescriptorImpl extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, FilePath.class, Launcher.class, TaskListener.class);
        }

        @Override
        public String getFunctionName() {
            return "notifyNewRelic";
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return "Notifies a New Relic instance about deployment";
        }

        public ListBoxModel doFillApiKeyItems(@AncestorInPath Job<?, ?> owner) {
            return getDeploymentNotificationBeanDescriptor().doFillApiKeyItems(owner);
        }

        public FormValidation doCheckApiKey(@QueryParameter("apiKey") String apiKey) {
            return getDeploymentNotificationBeanDescriptor().doCheckApiKey(apiKey);
        }

        public ListBoxModel doFillApplicationIdItems(@AncestorInPath Job<?, ?> owner,
                                                     @QueryParameter("apiKey") final String apiKey) throws IOException {
            return getDeploymentNotificationBeanDescriptor().doFillApplicationIdItems(owner, apiKey);
        }

        public FormValidation doCheckApplicationId(@QueryParameter("applicationId") String applicationId) {
            return getDeploymentNotificationBeanDescriptor().doCheckApplicationId(applicationId);
        }

        private DeploymentNotificationBean.DescriptorImpl getDeploymentNotificationBeanDescriptor() {
            return Jenkins.getInstance().getDescriptorByType(DeploymentNotificationBean.DescriptorImpl.class);
        }
    }
}
