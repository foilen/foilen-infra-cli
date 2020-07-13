/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

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
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.mariadb.MariaDBServer;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.StringTools;
import com.foilen.smalltools.tuple.Tuple2;

@ShellComponent
public class MariadbCommands extends AbstractBasics {

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

    @ShellMethod("List the MariaDB server with their versions")
    public void mariadbListServer() {

        // Get the list of applications
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(MariaDBServer.RESOURCE_TYPE));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }

        resourceBuckets.getItems().stream() //
                .map(resourceBucket -> new Tuple2<>(resourceBucket, JsonTools.clone(resourceBucket.getResourceDetails().getResource(), MariaDBServer.class))) //
                .sorted((a, b) -> a.getB().getName().compareTo(b.getB().getName())) //
                .forEach(t -> {
                    MariaDBServer mariaDBServer = t.getB();
                    System.out.println(mariaDBServer.getName() + " " + mariaDBServer.getVersion());

                    // Show installed on machines
                    t.getA().getLinksTo().stream() //
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

    @ShellMethod("List the MariaDB server with their versions sorted by versions")
    public void mariadbListServerByVersion() {

        // Get the list of MariaDB applications
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(MariaDBServer.RESOURCE_TYPE));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }

        Map<String, List<ResourceBucket>> serversByVersion = resourceBuckets.getItems().stream() //
                .collect(Collectors.groupingBy(resourceBucket -> {
                    MariaDBServer mariaDBServer = JsonTools.clone(resourceBucket.getResourceDetails().getResource(), MariaDBServer.class);
                    return mariaDBServer.getVersion();
                }));

        serversByVersion.entrySet().stream() //
                .sorted((a, b) -> a.getKey().compareTo(b.getKey())) //
                .forEach(e -> {
                    String version = e.getKey();
                    System.out.println("---[ " + version + " ]---");
                    e.getValue().stream() //
                            .map(resourceBucket -> new Tuple2<>(resourceBucket, JsonTools.clone(resourceBucket.getResourceDetails().getResource(), MariaDBServer.class))) //
                            .sorted((a, b) -> a.getB().getName().compareTo(b.getB().getName())) //
                            .forEach(t -> {
                                MariaDBServer mariaDBServer = t.getB();
                                System.out.println("\t" + mariaDBServer.getName() + " " + mariaDBServer.getVersion());

                                // Show installed on machines
                                t.getA().getLinksTo().stream() //
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

    @ShellMethod("Update the Mariadb Server")
    public void mariadbUpdate( //
            @ShellOption(help = "Update only the MariaDBServer resources that currently use that version") String fromVersion, //
            String toVersion //
    ) {

        // Get the list of MariaDB applications
        System.out.println("Find the resources with MariaDB version " + fromVersion);
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        RequestResourceSearch requestResourceSearch = new RequestResourceSearch().setResourceType(MariaDBServer.RESOURCE_TYPE);
        // TODO When supported by the plugin - requestResourceSearch.getProperties().put(MariaDBServer.PROPERTY_VERSION, fromVersion);
        ResponseResourceBuckets resourceBuckets = infraResourceApiService.resourceFindAllWithDetails(requestResourceSearch);
        exceptionService.displayResultAndThrow(resourceBuckets, "Find the resources with MariaDB version " + fromVersion);

        RequestChanges changes = new RequestChanges();
        resourceBuckets.getItems().stream() //
                .map(resourceBucket -> JsonTools.clone(resourceBucket.getResourceDetails().getResource(), MariaDBServer.class)) //
                .filter(mariaDBServer -> StringTools.safeEquals(fromVersion, mariaDBServer.getVersion())) // TODO Remove when can search directly
                .forEach(mariaDBServer -> {

                    System.out.println(mariaDBServer.getName());

                    // Change the version
                    mariaDBServer.setVersion(toVersion);
                    ResourceDetails resourceDetails = new ResourceDetails(MariaDBServer.RESOURCE_TYPE, mariaDBServer);
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
