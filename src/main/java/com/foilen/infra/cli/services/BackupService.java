/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import com.foilen.infra.api.model.resource.ResourceBucket;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.SshException;
import com.foilen.infra.cli.commands.model.BackupResult;
import com.foilen.infra.cli.commands.model.BackupResults;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.unixuser.UnixUser;
import com.foilen.smalltools.consolerunner.ConsoleRunner;
import com.foilen.smalltools.tools.*;
import org.slf4j.event.Level;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Component
public class BackupService extends AbstractBasics {

    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private SshService sshService;

    private void backupDirectArchive(InfraResourceApiService infraResourceApiService, String backupFolder, String timestamp, BackupResults results, ResourceBucket machineBucket) {
        Machine machine = JsonTools.clone(machineBucket.getResourceDetails().getResource(), Machine.class);
        String machineName = machine.getName();
        logger.info("Backuping machine {}", machineName);

        List<UnixUser> unixUsers = machineBucket.getLinksFrom().stream() //
                .filter(link -> link.getLinkType().equals(LinkTypeConstants.INSTALLED_ON)) //
                .filter(link -> link.getOtherResource().getResourceType().equals(UnixUser.RESOURCE_TYPE)) //
                .map(link -> {
                    String resourceId = InfraResourceUtils.getResourceId(link.getOtherResource());
                    ResponseResourceBucket unixUserBucket = infraResourceApiService.resourceFindById(resourceId);
                    exceptionService.displayResultAndThrow(unixUserBucket, "Get the unix user " + resourceId);
                    return JsonTools.clone(unixUserBucket.getItem().getResourceDetails().getResource(), UnixUser.class);
                }) //
                .sorted() //
                .collect(Collectors.toList());

        for (UnixUser unixUser : unixUsers) {

            backupDirectArchive(backupFolder, timestamp, results, machineName, unixUser);

        }
    }

    private void backupDirectArchive(String backupFolder, String timestamp, BackupResults results, String machineName, UnixUser unixUser) {
        logger.info("Backuping {} / {}", machineName, unixUser.getName());

        String owner = InfraResourceUtils.getOwner(unixUser);
        if (owner == null) {
            owner = "NO_OWNER";
        }

        // Create path to the file
        String backupPath = backupFolder + "/" + timestamp + "/" + owner + "/" + machineName + "-" + unixUser.getName() + ".tgz";
        logger.info("Backuping to {}", backupPath);
        AssertTools.assertTrue(DirectoryTools.createPathToFile(backupPath), "Could not create the path to " + backupPath);

        // Archive
        File backupFile = new File(backupPath);
        AtomicBoolean completed = new AtomicBoolean();

        // Show the progress
        ExecutorsTools.getCachedDaemonThreadPool().submit(() -> {
            while (!completed.get()) {
                ThreadTools.sleep(1000);
                if (!completed.get()) {
                    logger.info("Backuping to {} ; Size in bytes {} ; Size {}", backupPath, backupFile.length(), SpaceConverterTools.convertToBiggestBUnit(backupFile.length()));
                }
            }
        });

        // Execute
        long executionTimeMs = System.currentTimeMillis();
        BackupResult result;
        try {
            sshService.executeCommandInFileTarget(machineName, "tar -zc " + unixUser.getHomeFolder(), backupPath);
            executionTimeMs = System.currentTimeMillis() - executionTimeMs;
            result = results.addResult(true, owner, machineName, unixUser.getName(), executionTimeMs, backupFile.length());
        } catch (SshException e) {
            executionTimeMs = System.currentTimeMillis() - executionTimeMs;
            result = results.addResult(false, owner, machineName, unixUser.getName(), executionTimeMs, backupFile.length());
            result.addError(e.getClass().getSimpleName() + " " + e.getMessage());
            for (String l : e.getLastErrorLines()) {
                result.addError(l);
            }
        } catch (Exception e) {
            executionTimeMs = System.currentTimeMillis() - executionTimeMs;
            result = results.addResult(false, owner, machineName, unixUser.getName(), executionTimeMs, backupFile.length());
            result.addError(e.getClass().getSimpleName() + " " + e.getCause().getMessage());
        } finally {
            completed.set(true);
        }

        // Add to the results
        logger.info("Backuping {} / {} took {} and copied {}", machineName, unixUser.getName(), TimeConverterTools.convertToTextFromMs(executionTimeMs),
                SpaceConverterTools.convertToBiggestBUnit(backupFile.length()));

        // Keep details of errors if not successful
        if (!result.isSuccess()) {
            FileTools.writeFile(result.toString(), backupPath + ".errors");
        }
    }

