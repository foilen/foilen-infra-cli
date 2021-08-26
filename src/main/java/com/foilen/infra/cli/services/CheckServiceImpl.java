/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.foilen.infra.api.model.resource.ResourceBucket;
import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.commands.exec.CheckWebsiteAccessible;
import com.foilen.infra.cli.commands.exec.model.ProgressionHook;
import com.foilen.infra.cli.commands.model.WebsitesAccessible;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.website.Website;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.StringTools;
import com.foilen.smalltools.tools.ThreadTools;
import com.foilen.smalltools.tuple.Tuple3;
import com.google.common.base.Strings;

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
        return checkWebsitesAccessible(null, progressionHook);
    }

    @Override
    public List<WebsitesAccessible> checkWebsitesAccessible(String owner, ProgressionHook progressionHook) {

        // Get the list
        displayService.display("Retrieve the websites list");
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        RequestResourceSearch requestResourceSearch = new RequestResourceSearch().setResourceType(Website.RESOURCE_TYPE);
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(requestResourceSearch);
        exceptionService.displayResultAndThrow(resourceBuckets, "Retrieve the websites list");

        // Get the list
        Stream<ResourceBucket> resourceStream = resourceBuckets.getItems().stream();
        if (!Strings.isNullOrEmpty(owner)) {
            resourceStream = resourceStream.filter(it -> StringTools.safeEquals(InfraResourceUtils.getOwner(it.getResourceDetails()), owner));
        }
        List<WebsitesAccessible> websitesAccessibles = resourceStream //
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

    @SuppressWarnings("unchecked")
    @Override
    public void listAllResourcesOnMachine(String machineName) {

        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        ResponseResourceBucket machineBucket = infraResourceApiService.resourceFindOneByPk(new ResourceDetails(Machine.RESOURCE_TYPE, new Machine(machineName)));
        if (!machineBucket.isSuccess() || machineBucket.getItem() == null) {
            throw new CliException("Could not get the machine: " + JsonTools.compactPrint(machineBucket));
        }

        Map<String, List<Tuple3<String, String, String>>> linkTypeAndResourceNameByResourceType = machineBucket.getItem().getLinksFrom().stream() //
                .map(it -> new Tuple3<>(it.getOtherResource().getResourceType(), it.getLinkType(), ((Map<String, String>) it.getOtherResource().getResource()).get("resourceName"))) //
                .collect(Collectors.groupingBy(it -> it.getA()));

        linkTypeAndResourceNameByResourceType.keySet().stream().sorted().forEach(resourceType -> {

            Map<Object, List<Tuple3<String, String, String>>> resourceNameByLinkType = linkTypeAndResourceNameByResourceType.get(resourceType).stream() //
                    .collect(Collectors.groupingBy(it -> it.getB()));

            resourceNameByLinkType.keySet().stream().sorted().forEach(linkType -> {

                resourceNameByLinkType.get(linkType).stream() //
                        .map(it -> it.getC()) //
                        .sorted() //
                        .forEach(resourceName -> System.out.println(resourceType + "|" + linkType + "|" + resourceName));
            });

        });

    }

}
