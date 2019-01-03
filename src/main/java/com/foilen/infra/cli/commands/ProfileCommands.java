/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.model.profile.ServerProfile;
import com.foilen.infra.cli.services.ProfileService;

@ShellComponent
public class ProfileCommands {

    @Autowired
    private ProfileService profileService;

    @ShellMethod("Add an API profile.")
    public void profileAddApi( //
            String profileName, //
            String infraBaseUrl, //
            String apiUser, //
            String apiKey, //
            @ShellOption(defaultValue = ShellOption.NULL) String rootSshCert) {

        ApiProfile profile = new ApiProfile();
        profile.setInfraBaseUrl(infraBaseUrl);
        profile.setApiUser(apiUser);
        profile.setApiKey(apiKey);
        profile.setSshCertificateFile(rootSshCert);
        profileService.add(profileName, profile);
    }

    @ShellMethod("Add a server profile.")
    public void profileAddServer( //
            String profileName, //
            String hostname, //
            @ShellOption(defaultValue = "22") int sshPort, //
            @ShellOption(defaultValue = "root") String username, //
            String sshCertificateFile) {

        ServerProfile profile = new ServerProfile();
        profile.setHostname(hostname);
        profile.setSshPort(sshPort);
        profile.setUsername(username);
        profile.setSshCertificateFile(sshCertificateFile);
        profileService.add(profileName, profile);
    }

    @ShellMethod("List profiles.")
    public List<String> profileList() {
        return profileService.list();
    }

    @ShellMethod("Set the source profile.")
    public void profileSetSource(String profileName) {
        profileService.setSource(profileName);
    }

    @ShellMethod("Set the target profile.")
    public void profileSetTarget(String profileName) {
        profileService.setTarget(profileName);
    }

}
