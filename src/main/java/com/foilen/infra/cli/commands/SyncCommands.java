/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2018 Foilen (http://foilen.com)

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

import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.ProfileHasCert;
import com.foilen.infra.cli.model.ServerProfile;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.cli.services.SshService;
import com.foilen.infra.cli.services.UnixUserService;
import com.foilen.smalltools.jsch.JSchTools;
import com.foilen.smalltools.jsch.SshLogin;
import com.foilen.smalltools.shell.ExecResult;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.AssertTools;
import com.foilen.smalltools.tools.SecureRandomTools;

@ShellComponent
public class SyncCommands extends AbstractBasics {

    @Autowired
    private SshService sshService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private UnixUserService unixUserService;

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

    @ShellMethod("Sync files using rsync")
    public void syncFiles( //
            @ShellOption(defaultValue = ShellOption.NULL) String sourceHostname, //
            String sourceUsername, //
            @ShellOption(defaultValue = ShellOption.NULL) String targetHostname, //
            String targetUsername //
    ) {

        // Check one side is using cert (jsch to the one with password and rsync using the cert)
        boolean sourceHasCert = false;
        boolean targetHasCert = false;
        if (profileService.getSource() instanceof ProfileHasCert) {
            sourceHasCert = true;
        }
        if (profileService.getTarget() instanceof ProfileHasCert) {
            targetHasCert = true;
        }
        if (!sourceHasCert && !targetHasCert) {
            throw new CliException("At least one side must use certificate");
        }

        if (!sourceHasCert) {

            // Log on source and push to target using cert
            AssertTools.assertNotNull(sourceHostname, "The sourceHostname must be provided");
            String password = unixUserService.getOrCreateUserPassword(profileService.getSourceInfraApiService(), sourceUsername, "source");
            sshService.waitCanLogin(sourceHostname, sourceUsername, password, 2 * 60);

            JSchTools jSchTools = new JSchTools();
            try {
                // Send source cert
                logger.info("Send target cert to source");
                ServerProfile serverProfile = (ServerProfile) profileService.getTarget();
                String tmpKeyfile = "/tmp/" + SecureRandomTools.randomHexString(10);
                jSchTools.login(new SshLogin(sourceHostname, sourceUsername).withPassword(password).autoApproveHostKey());
                jSchTools.createAndUseSftpChannel(consumer -> {
                    // Create
                    consumer.put(tmpKeyfile).close();

                    // Secure
                    consumer.chmod(00600, tmpKeyfile);

                    // Send
                    consumer.put(serverProfile.getSshCertificateFile(), tmpKeyfile);
                });

                logger.info("Log on source and push to target using cert");
                StringBuilder command = new StringBuilder();
                command.append("/usr/bin/rsync --delay-updates --compress-level=9 --delete -zrtv");
                command.append("e \"ssh -o StrictHostKeyChecking=no -i ").append(tmpKeyfile).append(" -l ").append(serverProfile.getUsername()).append("\" ");
                command.append("/home/").append(sourceUsername).append("/ ").append(targetHostname).append(":/home/").append(targetUsername).append("/");
                logger.info("Run command: {}", command.toString());
                ExecResult execResult = jSchTools.executeInLogger(command.toString());
                if (execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the rsync command. Exit code: {}", execResult.getExitCode());
                }

                logger.info("Delete cert");
                jSchTools.createAndUseSftpChannel(consumer -> {
                    consumer.rm(tmpKeyfile);
                });
            } finally {
                jSchTools.disconnect();
            }

        } else if (!targetHasCert) {

            // Log on target and pull from source using cert
            AssertTools.assertNotNull(targetHostname, "The targetHostname must be provided");
            String password = unixUserService.getOrCreateUserPassword(profileService.getTargetInfraApiService(), targetUsername, "target");
            sshService.waitCanLogin(targetHostname, targetUsername, password, 2 * 60);

            JSchTools jSchTools = new JSchTools();
            try {
                // Send source cert
                logger.info("Send source cert to target");
                ServerProfile serverProfile = (ServerProfile) profileService.getSource();
                String tmpKeyfile = "/tmp/" + SecureRandomTools.randomHexString(10);
                jSchTools.login(new SshLogin(targetHostname, targetUsername).withPassword(password).autoApproveHostKey());
                jSchTools.createAndUseSftpChannel(consumer -> {
                    // Create
                    consumer.put(tmpKeyfile).close();

                    // Secure
                    consumer.chmod(00600, tmpKeyfile);

                    // Send
                    consumer.put(serverProfile.getSshCertificateFile(), tmpKeyfile);
                });

                logger.info("Log on target and pull from source using cert");
                StringBuilder command = new StringBuilder();
                command.append("/usr/bin/rsync --delay-updates --compress-level=9 --delete -zrtv");
                command.append("e \"ssh -o StrictHostKeyChecking=no -i ").append(tmpKeyfile).append(" -l ").append(serverProfile.getUsername()).append("\" ");
                command.append(sourceHostname).append(":/home/").append(sourceUsername).append("/ /home/").append(targetUsername).append("/");
                logger.info("Run command: {}", command.toString());
                ExecResult execResult = jSchTools.executeInLogger(command.toString());
                if (execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the rsync command. Exit code: {}", execResult.getExitCode());
                }

                logger.info("Delete cert");
                jSchTools.createAndUseSftpChannel(consumer -> {
                    consumer.rm(tmpKeyfile);
                });
            } finally {
                jSchTools.disconnect();
            }

        } else {

            // Both has certs ; Log on source and push to target using cert
            JSchTools jSchTools = new JSchTools();
            try {
                // Send source cert
                logger.info("Send target cert to source");
                String tmpKeyfile = "/tmp/" + SecureRandomTools.randomHexString(10);
                ServerProfile sourceServerProfile = (ServerProfile) profileService.getSource();
                ServerProfile targetServerProfile = (ServerProfile) profileService.getTarget();
                jSchTools.login(new SshLogin(sourceHostname, sourceServerProfile.getUsername()).withPrivateKey(sourceServerProfile.getSshCertificateFile()).autoApproveHostKey());
                jSchTools.createAndUseSftpChannel(consumer -> {
                    // Create
                    consumer.put(tmpKeyfile).close();

                    // Secure
                    consumer.chmod(00600, tmpKeyfile);

                    // Send
                    consumer.put(targetServerProfile.getSshCertificateFile(), tmpKeyfile);
                });

                logger.info("Log on source and push to target using cert");
                StringBuilder command = new StringBuilder();
                command.append("/usr/bin/rsync --delay-updates --compress-level=9 --delete -zrtv");
                command.append("e \"ssh -o StrictHostKeyChecking=no -i ").append(tmpKeyfile).append(" -l ").append(targetServerProfile.getUsername()).append("\" ");
                command.append("/home/").append(sourceUsername).append("/ ").append(targetHostname).append(":/home/").append(targetUsername).append("/");
                logger.info("Run command: {}", command.toString());
                ExecResult execResult = jSchTools.executeInLogger(command.toString());
                if (execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the rsync command. Exit code: {}", execResult.getExitCode());
                }

                logger.info("Delete cert");
                jSchTools.createAndUseSftpChannel(consumer -> {
                    consumer.rm(tmpKeyfile);
                });
            } finally {
                jSchTools.disconnect();
            }

        }
    }

}