    public BackupResults backupDirectArchive(String backupFolder, String timestamp, String machineName) {

        BackupResults results = new BackupResults();

        try {

            DirectoryTools.createPath(backupFolder + "/" + timestamp);

            // Get the machine on the target
            InfraApiService infraApiService = profileService.getTargetInfraApiService();
            InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
            RequestResourceSearch search = new RequestResourceSearch().setResourceType(Machine.RESOURCE_TYPE);
            search.getProperties().put(Machine.PROPERTY_NAME, machineName);
            ResponseResourceBucket sourceMachineBucket = infraResourceApiService.resourceFindOne(search);
            exceptionService.displayResultAndThrow(sourceMachineBucket, "Get the machine");

            ResourceBucket machineBucket = sourceMachineBucket.getItem();
            backupDirectArchive(infraResourceApiService, backupFolder, timestamp, results, machineBucket);

            results.setCompleted(true);

        } catch (Exception e) {
            logger.error("Got an error", e);
        }

        // Put the summary in the file
        if (results.isCompleted()) {
            FileTools.writeFile(results.toString(), backupFolder + "/" + timestamp + "/BACKUP_COMPLETED.txt");
        } else {
            FileTools.writeFile(results.toString(), backupFolder + "/" + timestamp + "/BACKUP_INCOMPLETE.txt");
        }

        return results;

    }

    public BackupResults backupDirectArchiveAll(String backupFolder, String timestamp) {

        BackupResults results = new BackupResults();

        try {

            DirectoryTools.createPath(backupFolder + "/" + timestamp);

            // Get all the machines on the target
            InfraApiService infraApiService = profileService.getTargetInfraApiService();
            InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
            ResponseResourceBuckets sourceMachineBucket = infraResourceApiService.resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(Machine.RESOURCE_TYPE));
            exceptionService.displayResultAndThrow(sourceMachineBucket, "Get the machines");

            List<ResourceBucket> machineBuckets = sourceMachineBucket.getItems();
            for (ResourceBucket machineBucket : machineBuckets) {
                backupDirectArchive(infraResourceApiService, backupFolder, timestamp, results, machineBucket);
            }

            results.setCompleted(true);

        } catch (Exception e) {
            logger.error("Got an error", e);
        }

        // Put the summary in the file
        if (results.isCompleted()) {
            FileTools.writeFile(results.toString(), backupFolder + "/" + timestamp + "/BACKUP_COMPLETED.txt");
        } else {
            FileTools.writeFile(results.toString(), backupFolder + "/" + timestamp + "/BACKUP_INCOMPLETE.txt");
        }

