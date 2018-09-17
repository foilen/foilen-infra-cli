/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2018 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.io.File;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraApiServiceImpl;
import com.foilen.infra.cli.model.AbstractProfile;
import com.foilen.infra.cli.model.ApiProfile;
import com.foilen.smalltools.JavaEnvironmentValues;
import com.foilen.smalltools.reflection.ReflectionTools;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.DirectoryTools;
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
    public void createProfilesDirectory() {

        directoryPath = SystemTools.getPropertyOrEnvironment("HOME", JavaEnvironmentValues.getHomeDirectory()) + File.separator + ".foilenInfra" + File.separator;

        if (!DirectoryTools.createPath(directoryPath)) {
            throw new RuntimeException("Could not create the profiles directory " + directoryPath);
        }

    }

    private InfraApiService getInfraApiService(AbstractProfile profile, String type) {
        if (profile == null) {
            throw new RuntimeException("No " + type + " profile set");
        }
        if (!(profile instanceof ApiProfile)) {
            throw new RuntimeException("The " + type + " profile is not of API type");
        }

        ApiProfile apiProfile = (ApiProfile) profile;
        return new InfraApiServiceImpl(apiProfile.getInfraBaseUrl(), apiProfile.getApiUser(), apiProfile.getApiKey());
    }

    public AbstractProfile getSource() {
        return source;
    }

    public InfraApiService getSourceInfraApiService() {
        return getInfraApiService(source, "source");
    }

    public AbstractProfile getTarget() {
        return target;
    }

    public InfraApiService getTargetInfraApiService() {
        return getInfraApiService(target, "target");
    }

    public List<String> list() {
        return DirectoryTools.listOnlyFileNames(directoryPath);
    }

    @SuppressWarnings("unchecked")
    public void setSource(String profileName) {
        Map<String, Object> map = JsonTools.readFromFile(directoryPath + profileName, Map.class);
        String type = (String) map.get("type");
        Class<?> classType = ReflectionTools.safelyGetClass(type);
        if (AbstractProfile.class.isAssignableFrom(classType)) {
            source = (AbstractProfile) JsonTools.readFromFile(directoryPath + profileName, classType);
            source.setProfileName(profileName);
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
        }
    }

}
