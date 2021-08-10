/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.api.model.resource.PartialLinkDetails;
import com.foilen.infra.api.model.resource.ResourceBucket;
import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.services.InfraResourceUtils;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.CollectionsTools;
import com.foilen.smalltools.tools.StringTools;
import com.foilen.smalltools.tuple.Tuple2;

@ShellComponent
public class MachineCommands extends AbstractBasics {

    @Autowired
    private ProfileService profileService;

    @ShellMethodAvailability
    public Availability isAvailable() {

        if (profileService.getTarget() == null) {
            return Availability.unavailable("you did not specify a target profile");
        }

        return Availability.available();
    }

    @ShellMethod("List all resources installed on this machine")
    public void machineListInstalledResources( //
            String hostname //
    ) {

        // Get the machine
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        RequestResourceSearch search = new RequestResourceSearch().setResourceType(Machine.RESOURCE_TYPE);
        search.getProperties().put(Machine.PROPERTY_NAME, hostname);
        logger.info("Getting machine {}", hostname);
        ResponseResourceBucket responseResourceBucket = infraApiService.getInfraResourceApiService().resourceFindOne(search);

        if (!responseResourceBucket.isSuccess()) {
            throw new CliException(responseResourceBucket.getError());
        }

        // List what is installed
        ResourceBucket resourceBucket = responseResourceBucket.getItem();
        Map<String, List<String>> resourcesByUnixUserName = new HashMap<>();

        resourceBucket.getLinksFrom().stream() //
                .filter(link -> StringTools.safeEquals(LinkTypeConstants.INSTALLED_ON, link.getLinkType())) //
                .map(link -> {
                    // Get the unix user if any
                    String linkedFromResourceId = InfraResourceUtils.getResourceId(link.getOtherResource());
                    logger.info("Getting resource {}", linkedFromResourceId);
                    ResponseResourceBucket resourceOnMachine = infraApiService.getInfraResourceApiService().resourceFindById(linkedFromResourceId);
                    if (!resourceOnMachine.isSuccess()) {
                        throw new CliException(responseResourceBucket.getError());
                    }
                    Optional<PartialLinkDetails> unixUser = resourceOnMachine.getItem().getLinksTo().stream() //
                            .filter(unixUserLink -> StringTools.safeEquals(LinkTypeConstants.RUN_AS, unixUserLink.getLinkType())) //
                            .findAny();
                    String unixUserName = "N/A";
                    if (unixUser.isPresent()) {
                        unixUserName = InfraResourceUtils.getResourceName(unixUser.get().getOtherResource());
                    }
                    return new Tuple2<>(resourceOnMachine.getItem(), unixUserName);
                }) //
                .forEach(resourceAndUnixUser -> {
                    ResourceDetails otherResource = resourceAndUnixUser.getA().getResourceDetails();
                    String resourceName = InfraResourceUtils.getResourceName(otherResource);

                    CollectionsTools.getOrCreateEmptyArrayList(resourcesByUnixUserName, resourceAndUnixUser.getB(), String.class).add(otherResource.getResourceType() + " " + resourceName);
                });

        resourcesByUnixUserName.keySet().stream().sorted().forEach(unixUserName -> {
            List<String> resources = resourcesByUnixUserName.get(unixUserName);
            Collections.sort(resources);
            System.out.println(unixUserName);
            resources.forEach(resource -> System.out.println("\t" + resource));
        });

    }

}
