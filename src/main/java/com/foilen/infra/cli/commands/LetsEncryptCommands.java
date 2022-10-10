/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands;

import java.util.Calendar;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.Availability;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellMethodAvailability;

import com.foilen.infra.api.model.resource.ResourceDetails;
import com.foilen.infra.api.request.RequestChanges;
import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.request.RequestResourceToUpdate;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.api.service.InfraResourceApiService;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.ExceptionService;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.resource.letsencrypt.plugin.LetsEncryptWebsiteCertificateEditor;
import com.foilen.infra.resource.webcertificate.WebsiteCertificate;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.DateTools;
import com.foilen.smalltools.tools.JsonTools;

@ShellComponent
public class LetsEncryptCommands extends AbstractBasics {

    @Autowired
    private ExceptionService exceptionService;
    @Autowired
    private ProfileService profileService;

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

    @ShellMethod("Force refresh all certificates")
    public void letsEncryptForceRefresh() {

        // Get the list of Let's Encrypt certificates
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        RequestResourceSearch requestResourceSearch = new RequestResourceSearch().setResourceType(WebsiteCertificate.RESOURCE_TYPE);
        ResponseResourceBuckets resourceBuckets = infraResourceApiService.resourceFindAllWithDetails(requestResourceSearch);
        exceptionService.displayResultAndThrow(resourceBuckets, "Find the WebsiteCertificate resources");

        RequestChanges changes = new RequestChanges();
        resourceBuckets.getItems().stream() //
                .map(resourceBucket -> JsonTools.clone(resourceBucket.getResourceDetails().getResource(), WebsiteCertificate.class)) //
                .filter(websiteCertificate -> LetsEncryptWebsiteCertificateEditor.EDITOR_NAME.equals(websiteCertificate.getResourceEditorName())) //
                .forEach(websiteCertificate -> {

                    System.out.println(websiteCertificate.getDomainNames());

                    // Change the end date
                    websiteCertificate.setEnd(DateTools.addDate(Calendar.DAY_OF_YEAR, 1));
                    ResourceDetails resourceDetails = new ResourceDetails(WebsiteCertificate.RESOURCE_TYPE, websiteCertificate);
                    changes.getResourcesToUpdate().add(new RequestResourceToUpdate(resourceDetails, resourceDetails));

                    // Update in batch of 10
                    if (changes.getResourcesToUpdate().size() >= 10) {
                        ResponseResourceAppliedChanges resourceAppliedChanges = infraResourceApiService.applyChanges(changes);
                        exceptionService.displayResult(resourceAppliedChanges, "Applying update");
                        changes.getResourcesToUpdate().clear();
                    }
                });

        // If some pending
        if (!changes.getResourcesToUpdate().isEmpty()) {
            ResponseResourceAppliedChanges resourceAppliedChanges = infraResourceApiService.applyChanges(changes);
            exceptionService.displayResult(resourceAppliedChanges, "Applying update");
        }

    }

    @ShellMethod("Change the end date to be randomly spread out randomly up to 1.5 months before the end date")
    public void letsEncryptSpreadOut() {

        // Get the list of Let's Encrypt certificates
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        InfraResourceApiService infraResourceApiService = infraApiService.getInfraResourceApiService();
        RequestResourceSearch requestResourceSearch = new RequestResourceSearch().setResourceType(WebsiteCertificate.RESOURCE_TYPE);
        ResponseResourceBuckets resourceBuckets = infraResourceApiService.resourceFindAllWithDetails(requestResourceSearch);
        exceptionService.displayResultAndThrow(resourceBuckets, "Find the WebsiteCertificate resources");

        RequestChanges changes = new RequestChanges();
        resourceBuckets.getItems().stream() //
                .map(resourceBucket -> JsonTools.clone(resourceBucket.getResourceDetails().getResource(), WebsiteCertificate.class)) //
                .filter(websiteCertificate -> LetsEncryptWebsiteCertificateEditor.EDITOR_NAME.equals(websiteCertificate.getResourceEditorName())) //
                .forEach(websiteCertificate -> {

                    String initialEndDate = DateTools.formatDateOnly(websiteCertificate.getEnd());
                    Date newEndDate = DateTools.addDate(websiteCertificate.getEnd(), Calendar.DAY_OF_YEAR, (int) (Math.random() * -45));
                    System.out.println(websiteCertificate.getDomainNames() + " " + initialEndDate + " -> " + DateTools.formatDateOnly(newEndDate));

                    // Change the end date
                    websiteCertificate.setEnd(newEndDate);
                    ResourceDetails resourceDetails = new ResourceDetails(WebsiteCertificate.RESOURCE_TYPE, websiteCertificate);
                    changes.getResourcesToUpdate().add(new RequestResourceToUpdate(resourceDetails, resourceDetails));

                    // Update in batch of 10
                    if (changes.getResourcesToUpdate().size() >= 10) {
                        ResponseResourceAppliedChanges resourceAppliedChanges = infraResourceApiService.applyChanges(changes);
                        exceptionService.displayResult(resourceAppliedChanges, "Applying update");
                        changes.getResourcesToUpdate().clear();
                    }
                });

        // If some pending
        if (!changes.getResourcesToUpdate().isEmpty()) {
            ResponseResourceAppliedChanges resourceAppliedChanges = infraResourceApiService.applyChanges(changes);
            exceptionService.displayResult(resourceAppliedChanges, "Applying update");
        }

    }

}
