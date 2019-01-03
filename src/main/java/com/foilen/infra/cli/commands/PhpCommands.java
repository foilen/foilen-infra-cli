/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.apachephp.ApachePhp;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.website.Website;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;

@ShellComponent
public class PhpCommands extends AbstractBasics {

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
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAll(new RequestResourceSearch().setResourceType(ApachePhp.RESOURCE_TYPE));
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

}
