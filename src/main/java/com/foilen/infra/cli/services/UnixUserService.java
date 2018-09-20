/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2018 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.foilen.infra.api.model.ResourceDetails;
import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.request.RequestResourceToUpdate;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.resource.unixuser.UnixUser;
import com.foilen.smalltools.restapi.model.FormResult;
import com.foilen.smalltools.tools.AssertTools;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.SecureRandomTools;
import com.google.common.base.Strings;

@Component
public class UnixUserService {

    @Autowired
    private ExceptionService exceptionService;

    public String getOrCreateUserPassword(InfraApiService infraApiService, String username, String context) {

        // Get the user
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        ResponseResourceBucket resourceBucket = infraResourceApiService.resourceFindOne(new RequestResourceSearch() //
                .setResourceType(UnixUser.RESOURCE_TYPE) //
                .setProperties(Collections.singletonMap(UnixUser.PROPERTY_NAME, username)));
        AssertTools.assertNotNull(resourceBucket.getItem(), "User " + username + " does not exist on the " + context);

        // Get the password
        UnixUser unixUser = JsonTools.clone(resourceBucket.getItem().getResourceDetails().getResource(), UnixUser.class);
        if (Strings.isNullOrEmpty(unixUser.getPassword())) {
            if (Strings.isNullOrEmpty(unixUser.getHashedPassword())) {
                // Create
                System.out.println("[" + context + "] Update user " + username + " - Add a random password");
                String password = SecureRandomTools.randomHexString(15);
                RequestChanges changes = new RequestChanges();
                unixUser.setPassword(password);
                unixUser.setKeepClearPassword(true);
                ResourceDetails unixUserDetails = new ResourceDetails(UnixUser.RESOURCE_TYPE, unixUser);
                changes.getResourcesToUpdate().add(new RequestResourceToUpdate(unixUserDetails, unixUserDetails));
                FormResult result = infraResourceApiService.applyChanges(changes);
                exceptionService.displayResultAndThrow(result, "Update user " + username + " password");
                return password;
            } else {
                throw new CliException(
                        "The user " + username + " exists on the " + context + ", but has an hashed password, but not the clear text password. Will not change the password. Please do it manually.");
            }
        } else {
            return unixUser.getPassword();
        }

    }

}
