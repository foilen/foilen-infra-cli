/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2018 Foilen (http://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import org.jline.utils.AttributedString;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.stereotype.Component;

import com.foilen.smalltools.tools.AbstractBasics;

@Component
public class InfraPromptProvider extends AbstractBasics implements PromptProvider {

    @Autowired
    private ProfileService profileService;

    @Override
    public AttributedString getPrompt() {
        String prompt = "";
        if (profileService.getTarget() != null) {
            prompt += "->" + profileService.getTarget().getProfileName();
        }

        if (prompt.isEmpty()) {
            prompt = "NO PROFILE";
        }

        prompt += " > ";
        return new AttributedString(prompt);
    }

}
