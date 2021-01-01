/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import com.foilen.infra.api.model.resource.ResourceBucket;
import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.request.RequestResourceToUpdate;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.apachephp.ApachePhp;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.website.Website;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.StringTools;

@ShellComponent
public class PhpCommands extends AbstractBasics {

    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;

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

    @ShellMethod("List the PHP applications with their versions")
    public void phpListApplication() {

        // Get the list of PHP applications
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(ApachePhp.RESOURCE_TYPE));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }

        resourceBuckets.getItems().forEach(resourceBucket -> {
            ApachePhp apachePhp = JsonTools.clone(resourceBucket.getResourceDetails().getResource(), ApachePhp.class);
            System.out.println(apachePhp.getName() + " " + apachePhp.getVersion());

            // Show URL
            resourceBucket.getLinksFrom().stream() //
                    .filter(it -> LinkTypeConstants.POINTS_TO.equals(it.getLinkType())) //
                    .map(it -> it.getOtherResource()) //
                    .filter(it -> Website.RESOURCE_TYPE.equals(it.getResourceType())) //
                    .forEach(it -> {
                        Website website = JsonTools.clone(it.getResource(), Website.class);

                        website.getDomainNames().stream().sorted() //
                                .forEach(domainName -> {
                                    System.out.print("\tURL: ");
                                    if (website.isHttps()) {
                                        System.out.print("https://");
                                    } else {
                                        System.out.print("http://");
                                    }
                                    System.out.println(domainName);
                                });

                    });

            // Show installed on machines
            resourceBucket.getLinksTo().stream() //
                    .filter(it -> LinkTypeConstants.INSTALLED_ON.equals(it.getLinkType())) //
                    .map(it -> it.getOtherResource()) //
                    .filter(it -> "Machine".equals(it.getResourceType())) //
                    .map(it -> JsonTools.clone(it.getResource(), Machine.class)) //
                    .sorted((a, b) -> a.getName().compareTo(b.getName())) //
                    .forEach(machine -> {
                        System.out.println("\tMachine: " + machine.getName());
                    });
        });

    }

    @ShellMethod("List the PHP applications with their versions sorted by versions")
    public void phpListApplicationByVersion() {

        // Get the list of PHP applications
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(ApachePhp.RESOURCE_TYPE));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }

        Map<String, List<ResourceBucket>> sitesByVersion = resourceBuckets.getItems().stream() //
                .collect(Collectors.groupingBy(resourceBucket -> {
                    ApachePhp apachePhp = JsonTools.clone(resourceBucket.getResourceDetails().getResource(), ApachePhp.class);
                    return apachePhp.getVersion();
                }));

        sitesByVersion.entrySet().stream() //
                .sorted((a, b) -> a.getKey().compareTo(b.getKey())) //
                .forEach(e -> {
                    String version = e.getKey();
                    System.out.println("---[ " + version + " ]---");
                    e.getValue().forEach(resourceBucket -> {
                        ApachePhp apachePhp = JsonTools.clone(resourceBucket.getResourceDetails().getResource(), ApachePhp.class);
                        System.out.println("\t" + apachePhp.getName() + " " + apachePhp.getVersion());

                        // Show URL
                        resourceBucket.getLinksFrom().stream() //
                                .filter(it -> LinkTypeConstants.POINTS_TO.equals(it.getLinkType())) //
                                .map(it -> it.getOtherResource()) //
                                .filter(it -> Website.RESOURCE_TYPE.equals(it.getResourceType())) //
                                .forEach(it -> {
                                    Website website = JsonTools.clone(it.getResource(), Website.class);

                                    website.getDomainNames().stream().sorted() //
                                            .forEach(domainName -> {
                                                System.out.print("\t\tURL: ");
                                                if (website.isHttps()) {
                                                    System.out.print("https://");
                                                } else {
                                                    System.out.print("http://");
                                                }
                                                System.out.println(domainName);
                                            });

                                });

                        // Show installed on machines
                        resourceBucket.getLinksTo().stream() //
                                .filter(it -> LinkTypeConstants.INSTALLED_ON.equals(it.getLinkType())) //
                                .map(it -> it.getOtherResource()) //
                                .filter(it -> "Machine".equals(it.getResourceType())) //
                                .map(it -> JsonTools.clone(it.getResource(), Machine.class)) //
                                .sorted((a, b) -> a.getName().compareTo(b.getName())) //
                                .forEach(machine -> {
                                    System.out.println("\t\tMachine: " + machine.getName());
                                });

                        System.out.println();

                    });

                });

    }

    @ShellMethod("Update the PHP applications")
    public void phpUpdate( //
            @ShellOption(help = "Update only the ApachePhp resources that currently use that version") String fromVersion, //
            String toVersion //
    ) {

        // Get the list of PHP applications
        System.out.println("Find the resources with PHP version " + fromVersion);
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        RequestResourceSearch requestResourceSearch = new RequestResourceSearch().setResourceType(ApachePhp.RESOURCE_TYPE);
        // TODO When supported by the plugin - requestResourceSearch.getProperties().put(ApachePhp.PROPERTY_VERSION, fromVersion);
        ResponseResourceBuckets resourceBuckets = infraResourceApiService.resourceFindAllWithDetails(requestResourceSearch);
        exceptionService.displayResultAndThrow(resourceBuckets, "Find the resources with PHP version " + fromVersion);

        RequestChanges changes = new RequestChanges();
        resourceBuckets.getItems().stream() //
                .map(resourceBucket -> JsonTools.clone(resourceBucket.getResourceDetails().getResource(), ApachePhp.class)) //
                .filter(apachePhp -> StringTools.safeEquals(fromVersion, apachePhp.getVersion())) // TODO Remove when can search directly
                .forEach(apachePhp -> {

                    System.out.println(apachePhp.getName());

                    // Change the version
                    apachePhp.setVersion(toVersion);
                    ResourceDetails resourceDetails = new ResourceDetails(ApachePhp.RESOURCE_TYPE, apachePhp);
                    changes.getResourcesToUpdate().add(new RequestResourceToUpdate(resourceDetails, resourceDetails));

                    // Update in batch of 10
                    if (changes.getResourcesToUpdate().size() >= 10) {
                        ResponseResourceAppliedChanges resourceAppliedChanges = infraResourceApiService.applyChanges(changes);
                        exceptionService.displayResult(resourceAppliedChanges, "Applying update");
                        changes.getResourcesToUpdate().clear();
                    }
                });

        // If some pending
        if (!changes.getResourcesToUpdate().isEmpty()) {
            ResponseResourceAppliedChanges resourceAppliedChanges = infraResourceApiService.applyChanges(changes);
            exceptionService.displayResult(resourceAppliedChanges, "Applying update");
        }

    }

}
