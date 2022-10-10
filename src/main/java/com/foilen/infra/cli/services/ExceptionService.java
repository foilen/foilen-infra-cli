/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.foilen.infra.api.model.audit.AuditItemSmallWithPagination;
import com.foilen.infra.api.model.resource.ResourceDetailsSmall;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.cli.CliException;
import com.foilen.smalltools.restapi.model.AbstractApiBaseWithError;
import com.foilen.smalltools.restapi.model.ApiError;
import com.foilen.smalltools.restapi.model.ApiPagination;
import com.foilen.smalltools.restapi.model.FormResult;
import com.foilen.smalltools.tools.CollectionsTools;

@Component
public class ExceptionService {

    @Autowired
    private DisplayService displayService;

    public void displayResult(AbstractApiBaseWithError formResult, String context) {
        if (formResult.isSuccess()) {
            displayService.display("[SUCCESS] " + context);
        } else {
            displayResultError(formResult, context);
        }
    }

    public void displayResult(FormResult formResult, String context) {
        if (formResult.isSuccess()) {
            displayService.display("[SUCCESS] " + context);
        } else {
            displayResultError(formResult, context);
        }
    }

    public void displayResult(ResponseResourceAppliedChanges formResult, String context) {
        if (formResult.isSuccess()) {
            displayService.display("[SUCCESS] " + context + " (" + formResult.getTxId() + ")");

            AuditItemSmallWithPagination auditItems = formResult.getAuditItems();
            ApiPagination auditItemsPagination = auditItems.getPagination();
            displayService.display("\tApplied modifications (" + auditItems.getItems().size() + "/" + auditItemsPagination.getTotalItems() + ")");
            auditItems.getItems().forEach(it -> {
                StringBuilder line = new StringBuilder();
                line.append("\t\t").append(it.getAction()).append(" ").append(it.getType()).append(": ");
                switch (it.getType()) {
                case LINK:
                    line.append(getResourceDetailsText(it.getResourceFirst()));
                    line.append(" -> ");
                    line.append(it.getLinkType());
                    line.append(" -> ");
                    line.append(getResourceDetailsText(it.getResourceSecond()));
                    break;
                case RESOURCE:
                    line.append(getResourceDetailsText(it.getResourceFirst()));
                    if (it.getResourceSecond() != null) {
                        line.append(" -> ");
                        line.append(getResourceDetailsText(it.getResourceSecond()));
                    }
                    break;
                case TAG:
                    line.append(getResourceDetailsText(it.getResourceFirst()));
                    line.append(" -> ");
                    line.append(it.getTagName());
                    break;
                case DOCUMENT:
                    break;
                default:
                    break;
                }
                displayService.display(line.toString());
            });

        } else {
            displayResultError(formResult, context);
        }
    }

    public void displayResultAndThrow(AbstractApiBaseWithError formResult, String context) {
        displayResult(formResult, context);
        if (!formResult.isSuccess()) {
            displayResultError(formResult, context);
            throw new CliException();
        }
    }

    public void displayResultAndThrow(FormResult formResult, String context) {
        displayResult(formResult, context);
        if (!formResult.isSuccess()) {
            throw new CliException();
        }
    }

    public void displayResultAndThrow(ResponseResourceAppliedChanges formResult, String context) {
        displayResult(formResult, context);
        if (!formResult.isSuccess()) {
            throw new CliException();
        }
    }

    private void displayResultError(AbstractApiBaseWithError formResult, String context) {
        displayService.display("[ERROR] " + context);

        if (formResult == null) {
            displayService.display("\tGot a null response");
            return;
        }
        ApiError error = formResult.getError();
        if (error != null) {
            displayService.display("\t" + error.getTimestamp() + " " + error.getUniqueId() + " : " + error.getMessage());
        }

    }

    private void displayResultError(FormResult formResult, String context) {
        displayService.display("[ERROR] " + context);

        ApiError error = formResult.getError();
        if (error != null) {
            displayService.display("\t" + error.getTimestamp() + " " + error.getUniqueId() + " : " + error.getMessage());
        }

        if (!CollectionsTools.isNullOrEmpty(formResult.getGlobalErrors())) {
            formResult.getGlobalErrors().forEach(it -> displayService.display("\t[GLOBAL] " + it));
        }

        if (!formResult.getValidationErrorsByField().isEmpty()) {
            formResult.getValidationErrorsByField().entrySet().stream() //
                    .sorted((a, b) -> a.getKey().compareTo(b.getKey())) //
                    .forEach(entry -> {
                        entry.getValue().stream().sorted().forEach(it -> {
                            displayService.display("\t[" + entry.getKey() + "] " + it);
                        });
                    });
        }
    }

    private String getResourceDetailsText(ResourceDetailsSmall resource) {
        return resource.getResourceType() + " / " + resource.getResourceName();
    }

    public void throwOnFailure(AbstractApiBaseWithError formResult, String context) {
        if (formResult == null || !formResult.isSuccess()) {
            displayResultError(formResult, context);
            throw new CliException();
        }
    }

}
