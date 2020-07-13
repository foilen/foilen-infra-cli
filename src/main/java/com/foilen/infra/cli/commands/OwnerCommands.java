/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import com.foilen.infra.api.model.permission.LinkAction;
import com.foilen.infra.api.model.permission.PermissionLink;
import com.foilen.infra.api.model.permission.PermissionResource;
import com.foilen.infra.api.model.permission.ResourceAction;
import com.foilen.infra.api.model.permission.RoleEditForm;
import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.request.RequestResourceToUpdate;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.response.ResponseResourceTypesDetails;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.api.service.InfraRoleApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.smalltools.restapi.model.FormResult;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.BufferBatchesTools;
import com.foilen.smalltools.tools.CollectionsTools;
import com.foilen.smalltools.tools.StringTools;
import com.foilen.smalltools.tools.ThreadTools;

@ShellComponent
public class OwnerCommands extends AbstractBasics {

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

    @SuppressWarnings("unchecked")
    @ShellMethod("Change the owner on all resources that matches any of the arguments")
    public void ownerChange( //
            @ShellOption(defaultValue = ShellOption.NULL) String nameStartsWith, //
            @ShellOption(defaultValue = ShellOption.NULL) String nameContains, //
            @ShellOption(defaultValue = ShellOption.NULL) String nameEndsWith, //
            @ShellOption String owner //
    ) {

        if (!CollectionsTools.isAnyItemNotNull(nameStartsWith, nameContains, nameEndsWith)) {
            throw new CliException("You need to specify at least one argument to match");
        }

        // Get the list of types
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        ResponseResourceTypesDetails allTypes = infraResourceApiService.typeFindAll();
        exceptionService.displayResultAndThrow(allTypes, "Get all types");

        // Get all the matching resources
        Queue<ResourceDetails> resourceDetailsToUpdate = new ConcurrentLinkedQueue<>();
        Queue<Future<?>> futures = new ConcurrentLinkedQueue<>();
        ExecutorService executorService = Executors.newFixedThreadPool(5, ThreadTools.daemonThreadFactory());
        allTypes.getItems().forEach(type -> {
            futures.add(executorService.submit(() -> {

                String resourceType = type.getResourceType();
                ResponseResourceBuckets resourceBuckets = infraResourceApiService.resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(resourceType));
                exceptionService.displayResultAndThrow(allTypes, "Get the matching resources of type " + resourceType);
                resourceBuckets.getItems().stream() //
                        .map(rB -> (Map<String, Object>) rB.getResourceDetails().getResource()) //
                        .filter(r -> nameStartsWith == null || ((String) r.get("resourceName")).startsWith(nameStartsWith)) //
                        .filter(r -> nameContains == null || ((String) r.get("resourceName")).contains(nameContains)) //
                        .filter(r -> nameEndsWith == null || ((String) r.get("resourceName")).endsWith(nameEndsWith)) //
                        .forEach(r -> {
                            String resourceId = (String) r.get("internalId");
                            System.out.println("\t" + r.get("resourceName") + " (" + resourceId + ")");

                            // Get all the details of the resource
                            futures.add(executorService.submit(() -> {
                                ResponseResourceBucket resourceBucket = infraResourceApiService.resourceFindById(resourceId);
                                exceptionService.throwOnFailure(resourceBucket, "Get the resource " + resourceId);
                                ResourceDetails resourceDetails = resourceBucket.getItem().getResourceDetails();
                                Map<String, Object> detailedResource = (Map<String, Object>) resourceDetails.getResource();

                                // Check the owner
                                Map<String, String> meta = (Map<String, String>) detailedResource.get("meta");
                                String currentOwner = meta.get("UI_OWNER");
                                if (StringTools.safeEquals(currentOwner, owner)) {
                                    System.out.println("\t\t[SKIP] Owner is already " + owner);
                                } else {
                                    System.out.println("\t\t[CHANGE] Change owner " + currentOwner + " -> " + owner);
                                    meta.put("UI_OWNER", owner);
                                    resourceDetailsToUpdate.add(resourceDetails);
                                }
                            }));
                        });
                ;
            }));
        });

        // Wait for all tasks to be processed
        futures.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new CliException("", e);
            }
        });

        // Update the resources if any
        if (!resourceDetailsToUpdate.isEmpty()) {
            System.out.println("Request the update of " + resourceDetailsToUpdate.size() + " resources in batches of 10");
            BufferBatchesTools<ResourceDetails> batches = new BufferBatchesTools<>(10, resourcesInTheBatch -> {
                RequestChanges changes = new RequestChanges();
                resourcesInTheBatch.forEach(rdtu -> changes.getResourcesToUpdate().add(new RequestResourceToUpdate(rdtu, rdtu)));
                exceptionService.displayResult(infraResourceApiService.applyChanges(changes), "Applying update");
            });
            batches.add(resourceDetailsToUpdate.stream().collect(Collectors.toList()));
            batches.close();
        }

    }

    @ShellMethod("Create an owner with a role")
    public void ownerCreate( //
            @ShellOption String owner //
    ) {

        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraRoleApiService infraRoleApiService = infraApiService.getInfraRoleApiService();

        String roleName = owner + "_all";
        FormResult result = infraRoleApiService.roleAdd(roleName);
        exceptionService.displayResultAndThrow(result, "Add role");

        RoleEditForm roleEditForm = new RoleEditForm();
        roleEditForm.getResources().add(new PermissionResource() //
                .setAction(ResourceAction.ALL) //
                .setExplicitChange(true) //
                .setType("*") //
                .setOwner(owner) //
        );
        roleEditForm.getLinks().add(new PermissionLink() //
                .setAction(LinkAction.ALL) //
                .setExplicitChange(true) //
                .setFromType("*") //
                .setFromOwner(owner) //
                .setLinkType("*") //
        );
        roleEditForm.getLinks().add(new PermissionLink() //
                .setAction(LinkAction.ALL) //
                .setExplicitChange(true) //
                .setLinkType("*") //
                .setToType("*") //
                .setToOwner(owner) //
        );
        result = infraRoleApiService.roleEdit(roleName, roleEditForm);
        exceptionService.displayResultAndThrow(result, "Edit role");

    }

    @SuppressWarnings("unchecked")
    @ShellMethod("List the resources without owner")
    public void ownerOrphan() {

        // Get the list of orphans
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        ResponseResourceBuckets resourcesWithoutOwner = infraResourceApiService.resourceFindAllWithoutOwner();
        exceptionService.displayResultAndThrow(resourcesWithoutOwner, "Get orphans");

        // Display
        resourcesWithoutOwner.getItems().stream() //
                .map(rB -> (Map<String, Object>) rB.getResourceDetails().getResource()) //
                .map(r -> r.get("resourceName") + " (" + r.get("internalId") + ")") //
                .sorted() //
                .forEach(r -> System.out.println(r));

    }

}
