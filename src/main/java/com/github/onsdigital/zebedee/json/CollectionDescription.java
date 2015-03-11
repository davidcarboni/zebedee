package com.github.onsdigital.zebedee.json;

import com.github.onsdigital.zebedee.Collection;

import java.util.Date;
import java.util.List;

/**
 * This cd ..
 *
 * @author david
 */
public class CollectionDescription {
    /**
     * The readable name of this {@link Collection}.
     */
    public String name;
    /**
     * The date-time when this {@link Collection} should be published (if it has
     * a publish date).
     */
    public Date publishDate;

    public List<String> inProgressUris;
    public List<String> approvedUris;

    /**
     * Default constuructor for serialisation.
     */
    public CollectionDescription() {
        // No action.
    }

    /**
     * Convenience constructor for instantiating with a name.
     *
     * @param name The value for the name.
     */
    public CollectionDescription(String name) {
        this.name = name;
    }
}
