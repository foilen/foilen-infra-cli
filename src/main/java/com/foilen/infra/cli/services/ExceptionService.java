/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2019 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import org.springframework.stereotype.Component;

import com.foilen.infra.cli.CliException;
import com.foilen.smalltools.restapi.model.ApiError;
import com.foilen.smalltools.restapi.model.FormResult;
import com.foilen.smalltools.tools.CollectionsTools;

@Component
public class ExceptionService {

    public void displayResult(FormResult formResult, String context) {
        if (formResult.isSuccess()) {
            System.out.println("[SUCCESS] " + context);
        } else {
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

    public void displayResultAndThrow(FormResult formResult, String context) {
        displayResult(formResult, context);
        if (!formResult.isSuccess()) {
            throw new CliException();
        }
    }

}
