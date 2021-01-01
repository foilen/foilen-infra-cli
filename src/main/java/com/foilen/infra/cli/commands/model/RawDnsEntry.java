/*
    Foilen Infra CLI
    https://github.com/foilen/foilen-infra-cli
    Copyright (c) 2018-2021 Foilen (https://foilen.com)

    The MIT License
    http://opensource.org/licenses/MIT

 */
package com.foilen.infra.cli.commands.model;

import com.foilen.smalltools.tools.AbstractBasics;
import com.google.common.collect.ComparisonChain;

public class RawDnsEntry extends AbstractBasics implements Comparable<RawDnsEntry> {

    private String name;
    private String type;
    private String details;

    private Integer priority;
    private long ttl;

    public RawDnsEntry() {
    }

    @Override
    public int compareTo(RawDnsEntry o) {
        ComparisonChain cc = ComparisonChain.start();
        cc = cc.compare(name, o.name);
        cc = cc.compare(type, o.type);
        cc = cc.compare(details, o.details);
        cc = cc.compare(priority, o.priority);
        cc = cc.compare(ttl, o.ttl);
        return cc.result();
    }

    public String getDetails() {
        return details;
    }

    public String getName() {
        return name;
    }

    public Integer getPriority() {
        return priority;
    }

    public long getTtl() {
        return ttl;
    }

    public String getType() {
        return type;
    }

    public RawDnsEntry setDetails(String details) {
        this.details = details;
        return this;
    }

    public RawDnsEntry setName(String name) {
        this.name = name;
        return this;
    }

    public RawDnsEntry setPriority(Integer priority) {
        this.priority = priority;
        return this;
    }

    public RawDnsEntry setTtl(long ttl) {
        this.ttl = ttl;
        return this;
    }

    public RawDnsEntry setType(String type) {
        this.type = type;
        return this;
    }

}
