/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.util.HashMap;
import java.util.Map;

import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.plugin.v1.model.resource.AbstractIPResource;
import com.foilen.smalltools.tools.JsonTools;

public class InfraResourceUtils {

    private static final String META_UI_OWNER = "UI_OWNER";

    public static String getOwner(AbstractIPResource resource) {
        if (resource.getMeta() == null) {
            return null;
        }
        return resource.getMeta().get(META_UI_OWNER);
    }

    @SuppressWarnings({ "unchecked" })
    public static String getOwner(ResourceDetails resourceDetails) {
        Map<String, Object> detailedResource = ((Map<String, Object>) resourceDetails.getResource());
        Map<String, String> meta = (Map<String, String>) detailedResource.get("meta");
        if (meta == null) {
            return null;
        }
        return meta.get(META_UI_OWNER);
    }

    @SuppressWarnings({ "unchecked" })
    public static String getResourceId(ResourceDetails resourceDetails) {
        Map<String, Object> detailedResource = ((Map<String, Object>) resourceDetails.getResource());
        return (String) detailedResource.get("internalId");
    }

    @SuppressWarnings({ "unchecked" })
    public static String getResourceName(ResourceDetails resourceDetails) {
        Map<String, Object> detailedResource = ((Map<String, Object>) resourceDetails.getResource());
        return (String) detailedResource.get("resourceName");
    }

    @SuppressWarnings("unchecked")
    public static <T extends AbstractIPResource> T resourceDetailsToResource(ResourceDetails resourceDetails, Class<T> resourceType) {
        T resource = JsonTools.clone(resourceDetails.getResource(), resourceType);
        resource.setInternalId(((Map<String, String>) resourceDetails.getResource()).get("internalId"));
        return resource;
    }

    @SuppressWarnings({ "unchecked" })
    public static void setOwner(ResourceDetails resourceDetails, String owner) {
        Map<String, Object> detailedResource = ((Map<String, Object>) resourceDetails.getResource());
        Map<String, String> meta = (Map<String, String>) detailedResource.get("meta");
        if (meta == null) {
            meta = new HashMap<String, String>();
            detailedResource.put("meta", meta);
        }

        meta.put(META_UI_OWNER, owner);
    }

    private InfraResourceUtils() {
    }

}
