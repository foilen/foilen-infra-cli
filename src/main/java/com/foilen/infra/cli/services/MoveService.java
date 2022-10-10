/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.apachephp.ApachePhp;
import com.foilen.infra.resource.application.Application;
import com.foilen.infra.resource.composableapplication.ComposableApplication;
import com.foilen.infra.resource.domain.Domain;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.mariadb.MariaDBServer;
import com.foilen.infra.resource.mongodb.MongoDBServer;
import com.foilen.infra.resource.postgresql.PostgreSqlServer;
import com.foilen.infra.resource.unixuser.UnixUser;
import com.foilen.infra.resource.urlredirection.UrlRedirection;
import com.foilen.infra.resource.website.Website;
import com.foilen.smalltools.listscomparator.ListComparatorHandler;
import com.foilen.smalltools.listscomparator.ListsComparator;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.DateTools;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.StringTools;
import com.foilen.smalltools.tools.ThreadTools;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;

@Component
public class MoveService extends AbstractBasics {

    private static final List<String> INSTALLABLE_APPLICATION_TYPES = Arrays.asList( //
            ApachePhp.RESOURCE_TYPE, //
            ComposableApplication.RESOURCE_TYPE, //
            MariaDBServer.RESOURCE_TYPE, //
            MongoDBServer.RESOURCE_TYPE, //
            PostgreSqlServer.RESOURCE_TYPE //
    );

    @Autowired
    private CheckService checkService;
    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private SshService sshService;

    public void moveAllFromMachine(String sourceHostname, String targetHostname) {

        System.out.println("===[ Migrating all unix users ]===");
        moveAllUnixUser(sourceHostname, targetHostname, false);

        System.out.println("===[ Migrating all websites ]===");
        List<Thread> threads = moveAllWebsitesCloser(sourceHostname, targetHostname, false);

        System.out.println("===[ Waiting for all websites to be migrated ]===");
        for (int i = 0; i < threads.size(); ++i) {
            System.out.println("Waiting on " + (i + 1) + " / " + threads.size());
            Thread t = threads.get(i);
            try {
                t.join();
            } catch (InterruptedException e) {
            }
        }

        System.out.println("\n\n\n===[ List everything still on the machine ]===");
        checkService.listAllResourcesOnMachine(sourceHostname);

    }

    @SuppressWarnings("unchecked")
    public void moveAllUnixUser(String sourceHostname, String targetHostname, boolean stopOnFailure) {

        // Ensure source and target profiles are the same
        if (!StringTools.safeEquals(profileService.getSource().getProfileName(), profileService.getTarget().getProfileName())) {
            throw new CliException("For now, the source and target profiles must be the same");
        }

        // Get all the unix users on the source
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        ResponseResourceBucket sourceMachineBucket = infraResourceApiService.resourceFindOneByPk(new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(sourceHostname)));
        if (!sourceMachineBucket.isSuccess() || sourceMachineBucket.getItem() == null) {
            throw new CliException("Could not get the Machine: " + JsonTools.compactPrint(sourceMachineBucket));
        }

        List<String> usernames = sourceMachineBucket.getItem().getLinksFrom().stream() //
                .filter(it -> StringTools.safeEquals(UnixUser.RESOURCE_TYPE, it.getOtherResource().getResourceType())) //
                .filter(it -> StringTools.safeEquals(LinkTypeConstants.INSTALLED_ON, it.getLinkType())) //
                .map(it -> ((Map<String, String>) it.getOtherResource().getResource()).get("resourceName")) //
                .sorted() //
                .collect(Collectors.toList());

        // Prepare
        Map<String, String> resultByUsername = new TreeMap<>();
        usernames.forEach(it -> resultByUsername.put(it, "PENDING"));

        // Execute
        for (String username : usernames) {
            System.out.println("\n\n\n---> Processing unix user " + username);

            try {
                moveUnixUser(sourceHostname, targetHostname, username);
                resultByUsername.put(username, "OK");
            } catch (Exception e) {
                resultByUsername.put(username, "ERROR - " + e.getMessage());
                if (stopOnFailure) {
                    break;
                }
            }
        }

