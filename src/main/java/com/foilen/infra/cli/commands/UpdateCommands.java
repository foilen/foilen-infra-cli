/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2018 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.api.model.ResourceDetails;
import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.request.RequestResourceToUpdate;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.model.ApiProfile;
import com.foilen.infra.cli.model.OnlineFileDetails;
import com.foilen.infra.cli.services.BintrayService;
import com.foilen.infra.cli.services.DockerHubService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.resource.infraconfig.InfraConfig;
import com.foilen.infra.resource.infraconfig.InfraConfigPlugin;
import com.foilen.smalltools.restapi.model.FormResult;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.StringTools;

@ShellComponent
public class UpdateCommands {

    private static final String RESOURCE_TYPE_INFRASTRUCTURE_CONFIGURATION = "Infrastructure Configuration";
    private static final String RESOURCE_TYPE_INFRASTRUCTURE_PLUGIN = "Infrastructure Plugin";

    private static final String SUPPORTED_PLUGIN_URI_START = "https://dl.bintray.com/foilen/maven/com/foilen/foilen-infra-resource-";

    @Autowired
    private BintrayService bintrayService;
    @Autowired
    private DockerHubService dockerHubService;
    @Autowired
    private ProfileService profileService;

    protected String getPluginNameFromUrl(String url) {
        return url.split("/")[7];
    }

    protected String getVersionFromUrl(String url) {
        return url.split("/")[8];
    }

    @ShellMethodAvailability
    public Availability isAvailable() {

        if (profileService.getTarget() == null) {
            return Availability.unavailable("you did not specify a target profile");
        }

        if (profileService.getTarget() instanceof ApiProfile) {
            return Availability.available();
        }

        return Availability.unavailable("the target profile is not of API type");
    }

    @ShellMethod("Update the Login and UI services and the UI plugins to the latest versions.")
    public void updateInfra() {
        InfraApiService infraApiService = profileService.getTargetInfraApiService();

        // Update Plugins
        ResponseResourceBuckets pluginsBuckets = infraApiService.getInfraResourceApiService().resourceFindAll(new RequestResourceSearch().setResourceType(RESOURCE_TYPE_INFRASTRUCTURE_PLUGIN));
        if (!pluginsBuckets.isSuccess()) {
            throw new RuntimeException("Could not get the list of plugins: " + JsonTools.compactPrint(pluginsBuckets));
        }
        List<InfraConfigPlugin> infraConfigPlugins = pluginsBuckets.getItems().stream() //
                .map(it -> JsonTools.clone(it.getResourceDetails().getResource(), InfraConfigPlugin.class)) //
                .collect(Collectors.toList());

        RequestChanges changes = new RequestChanges();

        infraConfigPlugins.forEach(it -> {
            String url = it.getUrl();
            if (url.startsWith(SUPPORTED_PLUGIN_URI_START)) {

                // Get latest version
                String pluginName = getPluginNameFromUrl(url);
                OnlineFileDetails onlineFileDetails = bintrayService.getLatestVersionBintray(pluginName);

                String currentVersion = getVersionFromUrl(url);
                if (StringTools.safeEquals(currentVersion, onlineFileDetails.getVersion())) {
                    System.out.println("[OK] " + pluginName + " : " + currentVersion);
                } else {
                    System.out.println("[UPDATE] " + pluginName + " : " + currentVersion + " -> " + onlineFileDetails.getVersion());
                    InfraConfigPlugin newInfraConfigPlugin = new InfraConfigPlugin();
                    newInfraConfigPlugin.setUrl(onlineFileDetails.getJarUrl());
                    changes.getResourcesToUpdate().add(new RequestResourceToUpdate( //
                            new ResourceDetails(RESOURCE_TYPE_INFRASTRUCTURE_PLUGIN, it), //
                            new ResourceDetails(RESOURCE_TYPE_INFRASTRUCTURE_PLUGIN, newInfraConfigPlugin) //
                    ));
                }

            }
        });

        // Update UI and login services
        OnlineFileDetails loginVersionDetails = dockerHubService.getLatestVersionDockerHub("foilen/foilen-login");
        OnlineFileDetails uiVersionDetails = dockerHubService.getLatestVersionDockerHub("foilen/foilen-infra-ui");

        ResponseResourceBucket infraConfigBucket = infraApiService.getInfraResourceApiService()
                .resourceFindOne(new RequestResourceSearch().setResourceType(RESOURCE_TYPE_INFRASTRUCTURE_CONFIGURATION));
        if (!infraConfigBucket.isSuccess() || infraConfigBucket.getItem() == null) {
            throw new RuntimeException("Could not get the infra config: " + JsonTools.compactPrint(infraConfigBucket));
        }
        InfraConfig infraConfig = JsonTools.clone(infraConfigBucket.getItem().getResourceDetails().getResource(), InfraConfig.class);

        boolean updateInfraConfig = false;
        if (StringTools.safeEquals(loginVersionDetails.getVersion(), infraConfig.getLoginVersion())) {
            System.out.println("[OK] Login Service : " + infraConfig.getLoginVersion());
        } else {
            System.out.println("[UPDATE] Login Service : " + infraConfig.getLoginVersion() + " -> " + loginVersionDetails.getVersion());
            updateInfraConfig = true;
        }
        if (StringTools.safeEquals(uiVersionDetails.getVersion(), infraConfig.getUiVersion())) {
            System.out.println("[OK] UI Service : " + infraConfig.getUiVersion());
        } else {
            System.out.println("[UPDATE] UI Service : " + infraConfig.getUiVersion() + " -> " + uiVersionDetails.getVersion());
            updateInfraConfig = true;
        }

        if (updateInfraConfig) {

            InfraConfig newInfraConfig = JsonTools.clone(infraConfig);
            newInfraConfig.setLoginVersion(loginVersionDetails.getVersion());
            newInfraConfig.setUiVersion(uiVersionDetails.getVersion());

            changes.getResourcesToUpdate().add(new RequestResourceToUpdate( //
                    new ResourceDetails(RESOURCE_TYPE_INFRASTRUCTURE_CONFIGURATION, infraConfig), //
                    new ResourceDetails(RESOURCE_TYPE_INFRASTRUCTURE_CONFIGURATION, newInfraConfig) //
            ));
        }

        // Execute changes if any
        if (changes.getResourcesToUpdate().isEmpty()) {
            System.out.println("Nothing to update");
        } else {
            System.out.println("Execute update");
            FormResult formResult = infraApiService.getInfraResourceApiService().applyChanges(changes);
            if (formResult.isSuccess()) {
                System.out.println("[SUCCESS]");
            } else {
                System.out.println("[ERROR]" + JsonTools.compactPrint(formResult));
            }

        }

    }

}
