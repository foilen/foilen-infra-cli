/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import org.springframework.stereotype.Component;

import com.foilen.infra.api.model.AuditItemSmallWithPagination;
import com.foilen.infra.api.model.ResourceDetailsSmall;
import com.foilen.infra.api.response.ResponseResourceAppliedChanges;
import com.foilen.infra.cli.CliException;
import com.foilen.smalltools.restapi.model.AbstractApiBaseWithError;
import com.foilen.smalltools.restapi.model.ApiError;
import com.foilen.smalltools.restapi.model.ApiPagination;
import com.foilen.smalltools.restapi.model.FormResult;
import com.foilen.smalltools.tools.CollectionsTools;

@Component
public class ExceptionService {

    private void display(ResourceDetailsSmall resource) {
        System.out.print(resource.getResourceType());
        System.out.print("/");
        System.out.print(resource.getResourceName());
    }

    public void displayResult(AbstractApiBaseWithError formResult, String context) {
        if (formResult.isSuccess()) {
            System.out.println("[SUCCESS] " + context);
        } else {
            displayResultError(formResult, context);
        }
    }

    public void displayResult(FormResult formResult, String context) {
        if (formResult.isSuccess()) {
            System.out.println("[SUCCESS] " + context);
        } else {
            displayResultError(formResult, context);
        }
    }

    public void displayResult(ResponseResourceAppliedChanges formResult, String context) {
        if (formResult.isSuccess()) {
            System.out.println("[SUCCESS] " + context + " (" + formResult.getTxId() + ")");

            AuditItemSmallWithPagination auditItems = formResult.getAuditItems();
            ApiPagination auditItemsPagination = auditItems.getPagination();
            System.out.println("\tApplied modifications (" + auditItems.getItems().size() + "/" + auditItemsPagination.getTotalItems() + ")");
            auditItems.getItems().forEach(it -> {
                System.out.print("\t\t" + it.getAction() + " " + it.getType());
                System.out.print(" ");
                switch (it.getType()) {
                case LINK:
                    display(it.getResourceFirst());
                    System.out.print(" -> ");
                    System.out.print(it.getLinkType());
                    System.out.print(" -> ");
                    display(it.getResourceSecond());
                    break;
                case RESOURCE:
                    display(it.getResourceFirst());
                    if (it.getResourceSecond() != null) {
                        System.out.print(" -> ");
                        display(it.getResourceSecond());
                    }
                    break;
                case TAG:
                    display(it.getResourceFirst());
                    System.out.print(" -> ");
                    System.out.print(it.getTagName());
                    break;
                }
                System.out.println();
            });

        } else {
            displayResultError(formResult, context);
        }
    }

    public void displayResultAndThrow(AbstractApiBaseWithError formResult, String context) {
        displayResult(formResult, context);
        if (!formResult.isSuccess()) {
            throw new CliException();
        }
    }

    public void displayResultAndThrow(FormResult formResult, String context) {
        displayResult(formResult, context);
        if (!formResult.isSuccess()) {
            throw new CliException();
        }
    }

    private void displayResultError(AbstractApiBaseWithError formResult, String context) {
        System.out.println("[ERROR] " + context);

        ApiError error = formResult.getError();
        if (error != null) {
            System.out.println("\t" + error.getTimestamp() + " " + error.getUniqueId() + " : " + error.getMessage());
        }

    }

    private void displayResultError(FormResult formResult, String context) {
        System.out.println("[ERROR] " + context);

        ApiError error = formResult.getError();
        if (error != null) {
            System.out.println("\t" + error.getTimestamp() + " " + error.getUniqueId() + " : " + error.getMessage());
        }

        if (!CollectionsTools.isNullOrEmpty(formResult.getGlobalErrors())) {
            formResult.getGlobalErrors().forEach(it -> System.out.println("\t[GLOBAL] " + it));
        }

        if (!formResult.getValidationErrorsByField().isEmpty()) {
            formResult.getValidationErrorsByField().entrySet().stream() //
                    .sorted((a, b) -> a.getKey().compareTo(b.getKey())) //
                    .forEach(entry -> {
                        entry.getValue().stream().sorted().forEach(it -> {
                            System.out.println("\t[" + entry.getKey() + "] " + it);
                        });
                    });
        }
    }

}