        // Show summary
        System.out.println("\n\n\n---[ Summary ]---");
        usernames.forEach(it -> System.out.println("[" + resultByUsername.get(it) + "] " + it));

    }

    public List<Thread> moveAllWebsitesCloser(String machineName, String redirectionOnlyMachine, boolean stopOnFailure) {

        // Ensure source and target profiles are the same
        if (!StringTools.safeEquals(profileService.getSource().getProfileName(), profileService.getTarget().getProfileName())) {
            throw new CliException("For now, the source and target profiles must be the same");
        }

        // Get the Machine
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        ResponseResourceBucket machineBucket = infraResourceApiService.resourceFindOneByPk(new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(machineName)));
        if (!machineBucket.isSuccess() || machineBucket.getItem() == null) {
            throw new CliException("Could not get the Machine: " + JsonTools.compactPrint(machineBucket));
        }

        // Get the domains of the installed websites
        List<String> domains = machineBucket.getItem().getLinksFrom().stream() //
                .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON) || StringTools.safeEquals(it.getLinkType(), "INSTALLED_ON_NO_DNS")) //
                .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Website.RESOURCE_TYPE)) //
                .map(it -> InfraResourceUtils.resourceDetailsToResource(it.getOtherResource(), Website.class).getInternalId()) //
                .map(websiteInternalId -> {
                    ResponseResourceBucket websiteBucket = infraResourceApiService.resourceFindById(websiteInternalId);
                    if (!websiteBucket.isSuccess() || websiteBucket.getItem() == null) {
                        throw new CliException("Could not get the Website: " + JsonTools.compactPrint(websiteBucket));
                    }

                    return InfraResourceUtils.resourceDetailsToResource(websiteBucket.getItem().getResourceDetails(), Website.class);
                }) //
                .peek(it -> System.out.println("Found Website: " + it.getName())) //
                .flatMap(website -> website.getDomainNames().stream()) //
                .sorted() //
                .distinct() //
                .collect(Collectors.toList());

        // Prepare
        Map<String, String> resultByDomain = new TreeMap<>();
        domains.forEach(it -> resultByDomain.put(it, "PENDING"));

        List<Thread> threads = new ArrayList<>();
        // Execute
        for (String domain : domains) {
            System.out.println("\n\n\n---> Processing domain " + domain);

            try {
                Thread thread = moveWebsiteCloser(domain, redirectionOnlyMachine);
                if (thread != null) {
                    threads.add(thread);
                }
                resultByDomain.put(domain, "OK");
            } catch (Exception e) {
                System.out.println(e);
                resultByDomain.put(domain, "ERROR - " + e.getMessage());
                if (stopOnFailure) {
                    break;
                }
            }

            ThreadTools.sleep(3000);
        }

        // Show summary
        System.out.println("\n\n\n---[ Summary ]---");
        domains.forEach(it -> System.out.println("[" + resultByDomain.get(it) + "] " + it));
        System.out.println("\nYou still need to wait for all the 10 minutes to be completed");

        return threads;
    }

    @SuppressWarnings({ "unchecked" })
    public void moveUnixUser(String sourceHostname, String targetHostname, String username) {

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

        UnixUser unixUser = InfraResourceUtils.resourceDetailsToResource(unixUserBucket.getItem().getResourceDetails(), UnixUser.class);
        List<String> unixUserAlreadyInstalledOnMachinesNames = unixUserBucket.getItem().getLinksTo().stream() //
                .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON)) //
                .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                .map(it -> InfraResourceUtils.resourceDetailsToResource(it.getOtherResource(), Machine.class).getInternalId()) //
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
                .map(it -> InfraResourceUtils.resourceDetailsToResource(it.getOtherResource(), Application.class).getInternalId()) //
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
        String cannotProceedMessage = null;

        List<String> allApplicationNames = new ArrayList<>();
        List<ResourceDetails> installableResourceDetails = new ArrayList<>();
        while (applicationIt.hasNext()) {
            ResourceBucket applicationBucket = applicationIt.next();
            Application application = InfraResourceUtils.resourceDetailsToResource(applicationBucket.getResourceDetails(), Application.class);

            System.out.println("\t" + application.getName() + " (owner: " + InfraResourceUtils.getOwner(application) + ")");

            // Filter out applications not installed on the source machine
            List<String> installedOnMachinesNames = applicationBucket.getLinksTo().stream() //
                    .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON)) //
                    .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                    .map(it -> InfraResourceUtils.resourceDetailsToResource(it.getOtherResource(), Machine.class).getInternalId()) //
                    .map(machineInternalId -> {
                        ResponseResourceBucket machineBucket = infraResourceApiService.resourceFindById(machineInternalId);
                        if (!machineBucket.isSuccess() || machineBucket.getItem() == null) {
                            throw new CliException("Could not get the Machine: " + JsonTools.compactPrint(machineBucket));
                        }

                        return InfraResourceUtils.resourceDetailsToResource(machineBucket.getItem().getResourceDetails(), Machine.class).getName();
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
                cannotProceedMessage = "The application is not managed by any known resource type";
                continue;
            }
            if (applicationManagedBy.size() > 1) {
                System.out.println("\t\t\t[STOP] The application is managed by more than 1 resource");
                applicationManagedBy.forEach(managedBy -> System.out.println("\t\t\t\t" + managedBy.getResource()));
                cannotProceedMessage = "The application is managed by more than 1 resource";
                continue;
            }

            allApplicationNames.add(application.getName());
            ResourceDetails managedBy = applicationManagedBy.get(0);
            if (INSTALLABLE_APPLICATION_TYPES.contains(managedBy.getResourceType())) {
                System.out.println("\t\t\t[OK] Resource Type: " + managedBy.getResourceType());
                ResponseResourceBucket responseResourceBucket = infraResourceApiService.resourceFindById(((Map<String, String>) managedBy.getResource()).get("internalId"));
                if (!responseResourceBucket.isSuccess() || responseResourceBucket.getItem() == null) {
                    throw new CliException("Could not get the managed by details: " + JsonTools.compactPrint(responseResourceBucket));
                }

                installableResourceDetails.add(responseResourceBucket.getItem().getResourceDetails());
                continue;
            }

            // Stop if we don't know how to handle any application
            cannotProceedMessage = "Doesn't know how to handle Resource Type: " + managedBy.getResourceType();
            System.out.println("\t\t\t[NO] Doesn't know how to handle Resource Type: " + managedBy.getResourceType());

        }

        if (cannotProceedMessage != null) {
            throw new CliException(cannotProceedMessage);
        }

        // Install the unix user on the target
        System.out.println("Install the unix user on the target");
        RequestChanges changes = new RequestChanges();
        changes.getLinksToAdd()
                .add(new LinkDetails(new ResourceDetails(UnixUser.RESOURCE_TYPE, unixUser), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(targetHostname))));
        ResponseResourceAppliedChanges result = infraResourceApiService.applyChanges(changes);
        exceptionService.displayResultAndThrow(result, "Install the unix user on the target");

        // Wait user is created on target
        System.out.println("Wait user is created on target");
        sshService.waitUserIsPresent(targetHostname, username);

        // First sync
        System.out.println("Do the first sync to get most of the files in the final state");
        sshService.syncFiles(sourceHostname, username, targetHostname, username, null);

        // Remove the applications on the source
        System.out.println("Remove the applications on the source");
        changes = new RequestChanges();
        for (ResourceDetails r : installableResourceDetails) {
            changes.getLinksToDelete().add(new LinkDetails(r, LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(sourceHostname))));
        }
        result = infraResourceApiService.applyChanges(changes);
        exceptionService.displayResultAndThrow(result, "Remove the applications on the source");

        // Stop the applications
        sshService.executeCommandInLoggerTarget(sourceHostname, "/usr/bin/docker stop " + Joiner.on(' ').join(allApplicationNames));

        // Final sync
        System.out.println("Do the last sync while the application is down");
        sshService.syncFiles(sourceHostname, username, targetHostname, username, null);

        // Install the applications on the target (per owner)
        System.out.println("Install the applications on the target");
        Map<String, List<ResourceDetails>> installableResourceDetailsByOwner = installableResourceDetails.stream() //
                .collect(Collectors.groupingBy(it -> InfraResourceUtils.getOwner(it)));
        installableResourceDetailsByOwner.forEach((owner, resourcesDetails) -> {
            System.out.println("Install the applications on the target of owner " + owner);
            RequestChanges requestChanges = new RequestChanges();
            requestChanges.setDefaultOwner(owner);
            for (ResourceDetails r : installableResourceDetails) {
                requestChanges.getLinksToAdd().add(new LinkDetails(r, LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(targetHostname))));
            }
            ResponseResourceAppliedChanges responseResourceAppliedChanges = infraResourceApiService.applyChanges(requestChanges);
            exceptionService.displayResultAndThrow(responseResourceAppliedChanges, "Install the applications on the target of owner " + owner);
        });

        // Remove the unix user from the source
        System.out.println("Remove the unix user from the source");
        changes = new RequestChanges();
        changes.getLinksToDelete()
                .add(new LinkDetails(new ResourceDetails(UnixUser.RESOURCE_TYPE, unixUser), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(sourceHostname))));
        result = infraResourceApiService.applyChanges(changes);
        exceptionService.displayResultAndThrow(result, "Remove the unix user from the source");

    }

    @SuppressWarnings("unchecked")
    public Thread moveWebsiteCloser(String domainName, String redirectionOnlyMachine) {

        // Ensure source and target profiles are the same
        if (!StringTools.safeEquals(profileService.getSource().getProfileName(), profileService.getTarget().getProfileName())) {
            throw new CliException("For now, the source and target profiles must be the same");
        }

        // Get the domain to find the Websites on it
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        ResponseResourceBucket domainBucket = infraResourceApiService.resourceFindOneByPk(new ResourceDetails(Domain.RESOURCE_TYPE, new Domain(domainName)));
        if (!domainBucket.isSuccess() || domainBucket.getItem() == null) {
            throw new CliException("Could not get the Domain: " + JsonTools.compactPrint(domainBucket));
        }

        List<ResourceBucket> websitesForDomain = domainBucket.getItem().getLinksFrom().stream() //
                .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.MANAGES)) //
                .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Website.RESOURCE_TYPE)) //
                .map(it -> InfraResourceUtils.resourceDetailsToResource(it.getOtherResource(), Website.class).getInternalId()) //
                .map(websiteInternalId -> {
                    ResponseResourceBucket websiteBucket = infraResourceApiService.resourceFindById(websiteInternalId);
                    if (!websiteBucket.isSuccess() || websiteBucket.getItem() == null) {
                        throw new CliException("Could not get the Website: " + JsonTools.compactPrint(websiteBucket));
                    }

                    return websiteBucket.getItem();
                }) //
                .collect(Collectors.toList());

        // Separate websites and get the applications
        List<ResourceBucket> applications = new ArrayList<>();
        List<ResourceBucket> urlRedirections = new ArrayList<>();
        List<ResourceBucket> directWebsites = new ArrayList<>();
        List<ResourceBucket> indirectWebsites = new ArrayList<>();
        websitesForDomain.forEach(website -> {

            // Separate websites
            if (website.getLinksFrom().stream().anyMatch(l -> StringTools.safeEquals(LinkTypeConstants.MANAGES, l.getLinkType()))) {

                indirectWebsites.add(website);

                // Get the UrlRedirections
                Website websiteResource = InfraResourceUtils.resourceDetailsToResource(website.getResourceDetails(), Website.class);

                website.getLinksFrom().stream() //
                        .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.MANAGES)) //
                        .peek(it -> {
                            if (!StringTools.safeEquals(it.getOtherResource().getResourceType(), UrlRedirection.RESOURCE_TYPE)) {
                                throw new CliException(
                                        "The website " + websiteResource.getName() + " is not managed by a known resource type. It is managed by " + it.getOtherResource().getResourceType());
                            }
                        }) //
                        .map(it -> InfraResourceUtils.resourceDetailsToResource(it.getOtherResource(), UrlRedirection.class).getInternalId()) //
                        .map(urlRedirectionInternalId -> {
                            ResponseResourceBucket urlRedirectionBucket = infraResourceApiService.resourceFindById(urlRedirectionInternalId);
                            if (!urlRedirectionBucket.isSuccess() || urlRedirectionBucket.getItem() == null) {
                                throw new CliException("Could not get the UrlRedirection: " + JsonTools.compactPrint(urlRedirectionBucket));
                            }

                            return urlRedirectionBucket.getItem();
                        }) //
                        .forEach(it -> urlRedirections.add(it));

            } else {

                directWebsites.add(website);

                // Get the list of applications where those websites are going
                website.getLinksTo().stream() //
                        .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.POINTS_TO)) //
                        .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Application.RESOURCE_TYPE))
                        .map(it -> InfraResourceUtils.resourceDetailsToResource(it.getOtherResource(), Application.class).getInternalId()) //
                        .map(applicationInternalId -> {
                            ResponseResourceBucket applicationBucket = infraResourceApiService.resourceFindById(applicationInternalId);
                            if (!applicationBucket.isSuccess() || applicationBucket.getItem() == null) {
                                throw new CliException("Could not get the Application: " + JsonTools.compactPrint(applicationBucket));
                            }

                            return applicationBucket.getItem();
                        }) //
                        .forEach(it -> applications.add(it));
            }

        });

        if (applications.isEmpty() && urlRedirections.isEmpty()) {
            throw new CliException("There are no applications or url redirections linked to these websites");
        }

        // Find all the Machines where the applications are installed (must be the same list from all the links)
        List<String> applicationInstalledOn;
        if (applications.isEmpty()) {
            if (Strings.isNullOrEmpty(redirectionOnlyMachine)) {
                throw new CliException("This domain only has UrlRedirection. You must provide the machine you want to move to");
            }
            applicationInstalledOn = Collections.singletonList(redirectionOnlyMachine);
        } else {
            List<List<String>> applicationInstalledOns = applications.stream() //
                    .map(application -> {
                        return application.getLinksTo().stream() //
                                .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON)) //
                                .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                                .map(it -> ((Map<String, String>) it.getOtherResource().getResource()).get("resourceName")) //
                                .sorted() //
                                .collect(Collectors.toList());
                    }).collect(Collectors.toList());
            applicationInstalledOn = applicationInstalledOns.get(0);
            if (applicationInstalledOns.stream().anyMatch(it -> !it.equals(applicationInstalledOn))) {
                throw new CliException("Not all the applications are installed on the same machines");
            }
        }

        // Check if the websites are already in the final desired state
        websitesForDomain.removeIf(websiteBucket -> {
            List<String> currentlyInstalledOnNoDns = websiteBucket.getLinksTo().stream() //
                    .filter(it -> StringTools.safeEquals(it.getLinkType(), "INSTALLED_ON_NO_DNS")) //
                    .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                    .map(it -> ((Map<String, String>) it.getOtherResource().getResource()).get("resourceName")) //
                    .sorted() //
                    .collect(Collectors.toList());
            if (!currentlyInstalledOnNoDns.isEmpty()) {
                return false;
            }

            List<String> currentlyInstalledOn = websiteBucket.getLinksTo().stream() //
                    .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON)) //
                    .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                    .map(it -> ((Map<String, String>) it.getOtherResource().getResource()).get("resourceName")) //
                    .sorted() //
                    .collect(Collectors.toList());

            if (currentlyInstalledOn.equals(applicationInstalledOn)) {
                Website website = InfraResourceUtils.resourceDetailsToResource(websiteBucket.getResourceDetails(), Website.class);
                System.out.println("[SKIP] " + website.getName() + " is already in the desired final state");
                return true;
            }

            return false;
        });

        if (websitesForDomain.isEmpty()) {
            System.out.println("[SKIP] All the websites are in the final desired state");
            return null;
        }

        // Update all the Websites with the Machines to remove as INSTALLED_ON_NO_DNS and the application's Machines as INSTALLED_ON
        Map<String, List<ResourceBucket>> websitesByOwner = websitesForDomain.stream() //
                .collect(Collectors.groupingBy(it -> InfraResourceUtils.getOwner(it.getResourceDetails())));
        {
            System.out.println("Update all the websites: old machines will be kept without DNS");

            websitesByOwner.forEach((owner, websites) -> {

                System.out.println("Update all the websites: old machines will be kept without DNS for owner " + owner);
                RequestChanges changes = new RequestChanges();
                changes.setDefaultOwner(owner);
                websites.forEach(websiteBucket -> {
                    Website website = InfraResourceUtils.resourceDetailsToResource(websiteBucket.getResourceDetails(), Website.class);
                    System.out.println("\t" + website.getName());

                    List<String> currentlyInstalledOn = websiteBucket.getLinksTo().stream() //
                            .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON)) //
                            .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                            .map(it -> ((Map<String, String>) it.getOtherResource().getResource()).get("resourceName")) //
                            .sorted() //
                            .collect(Collectors.toList());

                    ListsComparator.compareLists(currentlyInstalledOn, applicationInstalledOn, new ListComparatorHandler<String, String>() {

                        @Override
                        public void both(String machineName, String right) {
                            System.out.println("\t\t[KEEP] " + machineName);
                        }

                        @Override
                        public void leftOnly(String machineName) {
                            System.out.println("\t\t[DISABLE DNS] " + machineName);
                            changes.getLinksToDelete()
                                    .add(new LinkDetails(websiteBucket.getResourceDetails(), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(machineName))));
                            changes.getLinksToAdd()
                                    .add(new LinkDetails(websiteBucket.getResourceDetails(), "INSTALLED_ON_NO_DNS", new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(machineName))));
                        }

                        @Override
                        public void rightOnly(String machineName) {
                            System.out.println("\t\t[ADD] " + machineName);
                            changes.getLinksToAdd()
                                    .add(new LinkDetails(websiteBucket.getResourceDetails(), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(machineName))));
                        }
                    });

                });

                ResponseResourceAppliedChanges resourceAppliedChanges = infraResourceApiService.applyChanges(changes);
                exceptionService.displayResult(resourceAppliedChanges, "Applying update for owner " + owner);

            });

        }

        // Wait 10 minutes
        Thread thread = new Thread(() -> {

            try {

                System.out.println("Waiting 10 minutes to complete with DNS update. This will run in the background at " + DateTools.formatFull(DateTools.addDate(Calendar.MINUTE, 10)));
                ThreadTools.sleep(10 * 60000L);

                // Remove the NO DNS for direct Websites
                System.out.println("Remove the NO DNS for Websites");

                websitesByOwner.forEach((owner, websites) -> {

                    System.out.println("Remove the NO DNS for Websites for owner " + owner);

                    RequestChanges changes = new RequestChanges();
                    changes.setDefaultOwner(owner);

                    websitesForDomain.stream() //
                            .map(website -> {
                                ResponseResourceBucket websiteBucket = infraResourceApiService.resourceFindOneByPk(website.getResourceDetails());
                                if (!websiteBucket.isSuccess() || websiteBucket.getItem() == null) {
                                    throw new CliException("Could not get the Website: " + JsonTools.compactPrint(websiteBucket));
                                }

                                return websiteBucket.getItem();
                            }) //
                            .forEach(websiteBucket -> {
                                Website website = InfraResourceUtils.resourceDetailsToResource(websiteBucket.getResourceDetails(), Website.class);
                                System.out.println("\t" + website.getName());

                                List<String> currentlyInstalledOnNoDns = websiteBucket.getLinksTo().stream() //
                                        .filter(it -> StringTools.safeEquals(it.getLinkType(), "INSTALLED_ON_NO_DNS")) //
                                        .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                                        .map(it -> ((Map<String, String>) it.getOtherResource().getResource()).get("resourceName")) //
                                        .sorted() //
                                        .collect(Collectors.toList());

                                currentlyInstalledOnNoDns.forEach(machineName -> {
                                    System.out.println("\t\t[REMOVE DISABLED DNS] " + machineName);
                                    changes.getLinksToDelete()
                                            .add(new LinkDetails(websiteBucket.getResourceDetails(), "INSTALLED_ON_NO_DNS", new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(machineName))));
                                });

                            });

                    // Update the Url Redirections with the application's Machines
                    System.out.println("Update the Url Redirections with the application's Machines");
                    urlRedirections.forEach(urlRedirection -> {

                        List<String> currentlyInstalledOn = urlRedirection.getLinksTo().stream() //
                                .filter(it -> StringTools.safeEquals(it.getLinkType(), LinkTypeConstants.INSTALLED_ON)) //
                                .filter(it -> StringTools.safeEquals(it.getOtherResource().getResourceType(), Machine.RESOURCE_TYPE)) //
                                .map(it -> ((Map<String, String>) it.getOtherResource().getResource()).get("resourceName")) //
                                .sorted() //
                                .collect(Collectors.toList());

                        ListsComparator.compareLists(currentlyInstalledOn, applicationInstalledOn, new ListComparatorHandler<String, String>() {

                            @Override
                            public void both(String machineName, String right) {
                                System.out.println("\t\t[KEEP] " + machineName);
                            }

                            @Override
                            public void leftOnly(String machineName) {
                                System.out.println("\t\t[REMOVE] " + machineName);
                                changes.getLinksToDelete().add(
                                        new LinkDetails(urlRedirection.getResourceDetails(), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(machineName))));
                            }

                            @Override
                            public void rightOnly(String machineName) {
                                System.out.println("\t\t[ADD] " + machineName);
                                changes.getLinksToAdd().add(
                                        new LinkDetails(urlRedirection.getResourceDetails(), LinkTypeConstants.INSTALLED_ON, new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(machineName))));
                            }
                        });

                    });

                    ResponseResourceAppliedChanges resourceAppliedChanges = infraResourceApiService.applyChanges(changes);
                    exceptionService.displayResult(resourceAppliedChanges, "Applying update for owner " + owner);

                });

                System.out.println("Update completed");
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
        thread.start();
        return thread;

    }

}
