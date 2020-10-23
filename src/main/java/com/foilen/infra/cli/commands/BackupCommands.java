/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.cli.commands.model.BackupResults;
import com.foilen.infra.cli.services.BackupService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.smalltools.tools.AbstractBasics;

@ShellComponent
public class BackupCommands extends AbstractBasics {

    @Autowired
    private BackupService backupService;
    @Autowired
    private ProfileService profileService;

    @ShellMethod("Backup all by TIMESTAMP/OWNER/MACHINE-USER.tgz")
    public void backupAll( //
            String folder //
    ) {

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        BackupResults results = backupService.backupAll(folder, timestamp);

        System.out.println("---[ Summary ]---");
        System.out.println(results);

    }

    @ShellMethodAvailability
    public Availability isAvailable() {

        if (profileService.getTarget() == null) {
            return Availability.unavailable("you did not specify a target profile");
        }

        return Availability.available();
    }

}
