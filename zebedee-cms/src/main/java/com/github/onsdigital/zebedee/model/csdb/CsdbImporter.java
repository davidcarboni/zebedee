package com.github.onsdigital.zebedee.model.csdb;

import com.github.davidcarboni.cryptolite.KeyExchange;
import com.github.onsdigital.zebedee.api.Root;
import com.github.onsdigital.zebedee.content.page.base.Page;
import com.github.onsdigital.zebedee.content.page.base.PageType;
import com.github.onsdigital.zebedee.content.page.statistics.dataset.Dataset;
import com.github.onsdigital.zebedee.content.page.statistics.dataset.DownloadSection;
import com.github.onsdigital.zebedee.exceptions.BadRequestException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.UnauthorizedException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.EventType;
import com.github.onsdigital.zebedee.model.*;
import com.github.onsdigital.zebedee.model.publishing.PublishNotification;
import com.github.onsdigital.zebedee.reader.CollectionReader;
import com.github.onsdigital.zebedee.reader.ContentReader;
import com.github.onsdigital.zebedee.util.EncryptionUtils;
import com.github.onsdigital.zebedee.util.Log;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.PrivateKey;
import java.util.List;
import java.util.Map;

/**
 * Handles a notification when a new CSDB file is available to zebedee.
 * This class handles the retrieval of the CSDB data and saving it into the premade collection.
 */
public class CsdbImporter {

    public static final String APPLICATION_KEY_ID = "csdb-import";

    public static void processNotification(
            PrivateKey privateCsdbImportKey,
            String csdbIdentifier,
            DylanClient dylan,
            Collections collections,
            Map<String, SecretKey> keyCache) throws IOException, ZebedeeException {

        Collection collection;
        try (InputStream csdbData = getDylanData(privateCsdbImportKey, csdbIdentifier, dylan)) {
            collection = addCsdbToCollectionWithCorrectDataset(csdbIdentifier, collections, keyCache, csdbData);
        }

        if (collection != null && collection.description.approvedStatus == true) {
            preProcessCollection(collection);
        } else {
            Log.print("The collection %s is not approved.", collection.description.name);
        }
    }

    /**
     * Once a CSDB file has been inserted into a collection, the timeseries files must be regenerated.
     * Generate the files and then publish the updated uri list to babbage for cache notification.
     * @param collection
     * @throws IOException
     * @throws ZebedeeException
     */
    public static void preProcessCollection(Collection collection) throws IOException, ZebedeeException {
        SecretKey collectionKey = Root.zebedee.keyringCache.schedulerCache.get(collection.description.id);
        CollectionReader collectionReader = new ZebedeeCollectionReader(collection, collectionKey);
        CollectionWriter collectionWriter = new ZebedeeCollectionWriter(collection, collectionKey);
        ContentReader publishedReader = new ContentReader(Root.zebedee.published.path);

        List<String> uriList;
        try {
            uriList = Collections.preprocessTimeseries(Root.zebedee, collection, collectionReader, collectionWriter, publishedReader);
        } catch (URISyntaxException e) {
            throw new BadRequestException("Brian could not process this collection");
        }

        new PublishNotification(collection, uriList).sendNotification(EventType.APPROVED);
    }

    /**
     * Save the CSDB file into the collection that it should be inserted into.
     * @param csdbIdentifier
     * @param collections
     * @param keyCache
     * @param csdbData
     * @return
     * @throws IOException
     * @throws BadRequestException
     * @throws UnauthorizedException
     * @throws NotFoundException
     */
    private static Collection addCsdbToCollectionWithCorrectDataset(String csdbIdentifier,
                                                                    Collections collections,
                                                                    Map<String, SecretKey> keyCache,
                                                                    InputStream csdbData) throws IOException, BadRequestException, UnauthorizedException, NotFoundException {
        for (Collection collection : collections.list()) {
            SecretKey collectionKey = keyCache.get(collection.description.id);
            CollectionReader collectionReader = new ZebedeeCollectionReader(collection, collectionKey);
            Path csdbFileUri = getCsdbPathFromCollection(csdbIdentifier, collectionReader);
            if (csdbFileUri != null) {
                CollectionWriter collectionWriter = new ZebedeeCollectionWriter(collection, collectionKey);
                collectionWriter.getReviewed().write(csdbData, csdbFileUri.toString());
                return collection;
            }
        }
        return null; // if no collection is found.
    }

    /**
     * Given a CSDB file identifier and a collection, find the path the CSDB should be added to. If the CSDB file
     * does not belong to the collection then null is returned.
     * @param csdbIdentifier
     * @param collectionReader
     * @return
     * @throws IOException
     */
    static Path getCsdbPathFromCollection(String csdbIdentifier, CollectionReader collectionReader) throws IOException {
        List<String> uris = collectionReader.getReviewed().listUris();

        // for each uri in the collection
        for (String uri : uris) {
            // deserialise only the uris that are datasets
            if (uri.contains("/datasets/")) {
                try {
                    Page page = collectionReader.getReviewed().getContent(uri);

                    // if the page is a landing page then check the CSDB ID and put the csdb in the right place
                    if (page.getType().equals(PageType.timeseries_dataset)) {

                        String filename = csdbIdentifier + ".csdb";
                        Dataset datasetPage = (Dataset) page;

                        for (DownloadSection downloadSection : datasetPage.getDownloads()) {
                            if (downloadSection.getFile().equals(filename)) {
                                // work out what the URI of the CSDB file should be from the URI of the dataset page it belongs to.
                                return Paths.get(datasetPage.getUri().toString()).resolve(filename);
                            }
                        }
                    }
                } catch (ZebedeeException e) {
                    Log.print(e);
                }
            }
        }
        return null;
    }

    /**
     * Co-ordinate the key management to decrypt data provided by dylan.
     *
     * @param privateCsdbImportKey
     * @param csdbIdentifier
     * @param dylan
     * @return
     * @throws IOException
     */
    static InputStream getDylanData(PrivateKey privateCsdbImportKey, String csdbIdentifier, DylanClient dylan) throws IOException {
        // get key from dylan
        String dylanKey = dylan.getEncryptedSecretKey();

        // decrypt it using the private key
        SecretKey secretKey = new KeyExchange().decryptKey(dylanKey, privateCsdbImportKey);

        // get the csdb data
        InputStream csdbData = dylan.getEncryptedCsdbData(csdbIdentifier);

        // decrypt it using the retrieved key.
        InputStream inputStream = EncryptionUtils.encryptionInputStream(csdbData, secretKey);

        return inputStream;
    }
}
