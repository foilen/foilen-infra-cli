/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands.model;

import com.foilen.infra.api.model.resource.ResourceBucket;
import com.google.common.collect.ComparisonChain;

import java.util.SortedMap;

public class ExplodedResource implements Comparable<ExplodedResource> {

    private String resourceType;
    private String resourceName;
    private SortedMap<String, Object> resource;
    private ResourceBucket resourceBucket;

    public ExplodedResource(String resourceType, String resourceName, SortedMap<String, Object> resource, ResourceBucket resourceBucket) {
        this.resourceType = resourceType;
        if (resourceName == null) {
            this.resourceName = "";
        } else {
            this.resourceName = resourceName;
        }
        this.resource = resource;
        this.resourceBucket = resourceBucket;
    }

    public String getResourceType() {
        return resourceType;
    }

    public SortedMap<String, Object> getResource() {
        return resource;
    }

    public ResourceBucket getResourceBucket() {
        return resourceBucket;
    }

    @Override
    public int compareTo(ExplodedResource o) {
        return ComparisonChain.start()
                .compare(resourceType, o.resourceType)
                .compare(resourceName, o.resourceName)
                .result();
    }
}
