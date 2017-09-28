package com.github.onsdigital.zebedee.json;

import com.github.onsdigital.zebedee.json.publishing.Result;
import com.github.onsdigital.zebedee.model.CollectionOwner;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.github.onsdigital.zebedee.model.CollectionOwner.PUBLISHING_SUPPORT;

/**
 * This cd ..
 *
 * @author david
 */
public class CollectionDescription extends CollectionBase {

    public List<String> inProgressUris;
    public List<String> completeUris;
    public List<String> reviewedUris;
    public ApprovalStatus approvalStatus = ApprovalStatus.NOT_STARTED;
    public boolean publishComplete;
    public Map<String, String> publishTransactionIds;
    public Date publishStartDate; // The date the publish process was actually started
    public Date publishEndDate; // The date the publish process ended.
    public boolean isEncrypted;
    private List<PendingDelete> pendingDeletes;
    private Set<CollectionInstance> instances;

    // Default to PUBLISHING_SUPPORT_TEAM
    public CollectionOwner collectionOwner = PUBLISHING_SUPPORT;

    public List<String> timeseriesImportFiles = new ArrayList<>();

    /**
     * events related to this collection
     */
    public Events events;

    /**
     * A List of {@link Event} for each uri in the collection.
     */
    public Map<String, Events> eventsByUri;

    /**
     * A list of {@link com.github.onsdigital.zebedee.json.publishing.Result} for
     * each attempt at publishing this collection.
     */
    public List<Result> publishResults;

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

    /**
     *
     */
    public CollectionDescription(String name, CollectionOwner collectionOwner) {
        this.name = name;
        this.collectionOwner = collectionOwner != null ? collectionOwner : PUBLISHING_SUPPORT;
    }

    /**
     * Convenience constructor for instantiating with a name
     * and publish date.
     *
     * @param name
     * @param publishDate
     */
    public CollectionDescription(String name, Date publishDate) {
        this.publishDate = publishDate;
        this.name = name;
    }

    /**
     * Convenience constructor for instantiating with a name
     * and publish date.
     *
     * @param name
     * @param publishDate
     */
    public CollectionDescription(String name, Date publishDate, CollectionOwner collectionOwner) {
        this.publishDate = publishDate;
        this.name = name;
        this.collectionOwner = collectionOwner != null ? collectionOwner : PUBLISHING_SUPPORT;
    }


    public void setCollectionOwner(CollectionOwner audience) {
        this.collectionOwner = audience != null ? audience : PUBLISHING_SUPPORT;
    }


    /**
     * Add an event to this collection description.
     *
     * @param event
     */
    public void addEvent(Event event) {

        if (events == null)
            events = new Events();

        events.add(event);
    }

    /**
     * Add a {@link Result} to this
     * {@link CollectionDescription}.
     *
     * @param result
     */
    public void AddPublishResult(Result result) {
        if (publishResults == null) {
            publishResults = new ArrayList<>();
        }

        publishResults.add(result);
    }

    public CollectionOwner getCollectionOwner() {
        return collectionOwner == null ? PUBLISHING_SUPPORT : collectionOwner;
    }

    public List<PendingDelete> getPendingDeletes() {
        if (this.pendingDeletes == null) {
            this.pendingDeletes = new ArrayList<>();
        }
        return pendingDeletes;
    }

    public void cancelPendingDelete(String uri) {
        setPendingDeletes(getPendingDeletes()
                .stream()
                .filter(pd -> !pd.getRoot().contentPath.equals(uri))
                .collect(Collectors.toList())
        );
    }

    public void setPendingDeletes(List<PendingDelete> pendingDeletes) {
        this.pendingDeletes = pendingDeletes;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Set<CollectionInstance> getInstances() {

        if (this.instances == null) {
            this.instances = new HashSet<>();
        }

        return instances;
    }

    public Optional<CollectionInstance> getInstance(String instanceID) {

        if (this.instances == null)
            return Optional.empty();

        return this.instances.stream()
                .filter(i -> i.getId().equals(instanceID)).findFirst();
    }

    public void addInstance(CollectionInstance instance) {
        if (this.instances == null)
            this.instances = new HashSet<>();

        this.instances.add(instance);
    }

    public void deleteInstance(CollectionInstance instance) {
        if (this.instances == null)
            return;

        this.instances.remove(instance);
    }
}
