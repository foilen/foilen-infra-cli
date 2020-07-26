/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2020 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.MysqlSyncSide;
import com.foilen.infra.cli.model.profile.AbstractProfile;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.model.profile.ServerProfile;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.cli.services.SshService;
import com.foilen.infra.cli.services.UnixUserService;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.mariadb.MariaDBDatabase;
import com.foilen.infra.resource.mariadb.MariaDBServer;
import com.foilen.infra.resource.mariadb.MariaDBUser;
import com.foilen.infra.resource.unixuser.UnixUser;
import com.foilen.smalltools.jsch.JSchTools;
import com.foilen.smalltools.shell.ExecResult;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.AssertTools;
import com.foilen.smalltools.tools.ExecutorsTools;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.SecureRandomTools;
import com.foilen.smalltools.tools.ThreadTools;
import com.foilen.smalltools.tuple.Tuple3;

@ShellComponent
public class SyncCommands extends AbstractBasics {

    @Autowired
    private ProfileService profileService;
    @Autowired
    private SshService sshService;
    @Autowired
    private UnixUserService unixUserService;

    private MysqlSyncSide getSyncSide(boolean isTarget, AbstractProfile profile, String databaseServerName, String databaseName, String databaseUsername, String databasePassword) {

        MysqlSyncSide side = new MysqlSyncSide();
        side.setDbUsername(databaseUsername);
        side.setDbPassword(databasePassword);

        String profileType = isTarget ? "target" : "source";

        if (profile instanceof ApiProfile) {
            ApiProfile apiProfile = (ApiProfile) profile;

            logger.info("[{}] uses API", profileType);
            InfraApiService apiService = profileService.getInfraApiService(apiProfile, profileType);
            InfraResourceApiService resourceApiService = apiService.getInfraResourceApiService();

            logger.info("[{}] search MariaDB Server {}", profileType, databaseServerName);
            ResponseResourceBucket mariadbServerBucket = resourceApiService.resourceFindOne(new RequestResourceSearch() //
                    .setResourceType("MariaDB Server") //
                    .setProperties(Collections.singletonMap(MariaDBServer.PROPERTY_NAME, databaseServerName)));
            if (!mariadbServerBucket.isSuccess() || mariadbServerBucket.getItem() == null) {
                throw new CliException("Could not get the MariaDB Server: " + JsonTools.compactPrint(mariadbServerBucket));
            }

            logger.info("[{}] search MariaDB Database {} on that server", profileType, databaseName);
            Optional<MariaDBDatabase> mariaDBDatabaseOptional = mariadbServerBucket.getItem().getLinksFrom().stream() //
                    .filter(link -> {
                        ResourceDetails otherResource = link.getOtherResource();
                        if (LinkTypeConstants.INSTALLED_ON.equals(link.getLinkType()) //
                                && "MariaDB Database".equals(otherResource.getResourceType())) {
                            MariaDBDatabase database = JsonTools.clone(otherResource.getResource(), MariaDBDatabase.class);
                            return databaseName.equals(database.getName());
                        } else {
                            return false;
                        }
                    }) //
                    .map(link -> JsonTools.clone(link.getOtherResource().getResource(), MariaDBDatabase.class)) //
                    .findAny();
            if (!mariaDBDatabaseOptional.isPresent()) {
                throw new CliException("The MariaDB Server does not contain the database");
            }

            // Server installed on machine
            logger.info("[{}] search Machine on which that server is installed", profileType);
            Optional<Machine> machineOptional = mariadbServerBucket.getItem().getLinksTo().stream() //
                    .filter(link -> LinkTypeConstants.INSTALLED_ON.equals(link.getLinkType()) //
                            && "Machine".equals(link.getOtherResource().getResourceType())) //
                    .map(link -> JsonTools.clone(link.getOtherResource().getResource(), Machine.class)) //
                    .findAny();
            if (!machineOptional.isPresent()) {
                throw new CliException("The MariaDB Server is not installed on any machine");
            }
            Machine machine = machineOptional.get();
            side.setMachineHost(machine.getName());

            // Server run as unix user
            logger.info("[{}] search the unix user that is running the server", profileType);
            Optional<UnixUser> unixUserOptional = mariadbServerBucket.getItem().getLinksTo().stream() //
                    .filter(link -> LinkTypeConstants.RUN_AS.equals(link.getLinkType()) //
                            && UnixUser.RESOURCE_TYPE.equals(link.getOtherResource().getResourceType())) //
                    .map(link -> JsonTools.clone(link.getOtherResource().getResource(), UnixUser.class)) //
                    .findAny();
            if (!unixUserOptional.isPresent()) {
                throw new CliException("The MariaDB Server has no unix user");
            }
            UnixUser unixUser = unixUserOptional.get();
            side.setMachineUsername(unixUser.getName());
            side.setMachinePassword(unixUserService.getOrCreateUserPassword(apiService, unixUser.getName(), profileType));
            sshService.waitCanLogin(side.getMachineHost(), side.getMachineUsername(), side.getMachinePassword(), 120);

            // Retrieve the MariaDB database resource
            logger.info("[{}] search MariaDB Database {}", profileType, databaseName);
            ResponseResourceBucket mariadbDatabaseBucket = resourceApiService.resourceFindOne(new RequestResourceSearch() //
                    .setResourceType("MariaDB Database") //
                    .setProperties(Collections.singletonMap(MariaDBServer.PROPERTY_NAME, databaseName)));
            if (!mariadbDatabaseBucket.isSuccess() || mariadbDatabaseBucket.getItem() == null) {
                throw new CliException("Could not get the MariaDB Database: " + JsonTools.compactPrint(mariadbDatabaseBucket));
            }

            // Database users
            logger.info("[{}] check the permissions of the MariaDB users", profileType);
            Map<MariaDBUser, Tuple3<Boolean, Boolean, Boolean>> permissionsRWAPerUser = new HashMap<>();
            mariadbDatabaseBucket.getItem().getLinksFrom().stream() //
                    .filter(link -> "MariaDB User".equals(link.getOtherResource().getResourceType())) //
                    .forEach(link -> {
                        MariaDBUser user = JsonTools.clone(link.getOtherResource().getResource(), MariaDBUser.class);
                        Tuple3<Boolean, Boolean, Boolean> permissionsRWA = permissionsRWAPerUser.get(user);
                        if (permissionsRWA == null) {
                            permissionsRWA = new Tuple3<>();
                            permissionsRWAPerUser.put(user, permissionsRWA);
                        }
                        switch (link.getLinkType()) {
                        case MariaDBUser.LINK_TYPE_READ:
                            permissionsRWA.setA(true);
                            break;
                        case MariaDBUser.LINK_TYPE_WRITE:
                            permissionsRWA.setB(true);
                            break;
                        case MariaDBUser.LINK_TYPE_ADMIN:
                            permissionsRWA.setC(true);
                            break;
                        }
                    });
            Optional<MariaDBUser> mariaDbUserOptional = permissionsRWAPerUser.entrySet().stream() //
                    .filter(entry -> isTarget ? //
                            entry.getValue().getB() && entry.getValue().getC() : // Target: Write and Admin
                            entry.getValue().getA()) // Source: Read
                    .map(entry -> entry.getKey()) //
                    .findAny();
            if (!mariaDbUserOptional.isPresent()) {
                throw new CliException("The MariaDB Database has no user with the right permissions");
            }
            MariaDBUser mariaDBUser = mariaDbUserOptional.get();
            side.setDbUsername(mariaDBUser.getName());
            side.setDbPassword(mariaDBUser.getPassword());

            // Connect on the machine and check the host:port of mariadb
            JSchTools jsch = sshService.connect(side);
            try {
                ExecResult result = jsch.executeInMemory("cat /var/infra-endpoints/" + databaseServerName + "_MYSQL_TCP");
                String[] hostPort = result.getStdOutAsString().split(":");
                if (hostPort.length != 2) {
                    logger.error("Could not retrieve the endpoints details. Exit code {}", result.getExitCode());
                    logger.error("STDOUT: {}", result.getStdOutAsString());
                    logger.error("STDERR: {}", result.getStdErrAsString());
                    throw new CliException("Could not retrieve the endpoints details of the database in /var/infra-endpoints/");
                }
                side.setDbHost(hostPort[0]);
                side.setDbPort(Integer.valueOf(hostPort[1]));
            } finally {
                jsch.disconnect();
            }

        } else if (profile instanceof ServerProfile) {
            ServerProfile serverProfile = (ServerProfile) profile;
            side.setMachineHost(serverProfile.getHostname());
            side.setMachineUsername(serverProfile.getUsername());
            side.setMachineCert(serverProfile.getSshCertificateFile());
        } else {
            throw new CliException("Profile type " + profile.getClass().getSimpleName() + " is not supported yet");
        }

        return side;
    }

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
            @ShellOption(defaultValue = ShellOption.NULL) String targetUsername, //
            @ShellOption(defaultValue = ShellOption.NULL) String subFolder //
    ) {

        sshService.syncFiles(sourceHostname, sourceUsername, targetHostname, targetUsername, subFolder);

    }

    @ShellMethod("Sync MySql by doing an dump/import")
    public void syncMysql( //
            @ShellOption(help = "MariaDB Server name when using the API", defaultValue = ShellOption.NULL) String sourceDatabaseServer, //
            String sourceDatabaseName, //
            @ShellOption(help = "MariaDB Username when not using the API", defaultValue = ShellOption.NULL) String sourceDatabaseUsername, //
            @ShellOption(help = "MariaDB Password when not using the API", defaultValue = ShellOption.NULL) String sourceDatabasePassword, //
            @ShellOption(help = "MariaDB Server name when using the API", defaultValue = ShellOption.NULL) String targetDatabaseServer, //
            String targetDatabaseName, //
            @ShellOption(help = "MariaDB Username when not using the API", defaultValue = ShellOption.NULL) String targetDatabaseUsername, //
            @ShellOption(help = "MariaDB Password when not using the API", defaultValue = ShellOption.NULL) String targetDatabasePassword //
    ) {

        MysqlSyncSide sourceSide = getSyncSide(false, profileService.getSource(), sourceDatabaseServer, sourceDatabaseName, sourceDatabaseUsername, sourceDatabasePassword);
        MysqlSyncSide targetSide = getSyncSide(true, profileService.getTarget(), targetDatabaseServer, targetDatabaseName, targetDatabaseUsername, targetDatabasePassword);

        // Validate
        AssertTools.assertNotNull(sourceSide.getMachineHost(), "Source must have a machine hostname");
        AssertTools.assertNotNull(sourceSide.getMachineUsername(), "Source must have a machine username");
        AssertTools.assertNotNull(sourceSide.getMachineCert(), "Source must have a machine cert");
        AssertTools.assertNotNull(sourceSide.getDbHost(), "Source must have a db host");
        AssertTools.assertNotNull(sourceSide.getDbUsername(), "Source must have a db username");
        AssertTools.assertNotNull(sourceSide.getDbPassword(), "Source must have a db password");

        AssertTools.assertNotNull(targetSide.getMachineHost(), "Target must have a machine hostname");
        AssertTools.assertNotNull(targetSide.getMachineUsername(), "Target must have a machine username");
        AssertTools.assertTrue(targetSide.getMachineCert() != null || targetSide.getMachinePassword() != null, "Target must have a machine cert or password");
        AssertTools.assertNotNull(targetSide.getDbHost(), "Target must have a db host");
        AssertTools.assertNotNull(targetSide.getDbUsername(), "Target must have a db username");
        AssertTools.assertNotNull(targetSide.getDbPassword(), "Target must have a db password");

        // Connect to target machine
        JSchTools jSchTools = sshService.connect(targetSide);
        try {
            // Send source cert
            logger.info("Send source cert to target");
            String tmpKeyfile = "/tmp/" + SecureRandomTools.randomHexString(10);
            jSchTools.createAndUseSftpChannel(consumer -> {
                // Create
                consumer.put(tmpKeyfile).close();

                // Secure
                consumer.chmod(00600, tmpKeyfile);

                // Send
                consumer.put(sourceSide.getMachineCert(), tmpKeyfile);
            });

            // Proxy the port (target machine calling source machine)
            int proxyPort = ((int) (Math.random() * 50000)) + 5000;
            StringBuilder proxyCommand = new StringBuilder();
            proxyCommand.append("ssh -o StrictHostKeyChecking=no -L 172.17.0.1:").append(proxyPort);
            proxyCommand.append(":").append(sourceSide.getDbHost()).append(":").append(sourceSide.getDbPort());
            proxyCommand.append(" -i ").append(tmpKeyfile).append(" ").append(sourceSide.getMachineUsername()).append("@").append(sourceSide.getMachineHost());
            logger.info("Start proxy. Command: {}", proxyCommand.toString());
            Semaphore proxyStarted = new Semaphore(0);
            AtomicBoolean proxyCompleted = new AtomicBoolean();
            AtomicBoolean finishedUsingProxy = new AtomicBoolean();
            ExecutorsTools.getCachedThreadPool().submit(() -> {
                proxyStarted.release();
                ExecResult execResult = jSchTools.executeInLogger(proxyCommand.toString());
                logger.info("Proxy command completed. Exit code: {}", execResult.getExitCode());
                if (!finishedUsingProxy.get() && execResult.getExitCode() != 0) {
                    logger.error("There was a problem executing the proxy command. Exit code: {}", execResult.getExitCode());
                }
                proxyCompleted.set(true);
            });
            proxyStarted.acquire();
            ThreadTools.sleep(2000);
            AssertTools.assertFalse(proxyCompleted.get(), "The proxy is already stopped");

            // Dump the db calling the source db
            String dumpFileName = SecureRandomTools.randomHexString(20) + ".sql";
            StringBuilder dumpCommand = new StringBuilder();
            dumpCommand.append("/usr/local/bin/docker-sudo exec mariadb ");
            dumpCommand.append("/bin/bash -c ");
            dumpCommand.append("\"mysqldump --add-drop-table --skip-add-locks --skip-comments --host=172.17.0.1 --port=").append(proxyPort);
            dumpCommand.append(" -u").append(sourceSide.getDbUsername()).append(" -p").append(sourceSide.getDbPassword()).append(" ").append(sourceDatabaseName).append(" > ").append(dumpFileName)
                    .append("\"");
            logger.info("Start dump. Command: {}", dumpCommand.toString());
            ExecResult execResult = jSchTools.executeInLogger(dumpCommand.toString());
            logger.info("Completed dump. Exit code: {}", execResult.getExitCode());
            finishedUsingProxy.set(true);
            if (execResult.getExitCode() != 0) {
                logger.error("There was a problem executing the dump command. Exit code: {}", execResult.getExitCode());
                throw new CliException("There was a problem executing the dump command");
            }

            // Import the db calling the target db
            StringBuilder importCommand = new StringBuilder();
            importCommand.append("/usr/local/bin/docker-sudo exec mariadb ");
            importCommand.append("/bin/bash -c ");
            importCommand.append("\"mysql --host=").append(targetSide.getDbHost()).append(" --port=").append(targetSide.getDbPort());
            importCommand.append(" -u").append(targetSide.getDbUsername()).append(" -p").append(targetSide.getDbPassword()).append(" ").append(targetDatabaseName).append(" < ").append(dumpFileName)
                    .append("\"");
            logger.info("Start import. Command: {}", importCommand.toString());
            execResult = jSchTools.executeInLogger(importCommand.toString());
            logger.info("Completed import. Exit code: {}", execResult.getExitCode());
            if (execResult.getExitCode() != 0) {
                logger.error("There was a problem executing the import command. Exit code: {}", execResult.getExitCode());
                throw new CliException("There was a problem executing the import command");
            }

            // Delete dump file and cert file
            jSchTools.createAndUseSftpChannel(consumer -> {
                logger.info("Delete cert");
                consumer.rm(tmpKeyfile);
                logger.info("Delete dump");
                consumer.rm(dumpFileName);
            });
        } catch (Exception e) {
            logger.error("Problem", e);
        } finally {
            jSchTools.disconnect();
        }

    }

}
