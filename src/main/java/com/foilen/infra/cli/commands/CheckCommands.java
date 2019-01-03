/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

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

import com.foilen.infra.api.request.RequestResourceSearch;
import com.foilen.infra.api.response.ResponseResourceBuckets;
import com.foilen.infra.api.service.InfraApiService;
import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.profile.ApiProfile;
import com.foilen.infra.cli.services.ProfileService;
import com.foilen.infra.resource.webcertificate.WebsiteCertificate;
import com.foilen.smalltools.tools.AbstractBasics;
import com.foilen.smalltools.tools.DateTools;
import com.foilen.smalltools.tools.JsonTools;

@ShellComponent
public class CheckCommands extends AbstractBasics {

    @Autowired
    private ProfileService profileService;

    @ShellMethod("List the Web Certificates that will expire this month (sooner first)")
    public void checkWebCertificatesNearExpiration() {

        // Get the list
        InfraApiService infraApiService = profileService.getTargetInfraApiService();
        ResponseResourceBuckets resourceBuckets = infraApiService.getInfraResourceApiService().resourceFindAll(new RequestResourceSearch().setResourceType(WebsiteCertificate.RESOURCE_TYPE));
        if (!resourceBuckets.isSuccess()) {
            throw new CliException(resourceBuckets.getError());
        }

        Date in1Month = DateTools.addDate(Calendar.MONTH, 1);
        resourceBuckets.getItems().stream() //
                .map(resourceBucket -> JsonTools.clone(resourceBucket.getResourceDetails().getResource(), WebsiteCertificate.class)) //
                .filter(websiteCertificate -> DateTools.isBefore(websiteCertificate.getEnd(), in1Month)) //
                .sorted((a, b) -> Long.compare(a.getEnd().getTime(), b.getEnd().getTime())) //
                .forEach(websiteCertificate -> {
                    System.out.println(DateTools.formatDateOnly(websiteCertificate.getEnd()) + " (" + websiteCertificate.getResourceEditorName() + ") " + websiteCertificate.getDomainNames());
                });

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

}