        return results;

    }

    private void backupRsyncArchive(InfraResourceApiService infraResourceApiService, String backupFolder, String timestamp, BackupResults results, ResourceBucket machineBucket) {
        Machine machine = JsonTools.clone(machineBucket.getResourceDetails().getResource(), Machine.class);
        String machineName = machine.getName();
        logger.info("Backuping machine {}", machineName);

        List<UnixUser> unixUsers = machineBucket.getLinksFrom().stream() //
                .filter(link -> link.getLinkType().equals(LinkTypeConstants.INSTALLED_ON)) //
                .filter(link -> link.getOtherResource().getResourceType().equals(UnixUser.RESOURCE_TYPE)) //
                .map(link -> {
                    String resourceId = InfraResourceUtils.getResourceId(link.getOtherResource());
                    ResponseResourceBucket unixUserBucket = infraResourceApiService.resourceFindById(resourceId);
                    exceptionService.displayResultAndThrow(unixUserBucket, "Get the unix user " + resourceId);
                    return JsonTools.clone(unixUserBucket.getItem().getResourceDetails().getResource(), UnixUser.class);
                }) //
                .sorted() //
                .collect(Collectors.toList());
        Queue<UnixUser> backupQueue = new LinkedList<>();
        backupQueue.addAll(unixUsers);

        int maxProcessing = backupQueue.size() * 10;

        while (!backupQueue.isEmpty() && maxProcessing > 0) {
            --maxProcessing;
            var unixUser = backupQueue.poll();
            try {
                logger.info("Processing {}/{}", machineName, unixUser.getName());
                backupRsyncArchive(backupFolder, timestamp, results, machineName, unixUser);
            } catch (Exception e) {
                logger.info("Got a problem with {}/{} . Will retry", machineName, unixUser.getName());
                backupQueue.add(unixUser);
                ThreadTools.sleep(2000);
            }
        }
    }

    private void backupRsyncArchive(String backupFolder, String timestamp, BackupResults results, String machineName, UnixUser unixUser) {
        logger.info("Backuping {} / {}", machineName, unixUser.getName());

        String owner = InfraResourceUtils.getOwner(unixUser);
        if (owner == null) {
            owner = "NO_OWNER";
        }

        // Create path to the raw
        String rawBackupPath = backupFolder + "/raw/" + machineName + "/" + unixUser.getName() + "/";
        logger.info("Doing rsync to {}", rawBackupPath);
        AssertTools.assertTrue(DirectoryTools.createPathToFile(rawBackupPath), "Could not create the path " + rawBackupPath);

        long executionTimeMs = System.currentTimeMillis();
        BackupResult result;
        AtomicBoolean completed = new AtomicBoolean();
        String backupPath = backupFolder + "/" + timestamp + "/" + owner + "/" + machineName + "-" + unixUser.getName() + ".tgz";
        File backupFile = new File(backupPath);
        try {

            // Rsync
            sshService.syncFilesRemoteToLocal(machineName, unixUser.getName(), rawBackupPath);

            // Create path to the archive
            logger.info("archiving to {}", backupPath);
            AssertTools.assertTrue(DirectoryTools.createPathToFile(backupPath), "Could not create the path to " + backupPath);

            // Archive

            // Show the progress
            ExecutorsTools.getCachedDaemonThreadPool().submit(() -> {
                while (!completed.get()) {
                    ThreadTools.sleep(1000);
                    if (!completed.get()) {
                        logger.info("Backuping to {} ; Size in bytes {} ; Size {}", backupPath, backupFile.length(), SpaceConverterTools.convertToBiggestBUnit(backupFile.length()));
                    }
                }
            });

            // Archive
            ConsoleRunner runner = new ConsoleRunner();
            runner.setCommand("tar");
            runner.setWorkingDirectory(rawBackupPath);
            runner.addArguments("-zcf", backupPath, ".");

            logger.info("Run command: {} {}", runner.getCommand(), runner.getArguments());
            int status = runner.executeWithLogger(logger, Level.INFO);
            if (status != 0) {
                logger.error("There was a problem executing the archiving command. Exit code: {}", status);
                throw new CliException("There was a problem executing the archiving command");
            }
            executionTimeMs = System.currentTimeMillis() - executionTimeMs;
            result = results.addResult(true, owner, machineName, unixUser.getName(), executionTimeMs, backupFile.length());
        } catch (Exception e) {
            executionTimeMs = System.currentTimeMillis() - executionTimeMs;
            result = results.addResult(false, owner, machineName, unixUser.getName(), executionTimeMs, backupFile.length());
            result.addError(e.getClass().getSimpleName() + " " + e.getCause().getMessage());
        } finally {
            completed.set(true);
        }

        // Add to the results
        logger.info("Backuping {} / {} took {} and copied {}", machineName, unixUser.getName(), TimeConverterTools.convertToTextFromMs(executionTimeMs),
                SpaceConverterTools.convertToBiggestBUnit(backupFile.length()));

        // Keep details of errors if not successful
        if (!result.isSuccess()) {
            FileTools.writeFile(result.toString(), backupPath + ".errors");
        }
    }

    public BackupResults backupRsyncArchive(String backupFolder, String timestamp, String machineName) {

        BackupResults results = new BackupResults();

        try {

            DirectoryTools.createPath(backupFolder + "/" + timestamp);

            // Get the machine on the target
            InfraApiService infraApiService = profileService.getTargetInfraApiService();
            InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
            RequestResourceSearch search = new RequestResourceSearch().setResourceType(Machine.RESOURCE_TYPE);
            search.getProperties().put(Machine.PROPERTY_NAME, machineName);
            ResponseResourceBucket sourceMachineBucket = infraResourceApiService.resourceFindOne(search);
            exceptionService.displayResultAndThrow(sourceMachineBucket, "Get the machine");

            ResourceBucket machineBucket = sourceMachineBucket.getItem();
            backupRsyncArchive(infraResourceApiService, backupFolder, timestamp, results, machineBucket);

            results.setCompleted(true);

        } catch (Exception e) {
            logger.error("Got an error", e);
        }

        // Put the summary in the file
        if (results.isCompleted()) {
            FileTools.writeFile(results.toString(), backupFolder + "/" + timestamp + "/BACKUP_COMPLETED.txt");
        } else {
            FileTools.writeFile(results.toString(), backupFolder + "/" + timestamp + "/BACKUP_INCOMPLETE.txt");
        }

        return results;

    }

    public BackupResults backupRsyncArchiveAll(String backupFolder, String timestamp) {

        BackupResults results = new BackupResults();

        try {

            DirectoryTools.createPath(backupFolder + "/" + timestamp);

            // Get all the machines on the target
            InfraApiService infraApiService = profileService.getTargetInfraApiService();
            InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
            ResponseResourceBuckets sourceMachineBucket = infraResourceApiService.resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(Machine.RESOURCE_TYPE));
            exceptionService.displayResultAndThrow(sourceMachineBucket, "Get the machines");

            List<ResourceBucket> machineBuckets = sourceMachineBucket.getItems();
            for (ResourceBucket machineBucket : machineBuckets) {
                backupRsyncArchive(infraResourceApiService, backupFolder, timestamp, results, machineBucket);
            }

            results.setCompleted(true);

        } catch (Exception e) {
            logger.error("Got an error", e);
        }

        // Put the summary in the file
        if (results.isCompleted()) {
            FileTools.writeFile(results.toString(), backupFolder + "/" + timestamp + "/BACKUP_COMPLETED.txt");
        } else {
            FileTools.writeFile(results.toString(), backupFolder + "/" + timestamp + "/BACKUP_INCOMPLETE.txt");
        }

        return results;

    }

}
