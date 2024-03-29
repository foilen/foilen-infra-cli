/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2022 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.services;

import java.io.IOException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import com.foilen.infra.cli.CliException;
import com.foilen.infra.cli.model.OnlineFileDetails;
import com.foilen.smalltools.tools.AbstractBasics;
import com.google.common.collect.ComparisonChain;

@Component
public class MavenCentralService extends AbstractBasics {

    public OnlineFileDetails getLatestVersion(String packageName) {
        try {
            // Get the version
            Document doc = Jsoup.connect("https://repo1.maven.org/maven2/com/foilen/" + packageName + "/").get();
            Elements links = doc.select("a");
            String version = links.stream() //
                    .map(it -> it.text().replace("/", "")) //
                    .map(it -> it.split("\\.")) //
                    .filter(it -> it.length == 3) //
                    .map(it -> {
                        try {
                            return new int[] { Integer.valueOf(it[0]), Integer.valueOf(it[1]), Integer.valueOf(it[2]) };
                        } catch (NumberFormatException e) {
                            return null;
                        }
                    }) //
                    .filter(it -> it != null) //
                    .sorted((a, b) -> ComparisonChain.start() //
                            .compare(b[0], a[0]) //
                            .compare(b[1], a[1]) //
                            .compare(b[2], a[2]) //
                            .result()) //
                    .map(it -> "" + it[0] + "." + it[1] + "." + it[2]) //
                    .findFirst().get(); //

            // Get the jar
            String jarUrl = "https://repo1.maven.org/maven2/com/foilen/" + packageName + "/" + version + "/" + packageName + "-" + version + ".jar";

            OnlineFileDetails onlineFileDetails = new OnlineFileDetails();
            onlineFileDetails.setJarUrl(jarUrl);
            onlineFileDetails.setVersion(version);
            return onlineFileDetails;
        } catch (IOException e) {
            throw new CliException("Problem getting the folder", e);
        }

    }

}
