/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

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

    @ShellMethod("Move all the resources gracefully from one machine to another")
    public void moveAllFromMachine( //
            String sourceHostname, //
            String targetHostname //
    ) {
        moveService.moveAllFromMachine(sourceHostname, targetHostname);
    }

    @ShellMethod("Move all the unix user to another host by syncing files and moving applications")
    public void moveAllUnixUsers( //
            String sourceHostname, //
            String targetHostname, //
            @ShellOption(defaultValue = "false") boolean stopOnFailure //
    ) {
        moveService.moveAllUnixUser(sourceHostname, targetHostname, stopOnFailure);
    }

    @ShellMethod("Move all the website where the application is installed for domains on a specific machine")
    public void moveAllWebsitesCloser( //
            String machineName, //
            @ShellOption(defaultValue = ShellOption.NULL) String redirectionOnlyMachine, //
            @ShellOption(defaultValue = "false") boolean stopOnFailure //
    ) {
        moveService.moveAllWebsitesCloser(machineName, redirectionOnlyMachine, stopOnFailure);
    }

    @ShellMethod("Move the unix user to another host by syncing files and moving applications")
    public void moveUnixUser( //
            String username, //
            String sourceHostname, //
            String targetHostname //
    ) {
        moveService.moveUnixUser(sourceHostname, targetHostname, username);
    }

    @ShellMethod("Move the website where the application is installed")
    public void moveWebsiteCloser( //
            String domainName, //
            @ShellOption(defaultValue = ShellOption.NULL) String redirectionOnlyMachine //
    ) {
        moveService.moveWebsiteCloser(domainName, redirectionOnlyMachine);
    }

}
