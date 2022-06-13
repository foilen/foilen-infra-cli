/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import com.foilen.infra.cli.model.profile.ProfileHasPassword;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.SshException;
import com.foilen.infra.cli.model.MysqlSyncSide;
import com.foilen.infra.cli.model.profile.ProfileHasCert;
import com.foilen.infra.cli.model.profile.ProfileHasHostname;
import com.foilen.infra.cli.model.profile.ProfileHasUser;
import com.foilen.smalltools.consolerunner.ConsoleRunner;
import com.foilen.smalltools.iterable.FileLinesIterable;
import com.foilen.smalltools.jsch.JSchTools;
import com.foilen.smalltools.jsch.SshLogin;
import com.foilen.smalltools.shell.ExecResult;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.CloseableTools;
import com.foilen.smalltools.tools.DirectoryTools;
import com.foilen.smalltools.tools.ExecutorsTools;
import com.foilen.smalltools.tools.SecureRandomTools;
import com.foilen.smalltools.tools.ThreadTools;
import com.google.common.base.Strings;
import com.google.common.collect.EvictingQueue;

@Component
public class SshService extends AbstractBasics {

    static protected String trimSlashes(String text) {
        if (text == null) {
            text = "";
        }
        while (text.startsWith("/")) {
            if (text.length() > 1) {
                text = text.substring(1);
            } else {
                text = "";
            }
        }
        while (text.endsWith("/")) {
            if (text.length() > 1) {
                text = text.substring(0, text.length() - 1);
            } else {
                text = "";
            }
        }
        return text;
    }

    @Autowired
    private ProfileService profileService;

    @Autowired
    private UnixUserService unixUserService;

    public JSchTools connect(MysqlSyncSide side) {
        SshLogin sshLogin = new SshLogin(side.getMachineHost(), side.getMachineUsername()).autoApproveHostKey();
        if (!Strings.isNullOrEmpty(side.getMachineCert())) {
            sshLogin.withPrivateKey(side.getMachineCert());
        } else if (!Strings.isNullOrEmpty(side.getMachinePassword())) {
            sshLogin.withPassword(side.getMachinePassword());
        } else {
            throw new CliException("Machine does not have a password or certificate to connect to it");
        }
        JSchTools jsch = new JSchTools().login(sshLogin);
        return jsch;
    }

    public void executeCommandInFileTarget(String hostname, String command, String stdOutFile) {

        ProfileHasCert targetProfileHasCert = profileService.getTargetAsOrFail(ProfileHasCert.class);
        ProfileHasPassword targetProfileHasPassword = profileService.getTargetAsOrFail(ProfileHasPassword.class);

        JSchTools jSchTools = new JSchTools();
        AtomicBoolean completed = new AtomicBoolean();
        EvictingQueue<String> errorLinesQueue = EvictingQueue.create(10);
        EvictingQueue<Long> progressDeltas = EvictingQueue.create(6); // No progress for 30 seconds (6 checks every 5 seconds)
        try {

            jSchTools.login(new SshLogin(hostname, "root") //
                    .withPrivateKey(targetProfileHasCert.getSshCertificateFile()) //
                    .withPassword(targetProfileHasPassword.getSshPassword()) //
                    .autoApproveHostKey());

            // Keep lasts errors lines
            PipedInputStream errInputStream = new PipedInputStream();
            OutputStream errOutputStream = new PipedOutputStream(errInputStream);

            CountDownLatch errCompleted = new CountDownLatch(1);
            ExecutorsTools.getCachedDaemonThreadPool().submit(() -> {
                logger.info("Start piping errors");
                FileLinesIterable errLinesIterable = new FileLinesIterable();
                errLinesIterable.openStream(errInputStream);

                while (errLinesIterable.hasNext()) {
                    String line = errLinesIterable.next();
                    errorLinesQueue.add(line);
                }

                errCompleted.countDown();
                logger.info("Completed piping errors");
            });

            File file = new File(stdOutFile);
            FileOutputStream outOutputStream = new FileOutputStream(file);

            // Kill if no progress
            ExecutorsTools.getCachedDaemonThreadPool().submit(() -> {

                long lastSize = 0;

                while (!completed.get()) {
                    ThreadTools.sleep(5000);
                    if (!completed.get()) {
                        long currentSize = file.length();
                        long delta = currentSize - lastSize;
                        lastSize = currentSize;

                        progressDeltas.add(delta);

                        if (delta == 0) {
                            boolean notAllZeros = progressDeltas.stream().anyMatch(it -> it != 0);
                            if (!notAllZeros) {
                                logger.error("No progress for 30 seconds. Killing");
                                errorLinesQueue.add("No progress for 30 seconds. Killing");
                                CloseableTools.close(outOutputStream);
                                CloseableTools.close(errOutputStream);
                                jSchTools.disconnect();
                                break;
                            }
                        }
                    }

                }
            });

            // Execute
            ExecResult result = jSchTools.executeOutputStreams(command, outOutputStream, errOutputStream);
            if (result.getExitCode() != 0) {
                errCompleted.await();
                throw new SshException("There was a problem executing the command. Exit code: " + result.getExitCode(), errorLinesQueue);
            }
        } catch (SshException e) {
            throw e;
        } catch (Exception e) {
            throw new SshException("Problem executing the command", e, errorLinesQueue);
        } finally {
            jSchTools.disconnect();
            completed.set(true);
        }

    }

