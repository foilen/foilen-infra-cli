/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraApiServiceImpl;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.profile.AbstractProfile;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.smalltools.JavaEnvironmentValues;
import com.foilen.smalltools.reflection.ReflectionTools;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.DirectoryTools;
import com.foilen.smalltools.tools.FileTools;
import com.foilen.smalltools.tools.JsonTools;
import com.foilen.smalltools.tools.SystemTools;

@Component
public class ProfileService extends AbstractBasics {

    private String directoryPath;

    private AbstractProfile source;
    private AbstractProfile target;

    public void add(String profileName, AbstractProfile profile) {
        JsonTools.writeToFile(directoryPath + profileName, profile);
    }

    @PostConstruct
    public void createProfilesDirectoryAndLoadLast() {

        directoryPath = SystemTools.getPropertyOrEnvironment("HOME", JavaEnvironmentValues.getHomeDirectory()) + File.separator + ".foilenInfra" + File.separator;

        if (!DirectoryTools.createPath(directoryPath)) {
            throw new CliException("Could not create the profiles directory " + directoryPath);
        }

        // Load last used profiles
        loadUsedProfiles();

    }

    public InfraApiService getInfraApiService(AbstractProfile profile, String type) {
        if (profile == null) {
            throw new CliException("No " + type + " profile set");
        }
        if (!(profile instanceof ApiProfile)) {
            throw new CliException("The " + type + " profile is not of API type");
        }

        ApiProfile apiProfile = (ApiProfile) profile;
        return new InfraApiServiceImpl(apiProfile.getInfraBaseUrl(), apiProfile.getApiUser(), apiProfile.getApiKey());
    }

    public AbstractProfile getSource() {
        return source;
    }

    public <T> T getSourceAs(Class<T> type) {
        try {
            return getSourceAsOrFail(type);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getSourceAsOrFail(Class<T> type) {
        if (source == null) {
            throw new CliException("No source profile set");
        }
        if (!type.isAssignableFrom(source.getClass())) {
            throw new CliException("The source profile is not of " + type + " type");
        }

        return (T) source;

    }

    public InfraApiService getSourceInfraApiService() {
        return getInfraApiService(source, "source");
    }

    public AbstractProfile getTarget() {
        return target;
    }

    public <T> T getTargetAs(Class<T> type) {
        try {
            return getTargetAsOrFail(type);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getTargetAsOrFail(Class<T> type) {
        if (target == null) {
            throw new CliException("No target profile set");
        }
        if (!type.isAssignableFrom(target.getClass())) {
            throw new CliException("The target profile is not of " + type + " type");
        }

        return (T) target;

    }

    public InfraApiService getTargetInfraApiService() {
        return getInfraApiService(target, "target");
    }

    public List<String> list() {
        return DirectoryTools.listOnlyFileNames(directoryPath);
    }

    @SuppressWarnings("unchecked")
    private void loadUsedProfiles() {
        Map<String, String> usedProfiles;
        try {
            if (!FileTools.exists(directoryPath + ".lastUsedProfiles.json")) {
                return;
            }
            usedProfiles = JsonTools.readFromFile(directoryPath + ".lastUsedProfiles.json", Map.class);
        } catch (Exception e) {
            System.out.println("Could not load the last used profiles files");
            return;
        }

        String sourceName = usedProfiles.get("source");
        if (sourceName != null) {
            try {
                setSource(sourceName);
            } catch (Exception e) {
                System.out.println("Source: Could not load profile " + sourceName + " . Will simply not set");
            }
        }
        String targetName = usedProfiles.get("target");
        if (targetName != null) {
            try {
                setTarget(targetName);
            } catch (Exception e) {
                System.out.println("Target: Could not load profile " + targetName + " . Will simply not set");
            }
        }

    }

    private void saveUsedProfiles() {
        Map<String, String> usedProfiles = new HashMap<>();
        if (source != null) {
            usedProfiles.put("source", source.getProfileName());
        }
        if (target != null) {
            usedProfiles.put("target", target.getProfileName());
        }
        JsonTools.writeToFile(directoryPath + ".lastUsedProfiles.json", usedProfiles);
    }

    @SuppressWarnings("unchecked")
    public void setSource(String profileName) {
        Map<String, Object> map = JsonTools.readFromFile(directoryPath + profileName, Map.class);
        String type = (String) map.get("type");
        Class<?> classType = ReflectionTools.safelyGetClass(type);
        if (AbstractProfile.class.isAssignableFrom(classType)) {
            source = (AbstractProfile) JsonTools.readFromFile(directoryPath + profileName, classType);
            source.setProfileName(profileName);
            saveUsedProfiles();
        }
    }

    @SuppressWarnings("unchecked")
    public void setTarget(String profileName) {
        Map<String, Object> map = JsonTools.readFromFile(directoryPath + profileName, Map.class);
        String type = (String) map.get("type");
        Class<?> classType = ReflectionTools.safelyGetClass(type);
        if (AbstractProfile.class.isAssignableFrom(classType)) {
            target = (AbstractProfile) JsonTools.readFromFile(directoryPath + profileName, classType);
            target.setProfileName(profileName);
            saveUsedProfiles();
        }
    }

}
