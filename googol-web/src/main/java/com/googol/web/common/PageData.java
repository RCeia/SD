package com.googol.web.common;

import java.io.Serializable;
import java.util.List;

public class PageData implements Serializable {
    private static final long serialVersionUID = 1L;

    private String url;
    private String title;
    private List<String> words;
    private List<String> outgoingLinks;

    public PageData(String url, String title, List<String> words, List<String> outgoingLinks) {
        this.url = url;
        this.title = title;
        this.words = words;
        this.outgoingLinks = outgoingLinks;
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getWords() {
        return words;
    }

    public List<String> getOutgoingLinks() {
        return outgoingLinks;
    }
}
