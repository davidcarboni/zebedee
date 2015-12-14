package com.github.onsdigital.zebedee.model;

import com.github.davidcarboni.cryptolite.Random;
import com.github.davidcarboni.restolino.json.Serialiser;
import com.github.onsdigital.zebedee.Builder;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.content.page.base.PageDescription;
import com.github.onsdigital.zebedee.content.page.base.PageType;
import com.github.onsdigital.zebedee.content.page.release.Release;
import com.github.onsdigital.zebedee.content.util.ContentUtil;
import com.github.onsdigital.zebedee.exceptions.*;
import com.github.onsdigital.zebedee.json.*;
import com.github.onsdigital.zebedee.model.content.item.ContentItemVersion;
import com.github.onsdigital.zebedee.model.publishing.CollectionScheduler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class CollectionTest {

    Zebedee zebedee;
    Collection collection;
    Builder builder;
    Session publisherSession;
    String publisher1Email;

    @Before
    public void setUp() throws Exception {
        builder = new Builder(this.getClass());
        zebedee = new Zebedee(builder.zebedee, false);
        collection = new Collection(builder.collections.get(1), zebedee);

        zebedee.openSession(builder.administratorCredentials);
        publisherSession = zebedee.openSession(builder.publisher1Credentials);
        publisher1Email = publisherSession.email;
    }

    @After
    public void tearDown() throws Exception {
        builder.delete();
    }

    @Test
    public void shouldCreateCollection() throws Exception {

        // Given
        // The content doesn't exist at any level:
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        String filename = PathUtils.toFilename(name);

        // When
        Collection.create(collectionDescription, zebedee, publisherSession);

        // Then
        Path rootPath = builder.zebedee.resolve(Zebedee.COLLECTIONS);
        Path releasePath = rootPath.resolve(filename);
        Path jsonPath = rootPath.resolve(filename + ".json");

        assertTrue(StringUtils.isNotEmpty(collectionDescription.id));

        assertTrue(Files.exists(releasePath));
        assertTrue(Files.exists(jsonPath));
        assertTrue(Files.exists(releasePath.resolve(Collection.REVIEWED)));
        assertTrue(Files.exists(releasePath.resolve(Collection.COMPLETE)));
        assertTrue(Files.exists(releasePath.resolve(Collection.IN_PROGRESS)));

        CollectionDescription createdCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(jsonPath)) {
            createdCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(createdCollectionDescription);
        assertEquals(collectionDescription.name, createdCollectionDescription.name);
        assertEquals(collectionDescription.publishDate, createdCollectionDescription.publishDate);
    }

    @Test
    public void shouldRenameCollection() throws Exception {

        // Given an existing collection
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.type = CollectionType.manual;
        collectionDescription.publishDate = new Date();
        String newName = "Economy Release";
        String filename = PathUtils.toFilename(newName);

        // When the rename function is called.
        Collection.create(collectionDescription, zebedee, publisherSession);
        Collection.rename(collectionDescription, newName, zebedee);

        // Then the collection is renamed.
        Path rootPath = builder.zebedee.resolve(Zebedee.COLLECTIONS);
        Path releasePath = rootPath.resolve(filename);
        Path jsonPath = rootPath.resolve(filename + ".json");

        Path oldJsonPath = rootPath.resolve(PathUtils.toFilename(name) + ".json");

        assertTrue(Files.exists(releasePath));
        assertTrue(Files.exists(jsonPath));
        assertTrue(!Files.exists(oldJsonPath));
        assertTrue(Files.exists(releasePath.resolve(Collection.REVIEWED)));
        assertTrue(Files.exists(releasePath.resolve(Collection.COMPLETE)));
        assertTrue(Files.exists(releasePath.resolve(Collection.IN_PROGRESS)));

        CollectionDescription renamedCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(jsonPath)) {
            renamedCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(renamedCollectionDescription);
        assertEquals(collectionDescription.id, renamedCollectionDescription.id);
        assertEquals(newName, renamedCollectionDescription.name);
        assertEquals(collectionDescription.publishDate, renamedCollectionDescription.publishDate);
        assertEquals(collectionDescription.type, renamedCollectionDescription.type);
    }

    @Test
    public void shouldUpdateCollection() throws Exception {

        // Given an existing collection
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.type = CollectionType.manual;
        collectionDescription.publishDate = new Date();
        Collection collection = Collection.create(collectionDescription, zebedee, publisherSession);

        // When the collection is updated
        String newName = "Economy Release";
        String filename = PathUtils.toFilename(newName);
        CollectionDescription updatedDescription = new CollectionDescription(newName);
        updatedDescription.type = CollectionType.scheduled;
        updatedDescription.publishDate = new DateTime(collectionDescription.publishDate).plusHours(1).toDate();
        Collection.update(collection, updatedDescription, zebedee, new CollectionScheduler());


        // Then the properties of the description passed to update have been updated.
        Path rootPath = builder.zebedee.resolve(Zebedee.COLLECTIONS);
        Path collectionFolderPath = rootPath.resolve(filename);
        Path collectionJsonPath = rootPath.resolve(filename + ".json");

        Path oldJsonPath = rootPath.resolve(PathUtils.toFilename(name) + ".json");

        assertTrue(Files.exists(collectionFolderPath));
        assertTrue(Files.exists(collectionJsonPath));
        assertTrue(!Files.exists(oldJsonPath));

        CollectionDescription updatedCollectionDescription;
        try (InputStream inputStream = Files.newInputStream(collectionJsonPath)) {
            updatedCollectionDescription = Serialiser.deserialise(inputStream, CollectionDescription.class);
        }

        assertNotNull(updatedCollectionDescription);
        assertEquals(collectionDescription.id, updatedCollectionDescription.id);
        assertEquals(newName, updatedCollectionDescription.name);
        assertEquals(updatedDescription.type, updatedCollectionDescription.type);
        assertEquals(updatedDescription.publishDate, updatedCollectionDescription.publishDate);
        assertTrue(updatedCollectionDescription.events.hasEventForType(EventType.CREATED));
    }

    @Test
    public void shouldUpdateScheduleTimeForAScheduledCollection() throws Exception {

        // Given an existing collection that has been scheduled
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        collectionDescription.publishDate = DateTime.now().plusSeconds(2).toDate();
        collectionDescription.type = CollectionType.scheduled;
        Collection collection = Collection.create(collectionDescription, zebedee, publisherSession);
        CollectionScheduler scheduler = new CollectionScheduler();
        CollectionScheduler.schedulePublish(scheduler, collection, zebedee);

        // When the collection is updated with a new release time
        String newName = "Economy Release";
        CollectionDescription updatedDescription = new CollectionDescription(newName);
        updatedDescription.type = CollectionType.scheduled;
        updatedDescription.publishDate = DateTime.now().plusSeconds(10).toDate();
        Collection updated = Collection.update(collection, updatedDescription, zebedee, scheduler);

        assertTrue(scheduler.taskExistsForCollection(updated));
        long timeUntilTaskRun = scheduler.getTaskForCollection(updated).getDelay(TimeUnit.SECONDS);
        assertTrue(timeUntilTaskRun > 8);
    }

    @Test(expected = BadRequestException.class)
    public void updateCollectionShouldThrowBadRequestExceptionForNullCollection() throws Exception {

        // Given a null collection
        Collection collection = null;

        // When we call the static update method
        Collection.update(collection, new CollectionDescription("name"), zebedee, new CollectionScheduler());

        // Then the expected exception is thrown.
    }

    @Test(expected = CollectionNotFoundException.class)
    public void shouldNotInstantiateInInvalidFolder() throws Exception {

        // Given
        // A folder that isn't a valid release:
        String name = "Population Release";
        CollectionDescription collectionDescription = new CollectionDescription(name);
        Collection.create(collectionDescription, zebedee, publisherSession);
        Path releasePath = builder.zebedee.resolve(Zebedee.COLLECTIONS).resolve(
                PathUtils.toFilename(name));
        FileUtils.cleanDirectory(releasePath.toFile());

        // When
        new Collection(releasePath, zebedee);

        // Then
        // We should get an exception.
    }

    @Test
    public void shouldCreate() throws IOException {

        // Given
        // The content doesn't exist at any level:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";

        // When
        boolean created = collection.create(publisher1Email, uri);

        // Then
        assertTrue(created);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertTrue(Files.exists(inProgress.resolve(uri.substring(1))));

        // check an event has been created for the content being created.
        assertTrue(collection.description.eventsByUri.get(uri).hasEventForType(EventType.CREATED));
    }

    @Test
    public void shouldNotCreateIfPublished() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createPublishedFile(uri);

        // When
        boolean created = collection.create(publisherSession.email, uri);

        // Then
        assertFalse(created);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
    }

    @Test
    public void shouldNotCreateIfReviewed() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createReviewedFile(uri);

        // When
        boolean created = collection.create(publisherSession.email, uri);

        // Then
        assertFalse(created);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
    }

    @Test
    public void shouldNotCreateIfComplete() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createReviewedFile(uri);

        // When
        boolean created = collection.create(publisherSession.email, uri);

        // Then
        assertFalse(created);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
    }

    @Test
    public void shouldNotCreateIfInProgress() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/abmi.html";
        builder.createInProgressFile(uri);

        // When
        boolean created = collection.create(publisherSession.email, uri);

        // Then
        assertFalse(created);
    }

    @Test
    public void shouldDeleteAllFilesFromInProgressDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createInProgressFile("/" + jsonFile);
        builder.createInProgressFile("/" + csvFile);

        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);

        // When the delete method is called on the json file
        boolean result = collection.deleteContent(publisherSession.email, jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(inProgress.resolve(jsonFile)));
        assertFalse(Files.exists(inProgress.resolve(csvFile)));
        // check an event has been created for the content being deleted.
        collection.description.eventsByUri.get("/" + jsonFile).hasEventForType(EventType.DELETED);
    }

    @Test
    public void shouldDeleteAllFilesFromCompleteDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createCompleteFile("/" + jsonFile);
        builder.createCompleteFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.COMPLETE);

        // When the delete method is called on the json file
        boolean result = collection.deleteContent(publisherSession.email, jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertFalse(Files.exists(root.resolve(csvFile)));
        collection.description.eventsByUri.get("/" + jsonFile).hasEventForType(EventType.DELETED);
    }

    @Test
    public void shouldDeleteAllFilesFromReviewedDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createReviewedFile("/" + jsonFile);
        builder.createReviewedFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.REVIEWED);

        // When the delete method is called on the json file
        boolean result = collection.deleteContent(publisher1Email, jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertFalse(Files.exists(root.resolve(csvFile)));
        collection.description.eventsByUri.get("/" + jsonFile).hasEventForType(EventType.DELETED);
    }

    @Test
    public void shouldDeleteOnlyGivenFileFromReviewedDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createReviewedFile("/" + jsonFile);
        builder.createReviewedFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.REVIEWED);

        // When the delete method is called on the json file
        boolean result = collection.deleteFile(jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertTrue(Files.exists(root.resolve(csvFile)));
    }

    @Test
    public void shouldDeleteOnlyGivenFileFromCompleteDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createCompleteFile("/" + jsonFile);
        builder.createCompleteFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.COMPLETE);

        // When the delete method is called on the json file
        boolean result = collection.deleteFile(jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertTrue(Files.exists(root.resolve(csvFile)));
    }

    @Test
    public void shouldDeleteOnlyGivenFileFromInProgressDirectory() throws IOException {

        // Given a content instance with a json file and csv file in it.
        String jsonFile = Random.id() + ".json";
        String csvFile = Random.id() + ".csv";

        builder.createInProgressFile("/" + jsonFile);
        builder.createInProgressFile("/" + csvFile);

        Path root = builder.collections.get(1).resolve(Collection.IN_PROGRESS);

        // When the delete method is called on the json file
        boolean result = collection.deleteFile(jsonFile);

        // Then both the json file and csv file are deleted.
        assertTrue(result);
        assertFalse(Files.exists(root.resolve(jsonFile)));
        assertTrue(Files.exists(root.resolve(csvFile)));
    }

    @Test
    public void shouldEditPublished() throws IOException {

        // Given
        // The content exists publicly:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);

        // When
        boolean edited = collection.edit(publisher1Email, uri);

        // Then
        assertTrue(edited);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertTrue(Files.exists(inProgress.resolve(uri.substring(1))));

        Path published = builder.zebedee.resolve(Zebedee.PUBLISHED);
        Path content = published.resolve(uri.substring(1));
        assertTrue(Files.exists(content));

        // check an event has been created for the content being created.
        collection.description.eventsByUri.get(uri).hasEventForType(EventType.EDITED);
    }

    @Test
    public void shouldEditComplete() throws IOException {

        // Given
        // The content exists, has been edited and completed:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        builder.createCompleteFile(uri);

        // When
        boolean edited = collection.edit(publisher1Email, uri);

        // Then
        // It should be edited
        assertTrue(edited);

        // It should be in in progress
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertTrue(Files.exists(inProgress.resolve(uri.substring(1))));

        // check the file no longer exists in complete, the previous version is no longer wanted.
        Path complete = builder.collections.get(1).resolve(Collection.COMPLETE);
        assertFalse(Files.exists(complete.resolve(uri.substring(1))));
    }

    @Test
    public void shouldEditReviewed() throws IOException {

        // Given
        // The content exists, has been edited and reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        builder.createReviewedFile(uri);

        // When
        boolean edited = collection.edit(publisher1Email, uri);

        // Then
        // It should be edited
        assertTrue(edited);

        // It should be in in progress
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertTrue(Files.exists(inProgress.resolve(uri.substring(1))));

        // check the file no longer exists in reviewed, the previous version is no longer wanted.
        Path reviewed = builder.collections.get(1).resolve(Collection.REVIEWED);
        assertFalse(Files.exists(reviewed.resolve(uri.substring(1))));
    }

    @Test
    public void shouldEditIfEditingAlready() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createInProgressFile(uri);

        // When
        boolean edited = collection.edit(publisher1Email, uri);

        // Then
        assertTrue(edited);
    }

    @Test
    public void shouldNotEditIfEditingElsewhere() throws IOException {

        // Given
        // The content already exists in another release:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.isBeingEditedElsewhere(uri, 0);

        // When
        boolean edited = collection.edit(publisher1Email, uri);

        // Then
        assertFalse(edited);
    }

    @Test
    public void shouldNotEditIfDoesNotExist() throws IOException {

        // Given
        // The content does not exist:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";

        // When
        boolean edited = collection.edit(publisher1Email, uri);

        // Then
        assertFalse(edited);
    }

    @Test
    public void shouldReviewWithReviewer() throws IOException, BadRequestException, UnauthorizedException, NotFoundException {

        // Given
        // The content exists, has been edited and complete:
        String uri = CreateCompleteContent();

        // When
        // One of the digital publishing team reviews it
        boolean reviewed = collection.review(builder.createSession(builder.publisher2), uri);

        // Then
        // The content should be reviewed and no longer located in "in progress"
        assertTrue(reviewed);
        Path edited = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(edited.resolve(uri.substring(1))));

        // check an event has been created for the content being created.
        collection.description.eventsByUri.get(uri).hasEventForType(EventType.REVIEWED);
    }

    @Test(expected = UnauthorizedException.class)
    public void shouldNotReviewAsPublisher() throws IOException, BadRequestException, UnauthorizedException, NotFoundException {

        // Given
        // The content exists, has been edited and complete by publisher1:
        String uri = CreateCompleteContent();

        // When
        // the original content creator attempts to review the content
        collection.review(builder.createSession(publisher1Email), uri);

        // Then
        // expect an Unauthorized error
    }

    private String CreatePublishedContent() throws IOException {
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        return uri;
    }

    private String CreateEditedContent() throws IOException {
        String uri = CreatePublishedContent();
        collection.edit(publisher1Email, uri);
        return uri;
    }

    private String CreateCompleteContent() throws IOException {
        String uri = CreateEditedContent();
        collection.complete(publisher1Email, uri);
        return uri;
    }

    @Test(expected = BadRequestException.class)
    public void shouldNotReviewIfContentHasNotBeenCompleted() throws IOException, BadRequestException, UnauthorizedException, NotFoundException {

        // Given some content that has been edited by a publisher:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        collection.edit(publisher1Email, uri);

        // When - A reviewer edits reviews content
        boolean reviewed = collection.review(builder.createSession(builder.publisher2), uri);

        // Then
        // Expect an error
    }

    @Test
    public void shouldComplete() throws IOException {

        // Given
        // The content exists, has been edited and complete:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createPublishedFile(uri);
        builder.createInProgressFile(uri);

        // When
        boolean complete = collection.complete(publisher1Email, uri);

        // Then
        assertTrue(complete);
        Path edited = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(edited.resolve(uri.substring(1))));

        // check an event has been created for the content being created.
        collection.description.eventsByUri.get(uri).hasEventForType(EventType.COMPLETED);
    }

    @Test
    public void completeShouldMoveFilesWithNoExtension() throws IOException {

        // Given
        // The content exists, has been edited and complete:
        String uri = "/economy/inflationandpriceindices/timeseries/fileWithNoExtension";
        builder.createInProgressFile(uri);

        // When
        boolean complete = collection.complete(publisher1Email, uri);

        // Then
        assertTrue(complete);
        Path inProgressPath = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        Path completedPath = builder.collections.get(1).resolve(Collection.COMPLETE);
        assertFalse(Files.exists(inProgressPath.resolve(uri.substring(1))));
        assertTrue(Files.exists(completedPath.resolve(uri.substring(1))));

        // check an event has been created for the content being created.
        collection.description.eventsByUri.get(uri).hasEventForType(EventType.COMPLETED);
    }

    @Test
    public void shouldNotCompleteIfReviewed() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createReviewedFile(uri);

        // When
        boolean isComplete = collection.complete(publisher1Email, uri);

        // Then
        assertFalse(isComplete);
    }

    @Test
    public void shouldNotCompleteIfAlreadyComplete() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createCompleteFile(uri);

        // When
        boolean isComplete = collection.complete(publisher1Email, uri);

        // Then
        assertFalse(isComplete);
    }

    @Test
    public void shouldNotCompleteIfNotEditing() throws IOException {

        // Given
        // The content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        builder.createCompleteFile(uri);

        // When
        boolean isComplete = collection.complete(publisher1Email, uri);

        // Then
        assertFalse(isComplete);
    }

    @Test(expected = BadRequestException.class)
    public void shouldNotReviewIfAlreadyReviewed() throws IOException, BadRequestException, UnauthorizedException, NotFoundException {

        // Given
        // The content already exists:
        String uri = CreateCompleteContent();
        builder.createReviewedFile(uri);

        // When
        // An alternative publisher reviews the content
        collection.review(builder.createSession(builder.publisher2), uri);

        // Then
        // Expect error
    }

    @Test(expected = NotFoundException.class)
    public void shouldNotReviewIfNotPreviouslyCompleted() throws IOException, BadRequestException, UnauthorizedException, NotFoundException {

        // Given
        // Some content:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        collection.edit(publisher1Email, uri);

        // When content is trying to be reviewed before being completed
        boolean reviewed = collection.review(builder.createSession(publisher1Email), uri);

        // Then the expected exception is thrown.
    }

    @Test
    public void shouldBeInProgress() throws IOException {

        // Given
        // The content is currently being edited:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createInProgressFile(uri);

        // When
        boolean inProgress = collection.isInProgress(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertTrue(inProgress);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldBeComplete() throws IOException {

        // Given
        // The content has been completed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createCompleteFile(uri);

        // When
        boolean complete = collection.isComplete(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertTrue(complete);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldBeReviewed() throws IOException {

        // Given
        // The content has been reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createReviewedFile(uri);

        // When
        boolean reviewed = collection.isReviewed(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertTrue(reviewed);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldNotBeCompleteIfInProgress() throws IOException {

        // Given
        // The content has been reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createCompleteFile(uri);
        builder.createInProgressFile(uri);

        // When
        boolean isComplete = collection.isComplete(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertFalse(isComplete);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldNotBeReviewedIfComplete() throws IOException {

        // Given
        // The content has been complete:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createReviewedFile(uri);
        builder.createCompleteFile(uri);

        // When
        boolean reviewed = collection.isReviewed(uri);
        boolean isInCollection = collection.isInCollection(uri);

        // Then
        assertFalse(reviewed);
        assertTrue(isInCollection);
    }

    @Test
    public void shouldNotBeReviewedIfInProgress() throws IOException {

        // Given
        // The content has been reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createReviewedFile(uri);
        builder.createInProgressFile(uri);

        // When
        boolean reviewed = collection.isReviewed(uri);
        boolean inRelease = collection.isInCollection(uri);

        // Then
        assertFalse(reviewed);
        assertTrue(inRelease);
    }

    @Test
    public void shouldGetPath() throws IOException {

        // Given
        // We're editing some content:
        String uri = "/economy/inflationandpriceindices/timeseries/beer.html";
        builder.createPublishedFile(uri);
        builder.createReviewedFile(uri);
        builder.createInProgressFile(uri);

        // When
        // We write some output to the content:
        Path path = collection.getInProgressPath(uri);
        try (Writer writer = Files.newBufferedWriter(path,
                Charset.forName("utf8"));) {
            writer.append("test");
        }

        // Then
        // The output should have gone to the expected copy of the file:
        Path inProgressPath = builder.collections.get(1).resolve(
                Collection.IN_PROGRESS);
        Path expectedPath = inProgressPath.resolve(uri.substring(1));
        assertTrue(Files.size(expectedPath) > 0);
    }

    @Test
    public void shouldReturnInProgressUris() throws IOException {
        // Given
        // There are these files in progress:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        String uri2 = "/economy/someotherthing/timeseries/e4c4.html";
        builder.createInProgressFile(uri);
        builder.createInProgressFile(uri2);

        // When
        // We attempt to get the in progress files.
        List<String> uris = collection.inProgressUris();

        // Then
        // We get out the expected in progress files.
        assertTrue(uris.contains(uri));
        assertTrue(uris.contains(uri2));

        // and the uri lists for other states are empty.
        assertTrue(collection.completeUris().isEmpty());
        assertTrue(collection.reviewedUris().isEmpty());
    }

    @Test
    public void shouldReturnCompleteUris() throws IOException {
        // Given
        // There are these files complete:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        String uri2 = "/economy/someotherthing/timeseries/e4c4.html";
        builder.createCompleteFile(uri);
        builder.createCompleteFile(uri2);

        // When
        // We attempt to get the complete files.
        List<String> uris = collection.completeUris();

        // Then
        // We get out the expected complete files.
        assertTrue(uris.contains(uri));
        assertTrue(uris.contains(uri2));

        // and the uri lists for other states are empty.
        assertTrue(collection.inProgressUris().isEmpty());
        assertTrue(collection.reviewedUris().isEmpty());
    }

    @Test
    public void shouldReturnReviewedUris() throws IOException {
        // Given
        // There are these files reviewed:
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        String uri2 = "/economy/someotherthing/timeseries/e4c4.html";
        builder.createReviewedFile(uri);
        builder.createReviewedFile(uri2);

        // When
        // We attempt to get the reviewed files.
        List<String> uris = collection.reviewedUris();

        // Then
        // We get out the expected reviewed files.
        assertTrue(uris.contains(uri));
        assertTrue(uris.contains(uri2));

        // and the uri lists for other states are empty.
        assertTrue(collection.inProgressUris().isEmpty());
        assertTrue(collection.completeUris().isEmpty());
    }

    @Test
    public void shouldFindInProgressUri() throws IOException {
        // Given
        // There is a file in progress
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createInProgressFile(uri);

        // When
        // We attempt to find the file.
        Path path = collection.find(uri);

        // Then
        // We get the path to the in progress file.
        assertTrue(path.toString().contains("/" + Collection.IN_PROGRESS + "/"));
    }

    @Test
    public void shouldFindCompleteUri() throws IOException {
        // Given
        // There is a file in progress
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createCompleteFile(uri);

        // When
        // We attempt to find the file.
        Path path = collection.find(uri);

        // Then
        // We get the path to the in progress file.
        assertTrue(path.toString().contains("/" + Collection.COMPLETE + "/"));
    }

    @Test
    public void shouldFindReviewedUri() throws IOException {
        // Given
        // There is a file in progress
        String uri = "/economy/inflationandpriceindices/timeseries/d7g7.html";
        builder.createReviewedFile(uri);

        // When
        // We attempt to find the file.
        Path path = collection.find(uri);

        // Then
        // We get the path to the in progress file.
        assertTrue(path.toString().contains("/" + Collection.REVIEWED + "/"));
    }

    @Test
    public void associateWithReleaseShouldUseExistingReleaseIfItsAlreadyInCollection() throws NotFoundException, IOException {

        // Given
        // There is a release already in progress
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());
        collection.edit(publisher1Email, uri + "/data.json");

        // When we attempt to associate the collection with a release
        Release result = collection.associateWithRelease(publisher1Email, release);

        assertTrue(result.getDescription().getPublished());
        assertEquals(URI.create(uri), result.getUri());
    }

    @Test
    public void associateWithReleaseShouldSetReleaseToPublished() throws NotFoundException, IOException {

        // Given a release that is announced
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());

        // When we attempt to associate the collection with a release
        Release result = collection.associateWithRelease(publisher1Email, release);

        // Then the release is now in progress for the collection and the published flag is set to true
        assertTrue(collection.isInProgress(uri));
        assertTrue(result.getDescription().getPublished());
        assertEquals(URI.create(uri), result.getUri());
    }

    @Test
    public void populateReleaseShouldAddLinksToReleasePageForCollectionContent() throws ZebedeeException, IOException {

        // Given a collection that is associated with a release and has an article
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());

        CollectionDescription description = new CollectionDescription();
        description.id = Random.id();
        description.name = description.id;
        Collection collection = Collection.create(description, zebedee, publisherSession);

        collection.description.releaseUri = uri;
        collection.associateWithRelease(publisher1Email, release);

        String releaseJsonUri = uri + "/data.json";

        collection.complete(builder.publisher1.email, releaseJsonUri);
        collection.review(builder.createSession(builder.publisher2), releaseJsonUri);

        ContentDetail articleDetail = new ContentDetail("My article", "/some/uri", PageType.article.toString());
        FileUtils.write(collection.reviewed.path.resolve("some/uri/data.json").toFile(), Serialiser.serialise(articleDetail));

        // When we attempt to populate the release from the collection.
        Release result = collection.populateRelease(new FakeCollectionReader(zebedee.collections.path.toString(), collection.description.id));

        // Then the release is now in progress for the collection and the published flag is set to true
        assertEquals(1, result.getRelatedDocuments().size());
        assertEquals("My article", result.getRelatedDocuments().get(0).getTitle());
        assertEquals("/some/uri", result.getRelatedDocuments().get(0).getUri().toString());
    }


    @Test
    public void createCollectionShouldAssociateWithReleaseIfReleaseUriIsPresent() throws Exception {

        // Given an existing release page
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());

        // When a new collection is created with the release uri given
        CollectionDescription collectionDescription = new CollectionDescription(Random.id());
        collectionDescription.releaseUri = release.getUri().toString();
        Collection collection = Collection.create(collectionDescription, zebedee, publisherSession);

        // The release page is in progress within the collection and the collection publish date has been
        // taken from the release page date.
        assertTrue(collection.isInProgress(uri));
        assertEquals(collection.description.publishDate, release.getDescription().getReleaseDate());
    }

    @Test(expected = BadRequestException.class)
    public void createCollectionShouldThrowExceptionIfReleaseDateIsNull() throws Exception {

        // Given an existing release page with a null release date
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, null);

        // When a new collection is created with the release uri given
        CollectionDescription collectionDescription = new CollectionDescription(Random.id());
        collectionDescription.releaseUri = release.getUri().toString();
        Collection.create(collectionDescription, zebedee, publisherSession);

        // Then the expected exception is thrown
    }

    @Test(expected = ConflictException.class)
    public void createCollectionShouldThrowExceptionIfReleaseIsInAnotherCollection() throws Exception {

        // Given an existing release page which is associated with an existing collection
        String uri = String.format("/releases/%s", Random.id());
        Release release = createRelease(uri, new DateTime().plusWeeks(4).toDate());
        CollectionDescription collectionDescription = new CollectionDescription(Random.id());
        collectionDescription.releaseUri = release.getUri().toString();
        Collection.create(collectionDescription, zebedee, publisherSession);

        // When a new collection is created with the release uri given
        collectionDescription = new CollectionDescription(Random.id());
        collectionDescription.releaseUri = release.getUri().toString();
        Collection.create(collectionDescription, zebedee, publisherSession);

        // Then the expected exception is thrown
    }


    @Test(expected = NotFoundException.class)
    public void versionShouldThrowNotFoundIfContentIsNotPublished() throws Exception {

        // Given a URI that has not been published / does not exist.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());

        // When we attempt to create a version for the page
        collection.version(publisher1Email, uri);

        // Then a not found exception is thrown.
    }

    @Test(expected = ConflictException.class)
    public void versionShouldNotCreateASecondVersionForAURI() throws Exception {

        // Given a URI that has been published and already versioned in a collection.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());
        builder.createPublishedFile(uri + "/data.json");
        collection.version(publisher1Email, uri);

        // When we attempt to create a version for the page for a second time
        collection.version(publisher1Email, uri);

        // Then a ConflictException exception is thrown.
    }

    @Test
    public void versionShouldCreateVersionForUri() throws Exception {

        // Given an existing uri that has been publised.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());
        builder.createPublishedFile(uri + "/data.json");

        // When the version function is called for the URI
        ContentItemVersion version = collection.version(publisher1Email, uri);

        // Then the version directory is created, with the page and associated files copied into it
        // check versions file exists
        Path versionsDirectoryPath = version.getVersionedContentItem().getVersionDirectoryPath();
        assertTrue(Files.exists(versionsDirectoryPath));

        // check the json file is in there
        assertTrue(Files.exists(version.getPath()));

        // check for an associated file
        assertTrue(Files.exists(version.getPath().resolve("data.json")));
    }

    @Test(expected = NotFoundException.class)
    public void deleteVersionShouldThrowNotFoundIfVersionDoesNotExistInCollection() throws Exception {

        // Given a collection and a URI of a version that does not exist in the collection.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s/previous/v1", Random.id());

        // When we attempt to delete a version
        collection.deleteVersion(uri);

        // Then a not found exception is thrown.
    }

    @Test(expected = BadRequestException.class)
    public void deleteVersionShouldThrowBadRequestIfNotAValidVersionUri() throws Exception {

        // Given a collection and a URI that is not a version.
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());

        // When we attempt to delete a version
        collection.deleteVersion(uri);

        // Then a BadRequestException is thrown.
    }

    @Test
    public void deleteVersionShouldDeleteVersionDirectory() throws Exception {

        // Given an existing version URI
        String uri = String.format("/economy/inflationandpriceindices/timeseries/%s", Random.id());
        builder.createPublishedFile(uri + "/data.json");
        ContentItemVersion version = collection.version(publisher1Email, uri);

        assertTrue(Files.exists(version.getPath()));
        assertTrue(Files.exists(version.getPath().resolve("data.json")));

        // When the delete version function is called for the version URI
        collection.deleteVersion(version.getUri().toString());

        // Then the versions directory is deleted.
        assertFalse(Files.exists(version.getPath()));
        assertFalse(Files.exists(version.getPath().resolve("data.json")));
    }

    private Release createRelease(String uri, Date releaseDate) throws IOException {
        String trimmedUri = StringUtils.removeStart(uri, "/");
        Release release = new Release();
        release.setDescription(new PageDescription());
        release.getDescription().setPublished(false);
        release.getDescription().setReleaseDate(releaseDate);
        release.setUri(URI.create(uri));
        String content = ContentUtil.serialise(release);

        Path releasePath = zebedee.published.path.resolve(trimmedUri + "/data.json");
        FileUtils.write(releasePath.toFile(), content);
        return release;
    }

    @Test
    public void moveContentShouldRenameInprogressFile() throws IOException {

        // Given the content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        String toUri = "/economy/inflationandpriceindices/timeseries/a9errenamed.html";
        builder.createInProgressFile(uri);

        // When we move content
        boolean edited = collection.moveContent(publisher1Email, uri, toUri);

        // Then the file should exist only in the new location.
        assertTrue(edited);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
        assertTrue(Files.exists(inProgress.resolve(toUri.substring(1))));

        // check an event has been created for the content being created.
        assertTrue(collection.description.eventsByUri.get(uri).hasEventForType(EventType.MOVED));
    }

    @Test
    public void moveContentShouldRenameCompletedFiles() throws IOException {

        // Given the content already exists:
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        String toUri = "/economy/inflationandpriceindices/timeseries/a9errenamed.html";
        builder.createCompleteFile(uri);

        // When we move content
        boolean edited = collection.moveContent(publisher1Email, uri, toUri);

        // Then the file should exist only in the new location.
        assertTrue(edited);
        Path complete = builder.collections.get(1).resolve(Collection.COMPLETE);
        assertFalse(Files.exists(complete.resolve(uri.substring(1))));
        assertTrue(Files.exists(complete.resolve(toUri.substring(1))));

        // check an event has been created for the content being created.
        assertTrue(collection.description.eventsByUri.get(uri).hasEventForType(EventType.MOVED));
    }

    @Test
    public void moveContentShouldOverwriteExistingFiles() throws IOException {

        // Given some existing content in progress.
        String uri = "/economy/inflationandpriceindices/timeseries/a9er.html";
        String toUri = "/economy/inflationandpriceindices/timeseries/a9errenamed.html";
        builder.createInProgressFile(uri);
        builder.createInProgressFile(toUri);

        // When we move content to a URI where some content already exists.
        boolean edited = collection.moveContent(publisher1Email, uri, toUri);

        // Then the existing content should be overwritten.
        assertTrue(edited);
        Path inProgress = builder.collections.get(1).resolve(Collection.IN_PROGRESS);
        assertFalse(Files.exists(inProgress.resolve(uri.substring(1))));
        assertTrue(Files.exists(inProgress.resolve(toUri.substring(1))));

        // check an event has been created for the content being created.
        assertTrue(collection.description.eventsByUri.get(uri).hasEventForType(EventType.MOVED));
    }
}
