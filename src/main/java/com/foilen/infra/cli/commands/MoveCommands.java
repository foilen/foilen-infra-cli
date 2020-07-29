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
import org.springframework.shell.standard.ShellOption;

import com.foilen.infra.cli.services.MoveService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.smalltools.tools.AbstractBasics;

@ShellComponent
public class MoveCommands extends AbstractBasics {

    @Autowired
    private MoveService moveService;
    @Autowired
    private ProfileService profileService;

    @ShellMethodAvailability
    public Availability isAvailable() {

        if (profileService.getSource() == null) {
            return Availability.unavailable("you did not specify a source profile");
        }
        if (profileService.getTarget() == null) {
            return Availability.unavailable("you did not specify a target profile");
        }

        return Availability.available();
    }

    @ShellMethod("Move all the unix user to another host by syncing files and moving applications")
    public void moveAllUnixUsers( //
            String sourceHostname, //
            String targetHostname, //
            @ShellOption(defaultValue = "false") boolean stopOnFailure //
    ) {
        moveService.moveAllUnixUser(sourceHostname, targetHostname, stopOnFailure);
    }

    @ShellMethod("Move the unix user to another host by syncing files and moving applications")
    public void moveUnixUser( //
            String username, //
            String sourceHostname, //
            String targetHostname //
    ) {
        moveService.moveUnixUser(sourceHostname, targetHostname, username);
    }

}