    public void executeCommandInLoggerTarget(String hostname, String command) {

        ProfileHasCert targetProfileHasCert = profileService.getTargetAsOrFail(ProfileHasCert.class);
        ProfileHasPassword targetProfileHasPassword = profileService.getTargetAsOrFail(ProfileHasPassword.class);

        JSchTools jSchTools = new JSchTools();
        try {

            jSchTools.login(new SshLogin(hostname, "root") //
                    .withPrivateKey(targetProfileHasCert.getSshCertificateFile()) //
                    .withPassword(targetProfileHasPassword.getSshPassword()) //
                    .autoApproveHostKey());

            jSchTools.executeInLogger(command);
        } finally {
            jSchTools.disconnect();
        }

    }

    /**
     * Sync files between machines using rsync
     *
     * @param sourceHostname
     *            the source host name (optional) will use profile
     * @param sourceUsername
     *            the source user name
     * @param targetHostname
     *            the target host name (optional) will use profile
     * @param targetUsername
     *            the target user name (optional) will use sourceUsername
     * @param subFolder
     *            the sub folder to sync (optional) will copy all the home folders
     */
    public void syncFiles(String sourceHostname, String sourceUsername, String targetHostname, String targetUsername, String subFolder) {

        subFolder = trimSlashes(subFolder);

        if (sourceHostname == null) {
            ProfileHasHostname value = profileService.getSourceAs(ProfileHasHostname.class);
            if (value != null && value.getHostname() != null) {
                sourceHostname = value.getHostname();
            }
            if (sourceHostname == null) {
                throw new CliException("You must specify a sourceHostname");
            }
        }
        if (targetHostname == null) {
            ProfileHasHostname value = profileService.getTargetAs(ProfileHasHostname.class);
            if (value != null && value.getHostname() != null) {
                targetHostname = value.getHostname();
            }
            if (targetHostname == null) {
                throw new CliException("You must specify a targetHostname");
            }
        }
        if (targetUsername == null) {
            targetUsername = sourceUsername;
        }

        // Check one side is using cert (jsch to the one with password and rsync using the cert)
        boolean sourceHasCert = false;
        boolean targetHasCert = false;
        ProfileHasCert sourceProfileHasCert = profileService.getSourceAs(ProfileHasCert.class);
        ProfileHasPassword sourceProfileHasPassword = profileService.getSourceAs(ProfileHasPassword.class);
        ProfileHasCert targetProfileHasCert = profileService.getTargetAs(ProfileHasCert.class);
        ProfileHasPassword targetProfileHasPassword = profileService.getTargetAs(ProfileHasPassword.class);
        if (sourceProfileHasCert != null) {
            sourceHasCert = sourceProfileHasCert.getSshCertificateFile() != null;
        }
        if (targetProfileHasCert != null) {
            targetHasCert = targetProfileHasCert.getSshCertificateFile() != null;
        }
        if (!sourceHasCert && !targetHasCert) {
            throw new CliException("At least one side must use certificate");
        }

        ProfileHasUser sourceProfileHasUser = profileService.getSourceAs(ProfileHasUser.class);
        String sourceCertUsername = "root";
        if (sourceProfileHasUser != null && sourceProfileHasUser.getUsername() != null) {
            sourceCertUsername = sourceProfileHasUser.getUsername();
        }
        ProfileHasUser targetProfileHasUser = profileService.getTargetAs(ProfileHasUser.class);
        String targetCertUsername = "root";
        if (targetProfileHasUser != null && targetProfileHasUser.getUsername() != null) {
            targetCertUsername = targetProfileHasUser.getUsername();
        }

        if (!sourceHasCert) {

            // Log on source and push to target using cert
            logger.info("Log on source and push to target using cert");
            String password = unixUserService.getOrCreateUserPassword(profileService.getSourceInfraApiService(), sourceUsername, "source");
            waitCanLogin(sourceHostname, sourceUsername, password, 2 * 60);

            String tmpKeyfile = "/tmp/" + SecureRandomTools.randomHexString(10);
            JSchTools jSchTools = new JSchTools();
            try {
                // Send target cert
                logger.info("Send target cert to source");
                jSchTools.login(new SshLogin(sourceHostname, sourceUsername).withPassword(password).autoApproveHostKey());
                jSchTools.createAndUseSftpChannel(consumer -> {
                    // Create
                    consumer.put(tmpKeyfile).close();

                    // Secure
                    consumer.chmod(00600, tmpKeyfile);

                    // Send
                    consumer.put(targetProfileHasCert.getSshCertificateFile(), tmpKeyfile);
                });

                logger.info("Log on source and push to target using cert");
                StringBuilder command = new StringBuilder();
                command.append("/usr/bin/rsync --inplace --compress-level=9 --delete -zrtv");
                command.append("e \"ssh -o StrictHostKeyChecking=no -i ").append(tmpKeyfile).append(" -l ").append(targetCertUsername).append("\" ");
                command.append("/home/").append(sourceUsername).append("/").append(subFolder).append("/ ").append(targetHostname).append(":/home/").append(targetUsername).append("/").append(subFolder)
                        .append("/");
                logger.info("Run command: {}", command.toString());
                ExecResult execResult = jSchTools.executeInLogger(command.toString());
                if (execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the rsync command. Exit code: {}", execResult.getExitCode());
                    throw new CliException("There was a problem executing the rsync command");
                }

            } finally {

                try {
                    logger.info("Delete cert");
                    jSchTools.createAndUseSftpChannel(consumer -> {
                        consumer.rm(tmpKeyfile);
                    });
                } finally {
                    jSchTools.disconnect();
                }
            }

            // Chown target
            try {
                // Send source cert
                logger.info("chown on target");
                jSchTools.login(new SshLogin(targetHostname, targetCertUsername)
                        .withPrivateKey(targetProfileHasCert.getSshCertificateFile())
                        .withPassword(targetProfileHasPassword.getSshPassword())
                        .autoApproveHostKey());

                StringBuilder command = new StringBuilder();
                command.append("/bin/chown -R ");
                command.append(targetUsername).append(":").append(targetUsername);
                command.append(" /home/").append(targetUsername).append("/");
                logger.info("Run command: {}", command.toString());
                ExecResult execResult = jSchTools.executeInLogger(command.toString());
                if (execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the chown command. Exit code: {}", execResult.getExitCode());
                    throw new CliException("There was a problem executing the chown command");
                }
            } finally {
                jSchTools.disconnect();
            }

        } else if (!targetHasCert) {

            // Log on target and pull from source using cert
            logger.info("Log on target and pull from source using cert");
            String password = unixUserService.getOrCreateUserPassword(profileService.getTargetInfraApiService(), targetUsername, "target");
            waitCanLogin(targetHostname, targetUsername, password, 2 * 60);

            String tmpKeyfile = "/tmp/" + SecureRandomTools.randomHexString(10);
            JSchTools jSchTools = new JSchTools();
            try {
                // Send source cert
                logger.info("Send source cert to target");
                jSchTools.login(new SshLogin(targetHostname, targetUsername).withPassword(password).autoApproveHostKey());
                jSchTools.createAndUseSftpChannel(consumer -> {
                    // Create
                    consumer.put(tmpKeyfile).close();

                    // Secure
                    consumer.chmod(00600, tmpKeyfile);

                    // Send
                    consumer.put(sourceProfileHasCert.getSshCertificateFile(), tmpKeyfile);
                });

                logger.info("Log on target and pull from source using cert");
                StringBuilder command = new StringBuilder();
                command.append("/usr/bin/rsync --inplace --compress-level=9 --delete -zrtv");
                command.append("e \"ssh -o StrictHostKeyChecking=no -i ").append(tmpKeyfile).append(" -l ").append(sourceCertUsername).append("\" ");
                command.append(sourceHostname).append(":/home/").append(sourceUsername).append("/").append(subFolder).append("/ /home/").append(targetUsername).append("/").append(subFolder)
                        .append("/");
                logger.info("Run command: {}", command.toString());
                ExecResult execResult = jSchTools.executeInLogger(command.toString());
                if (execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the rsync command. Exit code: {}", execResult.getExitCode());
                    throw new CliException("There was a problem executing the rsync command");
                }

            } finally {
                try {
                    logger.info("Delete cert");
                    jSchTools.createAndUseSftpChannel(consumer -> {
                        consumer.rm(tmpKeyfile);
                    });
                } finally {
                    jSchTools.disconnect();
                }
            }

        } else {

            // Both has certs ; Log on source and push to target using cert
            logger.info("Both has certs ; Log on source and push to target using cert");
            String tmpKeyfile = "/tmp/" + SecureRandomTools.randomHexString(10);
            JSchTools jSchTools = new JSchTools();
            try {
                // Send source cert
                logger.info("Send target cert to source");
                jSchTools.login(new SshLogin(sourceHostname, sourceCertUsername)
                        .withPrivateKey(sourceProfileHasCert.getSshCertificateFile())
                        .withPassword(sourceProfileHasPassword.getSshPassword())
                        .autoApproveHostKey());
                jSchTools.createAndUseSftpChannel(consumer -> {
                    // Create
                    consumer.put(tmpKeyfile).close();

                    // Secure
                    consumer.chmod(00600, tmpKeyfile);

                    // Send
                    consumer.put(targetProfileHasCert.getSshCertificateFile(), tmpKeyfile);
                });

                logger.info("Log on source and push to target using cert");
                StringBuilder command = new StringBuilder();
                command.append("/usr/bin/rsync --inplace --compress-level=9 --delete -zrtv");
                command.append("e \"ssh -o StrictHostKeyChecking=no -i ").append(tmpKeyfile).append(" -l ").append(targetCertUsername).append("\" ");
                command.append("/home/").append(sourceUsername).append("/").append(subFolder).append("/ ").append(targetHostname).append(":/home/").append(targetUsername).append("/").append(subFolder)
                        .append("/");
                logger.info("Run command: {}", command.toString());
                ExecResult execResult = jSchTools.executeInLogger(command.toString());
                if (execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the rsync command. Exit code: {}", execResult.getExitCode());
                    throw new CliException("There was a problem executing the rsync command");
                }

            } finally {
                try {
                    logger.info("Delete cert");
                    jSchTools.createAndUseSftpChannel(consumer -> {
                        consumer.rm(tmpKeyfile);
                    });
                } finally {
                    jSchTools.disconnect();
                }
            }

            // Chown target
            try {
                // Send source cert
                logger.info("chown on target");
                jSchTools.login(new SshLogin(targetHostname, targetCertUsername)
                        .withPrivateKey(targetProfileHasCert.getSshCertificateFile())
                        .withPassword(targetProfileHasPassword.getSshPassword())
                        .autoApproveHostKey());

                StringBuilder command = new StringBuilder();
                command.append("/bin/chown -R ");
                command.append(targetUsername).append(":").append(targetUsername);
                command.append(" /home/").append(targetUsername).append("/");
                logger.info("Run command: {}", command.toString());
                ExecResult execResult = jSchTools.executeInLogger(command.toString());
                if (execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the chown command. Exit code: {}", execResult.getExitCode());
                    throw new CliException("There was a problem executing the chown command");
                }
            } finally {
                jSchTools.disconnect();
            }

        }

    }

