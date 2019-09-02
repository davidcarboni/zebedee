package com.github.onsdigital.zebedee.json.publishing;

import com.github.onsdigital.zebedee.json.CollectionBase;
import com.github.onsdigital.zebedee.json.CollectionType;
import com.github.onsdigital.zebedee.json.CollectionDataset;
import com.github.onsdigital.zebedee.json.CollectionDatasetVersion;

import java.util.Date;
import java.util.List;
import java.util.Set;


public class PublishedCollection extends CollectionBase {

    public Set<CollectionDataset> datasets;
    public Set<CollectionDatasetVersion> datasetVersions;

    public PublishedCollection(String id, String name, CollectionType type, Date publishDate) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.publishDate = publishDate;
    }

    public PublishedCollection() {}

    public Integer verifiedCount;
    public Integer verifyFailedCount;
    public Integer verifyInprogressCount;

    public Date publishStartDate; // The date the publish process was actually started
    public Date publishEndDate; // The date the publish process ended.

    /**
     * A list of {@link com.github.onsdigital.zebedee.json.publishing.Result} for
     * each attempt at publishing this collection.
     */
    public List<Result> publishResults;


    public void incrementVerified() {
        synchronized (this) {
            verifiedCount++;
        }
    }

    public void incrementVerifyFailed() {
        synchronized (this) {
            verifyFailedCount++;
        }
    }

    public void incrementVerifyInProgressCount() {
        synchronized (this) {
            verifyInprogressCount++;
        }
    }

    public void decrementVerifyInProgressCount() {
        synchronized (this) {
            verifyInprogressCount--;
        }
    }
}
