/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.commands.exec.CheckWebsiteAccessible;
import com.foilen.infra.cli.commands.exec.model.ProgressionHook;
import com.foilen.infra.cli.commands.model.WebsitesAccessible;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.website.Website;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.ThreadTools;

@Component
public class CheckServiceImpl extends AbstractBasics implements CheckService {

    @Autowired
    private DisplayService displayService;
    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;

    @Override
    public List<WebsitesAccessible> checkWebsitesAccessible(ProgressionHook progressionHook) {

        // Get the list
        displayService.display("Retrieve the websites list");
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
                .map(it -> executorService.submit(new CheckWebsiteAccessible(progressionHook, it))) //
                .collect(Collectors.toList());
        futures.forEach(f -> {
            try {
                f.get();
            } catch (Exception e) {
            }
        });

        Collections.sort(websitesAccessibles);

        return websitesAccessibles;
    }

}
