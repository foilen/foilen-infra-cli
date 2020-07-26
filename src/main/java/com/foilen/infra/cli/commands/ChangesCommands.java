/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.JsonTools;

@ShellComponent
public class ChangesCommands extends AbstractBasics {

    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;

    @ShellMethod("Execute the changes described in a JSON file")
    public void changeExecute( //
            String changeJsonFile //
    ) {

        // Get the list
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        RequestChanges changes = JsonTools.readFromFile(changeJsonFile, RequestChanges.class);
        ResponseResourceAppliedChanges resourceAppliedChanges = infraApiService.getInfraResourceApiService().applyChanges(changes);

        exceptionService.displayResult(resourceAppliedChanges, "Applying update");

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
