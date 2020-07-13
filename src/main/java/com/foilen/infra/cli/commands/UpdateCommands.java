/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;
import org.springframework.shell.standard.ShellOption;

import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.request.RequestResourceToUpdate;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.api.response.ResponseResourceBucket;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.OnlineFileDetails;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.model.profile.ProfileHasCert;
import com.foilen.infra.cli.services.BintrayService;
import com.foilen.infra.cli.services.DockerHubService;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.resource.infraconfig.InfraConfig;
import com.foilen.infra.resource.infraconfig.InfraConfigPlugin;
import com.foilen.infra.resource.machine.Machine;
import com.foilen.smalltools.jsch.JSchTools;
import com.foilen.smalltools.jsch.SshLogin;
import com.foilen.smalltools.shell.ExecResult;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.AssertTools;
import com.foilen.smalltools.tools.FreemarkerTools;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.StringTools;
import com.google.common.base.Strings;

@ShellComponent
public class UpdateCommands extends AbstractBasics {

    private static final String RESOURCE_TYPE_INFRASTRUCTURE_CONFIGURATION = "Infrastructure Configuration";
    private static final String RESOURCE_TYPE_INFRASTRUCTURE_PLUGIN = "Infrastructure Plugin";

    private static final String SUPPORTED_PLUGIN_URI_START = "https://dl.bintray.com/foilen/maven/com/foilen/foilen-infra-plugins-";
    private static final String SUPPORTED_RESOURCE_URI_START = "https://dl.bintray.com/foilen/maven/com/foilen/foilen-infra-resource-";

    @Autowired
    private BintrayService bintrayService;
    @Autowired
    private DockerHubService dockerHubService;
    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;

    protected String getPluginNameFromUrl(String url) {
        return url.split("/")[7];
    }

    protected String getVersionFromUrl(String url) {
        return url.split("/")[8];
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

    @ShellMethod("Update the docker manager on all the machines")
    public void updateDockerManager( //
            @ShellOption(defaultValue = ShellOption.NULL) String version, //
            @ShellOption(defaultValue = ShellOption.NULL, help = "If you do not want to update all the hosts, you can specify one") String hostname //
    ) {

        // Use latest version if none specified
        if (version == null) {
            OnlineFileDetails dockerManagerVersionDetails = dockerHubService.getLatestVersionDockerHub("foilen/foilen-infra-docker-manager");
            version = dockerManagerVersionDetails.getVersion();
        }
        String finalVersion = version;

        // Check there is a certificate
        String certFile = ((ProfileHasCert) profileService.getTarget()).getSshCertificateFile();
        AssertTools.assertNotNull(certFile, "The target profile does not have the root certificate set");

        // Get the list of machines
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(new RequestResourceSearch().setResourceType("Machine"));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }
        List<String> hostnames;
        if (Strings.isNullOrEmpty(hostname)) {
            hostnames = resourceBuckets.getItems().stream() //
                    .map(it -> JsonTools.clone(it.getResourceDetails().getResource(), Machine.class)) //
                    .map(it -> it.getName()) //
                    .sorted().collect(Collectors.toList());
        } else {
            hostnames = Collections.singletonList(hostname);
        }
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();

        hostnames.forEach(h -> {

            futures.add(executorService.submit(() -> {
                logger.info("Updating docker manager on {} to version {}", h, finalVersion);
                JSchTools jSchTools = null;
                try {
                    jSchTools = new JSchTools().login(new SshLogin(h, "root").withPrivateKey(certFile).autoApproveHostKey());

                    // Send the script
                    String scriptPath = "/home/infra_docker_manager/startDockerManager.sh";
                    jSchTools.createAndUseSftpChannel(sftp -> {
                        String content = FreemarkerTools.processTemplate("/com/foilen/infra/cli/commands/updateDockerManager.sh.ftl", Collections.singletonMap("version", finalVersion));
                        sftp.put(new ByteArrayInputStream(content.getBytes()), scriptPath);
                        sftp.chmod(00700, scriptPath);
                    });

                    // Execute the script
                    ExecResult execResult = jSchTools.executeInLogger(scriptPath);
                    if (execResult.getExitCode() == 0) {
                        logger.info("Updating docker manager on {} was a success", h);
                        return "[OK] " + h;
                    } else {
                        logger.info("Updating docker manager on {} failed with exit code {}", h, execResult.getExitCode());
                        return "[FAILED] " + h;
                    }
                } catch (Exception e) {
                    logger.info("Updating docker manager on {} failed ", h, e);
                    return "[FAILED] " + h;
                } finally {
                    if (jSchTools != null) {
                        jSchTools.disconnect();
                    }
                }
            }));

        });

        // Wait for the end
        futures.stream() //
                .map(it -> {
                    try {
                        return it.get();
                    } catch (Exception e) {
                        logger.error("Problem while executing", e);
                        return null;
                    }
                }) //
                .filter(it -> it != null) //
                .sorted().forEach(it -> {
                    System.out.println(it);
                });
        executorService.shutdown();

    }

