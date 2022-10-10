/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.util.Collections;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.foilen.infra.cli.model.DockerHubTag;
import com.foilen.infra.cli.model.DockerHubTagsResponse;
import com.foilen.infra.cli.model.OnlineFileDetails;
import com.foilen.smalltools.tools.AbstractBasics;

@Component
public class DockerHubService extends AbstractBasics {

    private static RestTemplate restTemplate = new RestTemplate();

    public OnlineFileDetails getLatestVersionDockerHub(String imageName) {
        DockerHubTagsResponse tags = restTemplate.getForObject("https://hub.docker.com/v2/repositories/{imageName}/tags/", DockerHubTagsResponse.class,
                Collections.singletonMap("imageName", imageName));

        Optional<DockerHubTag> tag = tags.getResults().stream() //
                .filter(it -> !"latest".equals(it.getName())) //
                .findFirst();

        if (tag.isPresent()) {
            return new OnlineFileDetails() //
                    .setVersion(tag.get().getName());
        }

        return null;
    }

}
