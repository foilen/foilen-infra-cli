/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.request.RequestResourceToUpdate;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.commands.exec.CheckWebsiteAccessible;
import com.foilen.infra.cli.commands.model.WebsitesAccessible;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.apachephp.ApachePhp;
import com.foilen.infra.resource.bind9.Bind9Server;
import com.foilen.infra.resource.composableapplication.ComposableApplication;
import com.foilen.infra.resource.email.resources.JamesEmailServer;
import com.foilen.infra.resource.infraconfig.InfraConfig;
import com.foilen.infra.resource.mariadb.MariaDBServer;
import com.foilen.infra.resource.mongodb.MongoDBServer;
import com.foilen.infra.resource.postgresql.PostgreSqlServer;
import com.foilen.infra.resource.webcertificate.WebsiteCertificate;
import com.foilen.infra.resource.website.Website;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.DateTools;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.ThreadTools;

@ShellComponent
public class CheckCommands extends AbstractBasics {

    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;

    @ShellMethod("Update some resources with the same values to ensure the updates were propagated")
    public void checkAllResourcesWellConfigured() {

        checkAllResourcesWellConfigured(InfraConfig.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(ApachePhp.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(Bind9Server.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(ComposableApplication.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(JamesEmailServer.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(MariaDBServer.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(MongoDBServer.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(PostgreSqlServer.RESOURCE_TYPE);

    }

    @SuppressWarnings("unchecked")
    private void checkAllResourcesWellConfigured(String resourceType) {
        InfraApiService infraApiService = profileService.getTargetInfraApiService();

        // Get the list
        System.out.println("---[ " + resourceType + " ]---");
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAll(new RequestResourceSearch().setResourceType(resourceType));
        exceptionService.displayResult(resourceBuckets, "Get resources");
        if (!resourceBuckets.isSuccess()) {
            return;
        }

        resourceBuckets.getItems().stream() //
                .forEach(resourceBucket -> {
                    try {
                        Map<String, Object> resource = (Map<String, Object>) resourceBucket.getResourceDetails().getResource();
                        String resourceName = (String) resource.get("resourceName");
                        System.out.println("-> " + resourceName);
                        RequestResourceToUpdate resourceToUpdate = new RequestResourceToUpdate(resourceBucket.getResourceDetails(), resourceBucket.getResourceDetails());
                        ResponseResourceAppliedChanges resourceAppliedChanges = infraApiService.getInfraResourceApiService()
                                .applyChanges(new RequestChanges().setResourcesToUpdate(Collections.singletonList(resourceToUpdate)));

                        // Show only if more than 1 change
                        if (resourceAppliedChanges.isSuccess() && resourceAppliedChanges.getAuditItems().getItems().size() == 1) {
                            System.out.println("[SUCCESS] Applying update and nothing changed (" + resourceAppliedChanges.getTxId() + ")");
                        } else {
                            exceptionService.displayResult(resourceAppliedChanges, "Applying update");
                        }
                    } catch (Throwable e) {
                        System.out.println("Problem: " + e.getMessage());
                    }
                });
    }

    @ShellMethod("List the Web Certificates that will expire this month (sooner first)")
    public void checkWebCertificatesNearExpiration() {

        // Get the list
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAll(new RequestResourceSearch().setResourceType(WebsiteCertificate.RESOURCE_TYPE));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }

        Date in1Month = DateTools.addDate(Calendar.MONTH, 1);
        resourceBuckets.getItems().stream() //
                .map(resourceBucket -> JsonTools.clone(resourceBucket.getResourceDetails().getResource(), WebsiteCertificate.class)) //
                .filter(websiteCertificate -> DateTools.isBefore(websiteCertificate.getEnd(), in1Month)) //
                .sorted((a, b) -> Long.compare(a.getEnd().getTime(), b.getEnd().getTime())) //
                .forEach(websiteCertificate -> {
                    System.out.println(DateTools.formatDateOnly(websiteCertificate.getEnd()) + " (" + websiteCertificate.getResourceEditorName() + ") " + websiteCertificate.getDomainNames());
                });

    }

    @ShellMethod("List the Web Certificates that are not used")
    public void checkWebCertificatesUnused() {

        // Get the list
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAll(new RequestResourceSearch().setResourceType(WebsiteCertificate.RESOURCE_TYPE));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }

        resourceBuckets.getItems().stream() //
                .filter(resourceBucket -> resourceBucket.getLinksFrom().isEmpty()) //
                .forEach(resourceBucket -> {
                    WebsiteCertificate websiteCertificate = JsonTools.clone(resourceBucket.getResourceDetails().getResource(), WebsiteCertificate.class);
                    System.out.println(websiteCertificate.getDomainNames() + " (" + websiteCertificate.getResourceEditorName() + ") ");
                });

    }

    @ShellMethod("Retrieve all the websites and try to contact them")
    public void checkWebsitesAccessible() {

        // Get the list
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAll(new RequestResourceSearch().setResourceType(Website.RESOURCE_TYPE));
        exceptionService.displayResultAndThrow(resourceBuckets, "Retrieve the websites list");

        // Get the list
        List<WebsitesAccessible> websitesAccessibles = resourceBuckets.getItems().stream() //
                .filter(i -> i.getLinksTo().stream().anyMatch(l -> l.getLinkType().equals(LinkTypeConstants.INSTALLED_ON))) //
                .map(resourceBucket -> JsonTools.clone(resourceBucket.getResourceDetails().getResource(), Website.class)) //
                .flatMap(website -> website.getDomainNames().stream().map(domain -> //
                new WebsitesAccessible(website.isHttps() ? "https://" + domain : "http://" + domain, website.getName()) //
                )) //
                .sorted() //
                .collect(Collectors.toCollection(() -> new ArrayList<>()));

        // Execute the checks
        ExecutorService executorService = Executors.newFixedThreadPool(10, ThreadTools.daemonThreadFactory());

        List<Future<?>> futures = websitesAccessibles.stream() //
                .map(it -> executorService.submit(new CheckWebsiteAccessible(it))) //
                .collect(Collectors.toList());
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
            }
        });
        System.out.println();

        // Display the results
        Collections.sort(websitesAccessibles);
        websitesAccessibles.forEach(it -> {
            if (it.isSuccess()) {
                System.out.print("[OK] ");
            } else {
                System.out.print("[ERROR] ");
            }

            System.out.print("[" + it.getHttpStatus() + "] ");
            System.out.print("[" + it.getExecutionTimeMs() + "ms] ");

            System.out.print(it.getUrl());
            System.out.print(" (" + it.getWebsiteName() + ")");

            System.out.println();

            if (!it.isSuccess()) {
                System.out.println("\t" + it.getError());
            }
        });

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

}