    @ShellMethod("Update the Login and UI services and the UI plugins to the latest versions.")
    public void updateInfra( //
            @ShellOption(defaultValue = "false", help = "Only show what would be done ; don't execute") boolean dryRun //
    ) {
        InfraApiService infraApiService = profileService.getTargetInfraApiService();

        // Update Plugins
        ResponseResourceBuckets pluginsBuckets = infraApiService.getInfraResourceApiService()
                .resourceFindAllWithDetails(new RequestResourceSearch().setResourceType(RESOURCE_TYPE_INFRASTRUCTURE_PLUGIN));
        if (!pluginsBuckets.isSuccess()) {
            throw new CliException("Could not get the list of plugins: " + JsonTools.compactPrint(pluginsBuckets));
        }
        List<InfraConfigPlugin> infraConfigPlugins = pluginsBuckets.getItems().stream() //
                .map(it -> JsonTools.clone(it.getResourceDetails().getResource(), InfraConfigPlugin.class)) //
                .collect(Collectors.toList());

        RequestChanges changes = new RequestChanges();

        infraConfigPlugins.forEach(it -> {
            String url = it.getUrl();
            if (url.startsWith(SUPPORTED_PLUGIN_URI_START) || url.startsWith(SUPPORTED_RESOURCE_URI_START)) {

                // Get latest version
                String pluginName = getPluginNameFromUrl(url);
                OnlineFileDetails onlineFileDetails = bintrayService.getLatestVersionBintray(pluginName);

                String currentVersion = getVersionFromUrl(url);
                if (StringTools.safeEquals(currentVersion, onlineFileDetails.getVersion())) {
                    System.out.println("[OK] " + pluginName + " : " + currentVersion);
                } else {
                    System.out.println("[UPDATE] " + pluginName + " : " + currentVersion + " -> " + onlineFileDetails.getVersion());
                    InfraConfigPlugin newInfraConfigPlugin = new InfraConfigPlugin();
                    newInfraConfigPlugin.setUrl(onlineFileDetails.getJarUrl());
                    changes.getResourcesToUpdate().add(new RequestResourceToUpdate( //
                            new ResourceDetails(RESOURCE_TYPE_INFRASTRUCTURE_PLUGIN, it), //
                            new ResourceDetails(RESOURCE_TYPE_INFRASTRUCTURE_PLUGIN, newInfraConfigPlugin) //
                    ));
                }

            }
        });

        // Update UI and login services
        OnlineFileDetails loginVersionDetails = dockerHubService.getLatestVersionDockerHub("foilen/foilen-login");
        OnlineFileDetails uiVersionDetails = dockerHubService.getLatestVersionDockerHub("foilen/foilen-infra-ui");

        ResponseResourceBucket infraConfigBucket = infraApiService.getInfraResourceApiService()
                .resourceFindOne(new RequestResourceSearch().setResourceType(RESOURCE_TYPE_INFRASTRUCTURE_CONFIGURATION));
        if (!infraConfigBucket.isSuccess() || infraConfigBucket.getItem() == null) {
            throw new CliException("Could not get the infra config: " + JsonTools.compactPrint(infraConfigBucket));
        }
        InfraConfig infraConfig = JsonTools.clone(infraConfigBucket.getItem().getResourceDetails().getResource(), InfraConfig.class);

        boolean updateInfraConfig = false;
        if (StringTools.safeEquals(loginVersionDetails.getVersion(), infraConfig.getLoginVersion())) {
            System.out.println("[OK] Login Service : " + infraConfig.getLoginVersion());
        } else {
            System.out.println("[UPDATE] Login Service : " + infraConfig.getLoginVersion() + " -> " + loginVersionDetails.getVersion());
            updateInfraConfig = true;
        }
        if (StringTools.safeEquals(uiVersionDetails.getVersion(), infraConfig.getUiVersion())) {
            System.out.println("[OK] UI Service : " + infraConfig.getUiVersion());
        } else {
            System.out.println("[UPDATE] UI Service : " + infraConfig.getUiVersion() + " -> " + uiVersionDetails.getVersion());
            updateInfraConfig = true;
        }

        if (updateInfraConfig) {

            InfraConfig newInfraConfig = JsonTools.clone(infraConfig);
            newInfraConfig.setLoginVersion(loginVersionDetails.getVersion());
            newInfraConfig.setUiVersion(uiVersionDetails.getVersion());

            changes.getResourcesToUpdate().add(new RequestResourceToUpdate( //
                    new ResourceDetails(RESOURCE_TYPE_INFRASTRUCTURE_CONFIGURATION, infraConfig), //
                    new ResourceDetails(RESOURCE_TYPE_INFRASTRUCTURE_CONFIGURATION, newInfraConfig) //
            ));
        }

        // Execute changes if any
        if (changes.getResourcesToUpdate().isEmpty()) {
            System.out.println("Nothing to update");
        } else {
            if (dryRun) {
                System.out.println("[DRY RUN] Not executing the update");
            } else {
                System.out.println("Execute update");
                ResponseResourceAppliedChanges formResult = infraApiService.getInfraResourceApiService().applyChanges(changes);
                exceptionService.displayResult(formResult, "Executing update");
            }
        }

    }

