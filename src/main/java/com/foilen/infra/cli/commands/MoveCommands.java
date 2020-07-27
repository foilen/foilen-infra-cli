/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.api.model.resource.LinkDetails;
import com.foilen.infra.api.model.resource.ResourceBucket;
import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.cli.services.SshService;
import com.foilen.infra.plugin.v1.model.resource.AbstractIPResource;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.apachephp.ApachePhp;
import com.foilen.infra.resource.application.Application;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.unixuser.UnixUser;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.StringTools;
import com.google.common.base.Joiner;

@ShellComponent
public class MoveCommands extends AbstractBasics {

    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private SshService sshService;

    @ShellMethodAvailability
    public Availability isAvailable() {

        if (profileService.getSource() == null) {
            return Availability.unavailable("you did not specify a source profile");
        }
        if (profileService.getTarget() == null) {
            return Availability.unavailable("you did not specify a target profile");
        }

        return Availability.available();
    }

    @SuppressWarnings("unchecked")
    @ShellMethod("Move the unix user to another host by syncing files and moving applications")
    public void moveUnixUser( //
            String username, //
            String sourceHostname, //
            String targetHostname //
    ) {

        // Ensure source and target profiles are the same
        if (!StringTools.safeEquals(profileService.getSource().getProfileName(), profileService.getTarget().getProfileName())) {
            throw new CliException("For now, the source and target profiles must be the same");
        }

        // Validate unixuser is on the source
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        RequestResourceSearch requestResourceSearch = new RequestResourceSearch().setResourceType(UnixUser.RESOURCE_TYPE);
        requestResourceSearch.getProperties().put(UnixUser.PROPERTY_NAME, username);
        ResponseResourceBucket unixUserBucket = infraResourceApiService.resourceFindOne(requestResourceSearch);
        if (!unixUserBucket.isSuccess() || unixUserBucket.getItem() == null) {
            throw new CliException("Could not get the Unix User: " + JsonTools.compactPrint(unixUserBucket));
        }

        UnixUser unixUser = resourceDetailsToResource(unixUserBucket.getItem().getResourceDetails(), UnixUser.class);
        List<String> unixUserAlreadyInstalledOnMachinesNames = unixUserBucket.getItem().getLinksTo().stream() //
                .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON)) //
                .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                .map(it -> resourceDetailsToResource(it.getOtherResource(), Machine.class).getInternalId()) //
                .map(machineInternalId -> {
                    ResponseResourceBucket machineBucket = infraResourceApiService.resourceFindById(machineInternalId);
                    if (!machineBucket.isSuccess() || machineBucket.getItem() == null) {
                        throw new CliException("Could not get the Machine: " + JsonTools.compactPrint(machineBucket));
                    }

                    return JsonTools.clone(machineBucket.getItem().getResourceDetails().getResource(), Machine.class).getName();
                }) //
                .sorted() //
                .collect(Collectors.toList());

        System.out.println("Unix user " + username + " is installed on machines:");
        unixUserAlreadyInstalledOnMachinesNames.forEach(machineName -> System.out.println("\t" + machineName));
        if (!unixUserAlreadyInstalledOnMachinesNames.contains(sourceHostname)) {
            throw new CliException("The unix user is not installed on the source machine");
        }

        // Check the applications that are using that unix user
        List<ResourceBucket> applicationsUsingTheUnixUserBucket = unixUserBucket.getItem().getLinksFrom().stream() //
                .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.RUN_AS)) //
                .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Application.RESOURCE_TYPE)) //
                .map(it -> resourceDetailsToResource(it.getOtherResource(), Application.class).getInternalId()) //
                .map(applicationInternalId -> {
                    ResponseResourceBucket applicationBucket = infraResourceApiService.resourceFindById(applicationInternalId);
                    if (!applicationBucket.isSuccess() || applicationBucket.getItem() == null) {
                        throw new CliException("Could not get the Application: " + JsonTools.compactPrint(applicationBucket));
                    }

                    return applicationBucket.getItem();
                }) //
                .sorted((a, b) -> {
                    String aName = ((Map<String, String>) a.getResourceDetails().getResource()).get("name");
                    String bName = ((Map<String, String>) b.getResourceDetails().getResource()).get("name");
                    return aName.compareTo(bName);
                }) //
                .collect(Collectors.toList());

        System.out.println("Unix user " + username + " is used by applications:");
        Iterator<ResourceBucket> applicationIt = applicationsUsingTheUnixUserBucket.iterator();
        AtomicBoolean cannotProceed = new AtomicBoolean();

        List<String> allApplicationNames = new ArrayList<>();
        List<ApachePhp> apachePhps = new ArrayList<>();
        while (applicationIt.hasNext()) {
            ResourceBucket applicationBucket = applicationIt.next();
            Application application = resourceDetailsToResource(applicationBucket.getResourceDetails(), Application.class);

            System.out.println("\t" + application.getName());

            // Filter out applications not installed on the source machine
            List<String> installedOnMachinesNames = applicationBucket.getLinksTo().stream() //
                    .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON)) //
                    .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                    .map(it -> resourceDetailsToResource(it.getOtherResource(), Machine.class).getInternalId()) //
                    .map(machineInternalId -> {
                        ResponseResourceBucket machineBucket = infraResourceApiService.resourceFindById(machineInternalId);
                        if (!machineBucket.isSuccess() || machineBucket.getItem() == null) {
                            throw new CliException("Could not get the Machine: " + JsonTools.compactPrint(machineBucket));
                        }

                        return resourceDetailsToResource(machineBucket.getItem().getResourceDetails(), Machine.class).getName();
                    }) //
                    .sorted() //
                    .collect(Collectors.toList());
            System.out.println("\t\tis installed on machines:");
            installedOnMachinesNames.forEach(machineName -> System.out.println("\t\t\t" + machineName));
            if (!installedOnMachinesNames.contains(sourceHostname)) {
                System.out.println("\t\t[SKIP] Not installed on the source machine");
                applicationIt.remove();
                continue;
            }

            // Stop if the application is not managed
            List<ResourceDetails> applicationManagedBy = applicationBucket.getLinksFrom().stream() //
                    .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.MANAGES)) //
                    .map(it -> it.getOtherResource()) //
                    .collect(Collectors.toList());
            System.out.println("\t\tis managed by:");
            if (applicationManagedBy.isEmpty()) {
                System.out.println("\t\t\t[STOP] The application is not managed by any known resource type");
                cannotProceed.set(true);
                continue;
            }
            if (applicationManagedBy.size() > 1) {
                System.out.println("\t\t\t[STOP] The application is managed by more than 1 resource");
                applicationManagedBy.forEach(managedBy -> System.out.println("\t\t\t\t" + managedBy.getResource()));
                cannotProceed.set(true);
                continue;
            }

            allApplicationNames.add(application.getName());
            ResourceDetails managedBy = applicationManagedBy.get(0);
            if (StringTools.safeEquals(managedBy.getResourceType(), ApachePhp.RESOURCE_TYPE)) {
                System.out.println("\t\t\t[OK] Resource Type: " + managedBy.getResourceType());
                ResponseResourceBucket responseResourceBucket = infraResourceApiService.resourceFindById(((Map<String, String>) managedBy.getResource()).get("internalId"));
                if (!responseResourceBucket.isSuccess() || responseResourceBucket.getItem() == null) {
                    throw new CliException("Could not get the managed by details: " + JsonTools.compactPrint(responseResourceBucket));
                }

                apachePhps.add(resourceDetailsToResource(responseResourceBucket.getItem().getResourceDetails(), ApachePhp.class));
                continue;
            }

            // Stop if we don't know how to handle any application
            cannotProceed.set(true);
            System.out.println("\t\t\t[NO] Doesn't know how to handle Resource Type: " + managedBy.getResourceType());

        }

        if (cannotProceed.get()) {
            throw new CliException("Cannot proceed");
        }

        // Install the unix user on the target
        System.out.println("Install the unix user on the target");
        RequestChanges changes = new RequestChanges();
        changes.getLinksToAdd()
                .add(new LinkDetails(new ResourceDetails(UnixUser.RESOURCE_TYPE, unixUser), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(targetHostname))));
        ResponseResourceAppliedChanges result = infraResourceApiService.applyChanges(changes);
        exceptionService.displayResultAndThrow(result, "Install the unix user on the target");

        // First sync
        System.out.println("Do the first sync to get most of the files in the final state");
        sshService.syncFiles(sourceHostname, username, targetHostname, username, null);

        // Wait user is created on target
        System.out.println("Wait user is created on target");
        sshService.waitUserIsPresent(targetHostname, username);

        // Remove the applications on the source
        System.out.println("Remove the applications on the source");
        changes = new RequestChanges();
        for (ApachePhp r : apachePhps) {
            changes.getLinksToDelete()
                    .add(new LinkDetails(new ResourceDetails(ApachePhp.RESOURCE_TYPE, r), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(sourceHostname))));
        }
        result = infraResourceApiService.applyChanges(changes);
        exceptionService.displayResultAndThrow(result, "Remove the applications on the source");

        // Stop the applications
        sshService.executeCommandInLoggerTarget(sourceHostname, "/usr/bin/docker stop " + Joiner.on(' ').join(allApplicationNames));

        // Final sync
        System.out.println("Do the last sync while the application is down");
        sshService.syncFiles(sourceHostname, username, targetHostname, username, null);

        // Install the applications on the target
        System.out.println("Install the applications on the target");
        changes = new RequestChanges();
        for (ApachePhp r : apachePhps) {
            changes.getLinksToAdd()
                    .add(new LinkDetails(new ResourceDetails(ApachePhp.RESOURCE_TYPE, r), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(targetHostname))));
        }
        result = infraResourceApiService.applyChanges(changes);
        exceptionService.displayResultAndThrow(result, "Install the applications on the target");

        // Remove the unix user from the source
        System.out.println("Remove the unix user from the source");
        changes = new RequestChanges();
        changes.getLinksToDelete()
                .add(new LinkDetails(new ResourceDetails(UnixUser.RESOURCE_TYPE, unixUser), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(sourceHostname))));
        result = infraResourceApiService.applyChanges(changes);
        exceptionService.displayResultAndThrow(result, "Remove the unix user from the source");

    }

    @SuppressWarnings("unchecked")
    private <T extends AbstractIPResource> T resourceDetailsToResource(ResourceDetails resourceDetails, Class<T> resourceType) {
        T resource = JsonTools.clone(resourceDetails.getResource(), resourceType);
        resource.setInternalId(((Map<String, String>) resourceDetails.getResource()).get("internalId"));
        return resource;
    }

}