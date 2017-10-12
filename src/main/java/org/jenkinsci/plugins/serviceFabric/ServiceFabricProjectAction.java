package org.jenkinsci.plugins.serviceFabric;

import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by prit8976 on 8/27/15.
 */
public class ServiceFabricProjectAction implements Action {

    private AbstractProject<?, ?> project;

    @Override
    public String getIconFileName() {
        return "/plugin/serviceFabric/img/project_icon.png";
    }

    @Override
    public String getDisplayName() {
        return "Service Fabric Deploy Status";
    }

    @Override
    public String getUrlName() {
        return "serviceFabricPA";
    }

    public AbstractProject<?, ?> getProject() {
        return this.project;
    }

    public String getProjectName() {
        return this.project.getName();
    }

    public String getProjectMessages() {
        // List<String> projectMessages = new ArrayList<String>();
        // List<? extends AbstractBuild<?, ?>> builds = project.getBuilds();
        // String projectMessage="";
        // final Class<ServiceFabricBuildAction> buildClass = ServiceFabricBuildAction.class;

        // for (AbstractBuild<?, ?> currentBuild : builds) {
        //     projectMessage = "Build #"+currentBuild.getAction(buildClass).getBuildNumber()
        //             +": "+currentBuild.getAction(buildClass).getCommand();
        //     projectMessages.add(projectMessage);
        // }
        // return projectMessages;
        return "";
    }

    ServiceFabricProjectAction(final AbstractProject<?, ?> project) {
        this.project = project;
    }
}
