package com.googol.web.common;

import java.io.Serializable;

public class UrlMetadata implements Serializable {
    private final String title;
    private final String citation;

    public UrlMetadata(String title, String citation) {
        this.title = title;
        this.citation = citation;
    }
    public String getTitle() { return title; }
    public String getCitation() { return citation; }
}