    /**
     * Sync files between machines using rsync
     *
     * @param sourceHostname
     *            the source host name
     * @param sourceUsername
     *            the source user name
     * @param localTargetPath
     *            the local target folder
     */
    public void syncFilesRemoteToLocal(String sourceHostname, String sourceUsername, String localTargetPath) {

        DirectoryTools.createPath(localTargetPath);

        if (sourceHostname == null) {
            ProfileHasHostname value = profileService.getSourceAs(ProfileHasHostname.class);
            if (value != null && value.getHostname() != null) {
                sourceHostname = value.getHostname();
            }
            if (sourceHostname == null) {
                throw new CliException("You must specify a sourceHostname");
            }
        }

        // Check is using cert (rsync using the cert)
        boolean sourceHasCert = false;
        ProfileHasCert sourceProfileHasCert = profileService.getSourceAs(ProfileHasCert.class);
        if (sourceProfileHasCert != null) {
            sourceHasCert = sourceProfileHasCert.getSshCertificateFile() != null;
        }
        if (!sourceHasCert) {
            throw new CliException("The source must use certificate");
        }

        ProfileHasUser sourceProfileHasUser = profileService.getSourceAs(ProfileHasUser.class);
        String sourceCertUsername = "root";
        if (sourceProfileHasUser != null && sourceProfileHasUser.getUsername() != null) {
            sourceCertUsername = sourceProfileHasUser.getUsername();
        }

        // Local rsync
        logger.info("Local rsync using cert");

        ConsoleRunner runner = new ConsoleRunner();
        runner.setCommand("/usr/bin/rsync");
        runner.addArguments("--inplace", "--compress-level=9", "--delete", "-zrtv");
        runner.addArguments("-e", "ssh -o StrictHostKeyChecking=no -i " + sourceProfileHasCert.getSshCertificateFile() + " -l " + sourceCertUsername);
        runner.addArguments(sourceHostname + ":/home/" + sourceUsername + "/");
        runner.addArguments(localTargetPath);

        logger.info("Run command: {} {}", runner.getCommand(), runner.getArguments());
        int status = runner.executeWithLogger(logger, Level.INFO);
        if (status != 0) {
            logger.error("There was a problem executing the rsync command. Exit code: {}", status);
            throw new CliException("There was a problem executing the rsync command");
        }

    }