    @ShellMethod("Update the softwares on all the machines (apt dist-upgrade).")
    public void updateSoftwares() {

        // Check there is a certificate
        String certFile = ((ProfileHasCert) profileService.getTarget()).getSshCertificateFile();
        AssertTools.assertNotNull(certFile, "The target profile does not have the root certificate set");

        // Get the list of machines
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAllWithDetails(new RequestResourceSearch().setResourceType("Machine"));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }
        ExecutorService executorService = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();
        resourceBuckets.getItems().stream() //
                .map(it -> JsonTools.clone(it.getResourceDetails().getResource(), Machine.class)) //
                .map(it -> it.getName()) //
                .sorted() //
                .forEach(hostname -> {

                    futures.add(executorService.submit(() -> {
                        JSchTools jSchTools = null;
                        try {
                            logger.info("Updating softwares list on {}", hostname);
                            jSchTools = new JSchTools().login(new SshLogin(hostname, "root").withPrivateKey(certFile).autoApproveHostKey());
                            ExecResult execResult = jSchTools.executeInLogger("export TERM=dumb ; /usr/bin/apt-get update");
                            if (execResult.getExitCode() == 0) {
                                logger.info("Updating softwares list on {} was a success", hostname);
                            } else {
                                logger.info("Updating softwares list {} failed with exit code {}", hostname, execResult.getExitCode());
                                return "[FAILED] " + hostname;
                            }
                            logger.info("Updating softwares on {}", hostname);
                            execResult = jSchTools.executeInLogger("export TERM=dumb ; /usr/bin/apt-get -y dist-upgrade");
                            if (execResult.getExitCode() == 0) {
                                logger.info("Updating softwares on {} was a success", hostname);
                                return "[OK] " + hostname;
                            } else {
                                logger.info("Updating softwares on {} failed with exit code {}", hostname, execResult.getExitCode());
                                return "[FAILED] " + hostname;
                            }
                        } catch (Exception e) {
                            logger.info("Updating softwares on {} failed ", hostname, e);
                            return "[FAILED] " + hostname;
                        } finally {
                            if (jSchTools != null) {
                                jSchTools.disconnect();
                            }
                        }
                    }));

                });

        // Wait for the end
        futures.stream() //
                .map(it -> {
                    try {
                        return it.get();
                    } catch (Exception e) {
                        logger.error("Problem while executing", e);
                        return null;
                    }
                }) //
                .filter(it -> it != null) //
                .sorted().forEach(it -> {
                    System.out.println(it);
                });
        executorService.shutdown();

    }

}
