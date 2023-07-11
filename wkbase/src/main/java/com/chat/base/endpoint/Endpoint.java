package com.chat.base.endpoint;

/**
 * 2020-09-01 18:08
 */
public class Endpoint implements Comparable<Endpoint> {
    public int sort;
    public String sid;
    public String category;
    public EndpointHandler iHandler;

    public Endpoint(String sid, String category, int sort, EndpointHandler iHandler) {
        this.sid = sid;
        this.category = category;
        this.sort = sort;
        this.iHandler = iHandler;
    }

    @Override
    public int compareTo(Endpoint Endpoint) {
        return Endpoint.sort - this.sort;
    }
}
