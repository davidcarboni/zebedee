package com.github.onsdigital.zebedee.content.staticpage.base;

import com.github.onsdigital.zebedee.content.link.ContentReference;

import java.net.URI;

/**
 * Created by bren on 30/06/15.
 *
 * References to static pages, holds a short version of summary of referred page seperately.
 */
public class StaticPageSection extends ContentReference {

    private String summary;

    public StaticPageSection(URI uri) {
        super(uri);
    }

    public StaticPageSection(URI uri, Integer index) {
        super(uri, index);
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