    public void waitCanLogin(String hostname, String username, String password, int timeoutSeconds) {
        long maxTime = System.currentTimeMillis() + timeoutSeconds * 1000L;

        SshLogin sshLogin = new SshLogin(hostname, username).withPassword(password).autoApproveHostKey();
        while (!JSchTools.canLogin(sshLogin)) {

            if (System.currentTimeMillis() >= maxTime) {
                throw new CliException("Could not SSH to " + hostname + " with user " + username + " with a password");
            }

            // Wait
            logger.info("Waiting for password to propagate. Retry in 10 seconds");
            ThreadTools.sleep(10000);
        }
        System.out.println();

    }

    public void waitUserIsPresent(String hostname, String username) {

        ProfileHasCert targetProfileHasCert = profileService.getTargetAsOrFail(ProfileHasCert.class);
        ProfileHasPassword targetProfileHasPassword = profileService.getTargetAsOrFail(ProfileHasPassword.class);

        JSchTools jSchTools = new JSchTools();
        try {

            jSchTools.login(new SshLogin(hostname, "root") //
                    .withPrivateKey(targetProfileHasCert.getSshCertificateFile()) //
                    .withPassword(targetProfileHasPassword.getSshPassword()) //
                    .autoApproveHostKey());

            jSchTools.executeInLogger("while(! grep '^" + username + ":' /etc/passwd); do\n" //
                    + "sleep 1s;\n" //
                    + "done");
        } finally {
            jSchTools.disconnect();
        }

    }

}
