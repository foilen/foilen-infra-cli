/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.commands.exec.model.ProgressionHook;
import com.foilen.infra.cli.commands.model.WebsitesAccessible;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.CheckService;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.resource.apachephp.ApachePhp;
import com.foilen.infra.resource.bind9.Bind9Server;
import com.foilen.infra.resource.composableapplication.ComposableApplication;
import com.foilen.infra.resource.email.resources.JamesEmailServer;
import com.foilen.infra.resource.infraconfig.InfraConfig;
import com.foilen.infra.resource.mariadb.MariaDBServer;
import com.foilen.infra.resource.mongodb.MongoDBServer;
import com.foilen.infra.resource.postgresql.PostgreSqlServer;
import com.foilen.infra.resource.unixuser.UnixUser;
import com.foilen.infra.resource.urlredirection.UrlRedirection;
import com.foilen.infra.resource.webcertificate.WebsiteCertificate;
import com.foilen.infra.resource.website.Website;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.DateTools;
import com.foilen.smalltools.tools.JsonTools;

@ShellComponent
public class CheckCommands extends AbstractBasics {

    @Autowired
    private CheckService checkService;
    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;

    private void apply(InfraApiService infraApiService, RequestChanges requestChanges) {
        ResponseResourceAppliedChanges resourceAppliedChanges = infraApiService.getInfraResourceApiService().applyChanges(requestChanges);
        exceptionService.displayResult(resourceAppliedChanges, "Applying refresh");
        requestChanges.getResourcesToRefreshPk().clear();
    }

    @ShellMethod("Refresh some resources to ensure the updates were propagated")
    public void checkAllResourcesWellConfigured() {

        checkAllResourcesWellConfigured(InfraConfig.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(ApachePhp.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(Bind9Server.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(ComposableApplication.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(JamesEmailServer.RESOURCE_TYPE);
        checkAllResourcesWellConfigured("Machine");
        checkAllResourcesWellConfigured(MariaDBServer.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(MongoDBServer.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(PostgreSqlServer.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(UnixUser.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(UrlRedirection.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(Website.RESOURCE_TYPE);
        checkAllResourcesWellConfigured(WebsiteCertificate.RESOURCE_TYPE);

    }

    @SuppressWarnings("unchecked")
    private void checkAllResourcesWellConfigured(String resourceType) {
        InfraApiService infraApiService = profileService.getTargetInfraApiService();

        // Get the list
        System.out.println("---[ " + resourceType + " ]---");
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(resourceType));
        exceptionService.displayResult(resourceBuckets, "Get resources");
        if (!resourceBuckets.isSuccess()) {
            return;
        }

        RequestChanges requestChanges = new RequestChanges();

        resourceBuckets.getItems().stream() //
                .forEach(resourceBucket -> {
                    try {
                        Map<String, Object> resource = (Map<String, Object>) resourceBucket.getResourceDetails().getResource();
                        String resourceName = (String) resource.get("resourceName");
                        System.out.println("-> " + resourceName);

                        requestChanges.getResourcesToRefreshPk().add(resourceBucket.getResourceDetails());
                        if (requestChanges.getResourcesToRefreshPk().size() >= 10) {
                            apply(infraApiService, requestChanges);
                        }

                    } catch (Throwable e) {
                        System.out.println("Problem: " + e.getMessage());
                    }
                });

        if (!requestChanges.getResourcesToRefreshPk().isEmpty()) {
            apply(infraApiService, requestChanges);
        }
    }

    @ShellMethod("List the Web Certificates that will expire this month (sooner first)")
    public void checkWebCertificatesNearExpiration() {

        // Get the list
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService()
                .resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(WebsiteCertificate.RESOURCE_TYPE));
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
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService()
                .resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(WebsiteCertificate.RESOURCE_TYPE));
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

        List<WebsitesAccessible> websitesAccessibles = checkService.checkWebsitesAccessible(new ProgressionHook() {
            @Override
            public void begin() {
                System.out.print(".");
            }

            @Override
            public void done() {
                System.out.print("+");
            }
        });
        System.out.println();

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
