/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.foilen.infra.api.model.resource.ResourceBucket;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.cli.SshException;
import com.foilen.infra.cli.commands.model.BackupResult;
import com.foilen.infra.cli.commands.model.BackupResults;
import com.foilen.infra.plugin.v1.model.resource.LinkTypeConstants;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.infra.resource.unixuser.UnixUser;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.AssertTools;
import com.foilen.smalltools.tools.DirectoryTools;
import com.foilen.smalltools.tools.ExecutorsTools;
import com.foilen.smalltools.tools.FileTools;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.SpaceConverterTools;
import com.foilen.smalltools.tools.ThreadTools;
import com.foilen.smalltools.tools.TimeConverterTools;

@Component
public class BackupService extends AbstractBasics {

    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;
    @Autowired
    private SshService sshService;

    public BackupResults backupAll(String backupFolder, String timestamp) {

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
                Machine machine = JsonTools.clone(machineBucket.getResourceDetails().getResource(), Machine.class);
                String machineName = machine.getName();
                logger.info("Backuping {}", machineName);

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
                    logger.info("Backuping {} / {} took {} and copied {}", machineName, unixUser.getName(), TimeConverterTools.convertToText(executionTimeMs),
                            SpaceConverterTools.convertToBiggestBUnit(backupFile.length()));

                    // Keep details of errors if not successful
                    if (!result.isSuccess()) {
                        FileTools.writeFile(result.toString(), backupPath + ".errors");
                    }

                }
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
