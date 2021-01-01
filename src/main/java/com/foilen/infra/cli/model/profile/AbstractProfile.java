/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.model.profile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.foilen.smalltools.tools.AbstractBasics;

@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AbstractProfile extends AbstractBasics {

    @JsonIgnore
    private String profileName;

    public String getProfileName() {
        return profileName;
    }

    public String getType() {
        return getClass().getName();
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

}
