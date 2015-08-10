package com.github.onsdigital.zebedee.reader.util;

import com.github.onsdigital.zebedee.content.collection.Collection;
import com.github.onsdigital.zebedee.content.dynamic.browse.ContentNode;
import com.github.onsdigital.zebedee.content.page.base.Page;
import com.github.onsdigital.zebedee.content.util.ContentUtil;
import com.github.onsdigital.zebedee.exceptions.CollectionNotFoundException;
import com.github.onsdigital.zebedee.exceptions.NotFoundException;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.reader.Resource;
import com.github.onsdigital.zebedee.util.URIUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static com.github.onsdigital.zebedee.reader.configuration.ReaderConfiguration.getConfiguration;

/**
 * Created by bren on 30/07/15.
 */
public class CollectionContentReader {

    private Path collections;
    private ContentReader inProgress;
    private ContentReader complete;
    private ContentReader reviewed;

    /**
     * @param collectionsFolderPath path of the collections folder
     */
    public CollectionContentReader(String collectionsFolderPath, String collectionId) throws NotFoundException, IOException, CollectionNotFoundException {
        if (collectionsFolderPath == null) {
            throw new NullPointerException("Collections folder can not be null");
        }
        this.collections = Paths.get(collectionsFolderPath);
        Path collectionsPath = findCollectionPath(collectionId);
        inProgress = getContentReader(collectionsPath, getConfiguration().getInProgressFolderName());
        complete = getContentReader(collectionsPath, getConfiguration().getCompleteFolderName());
        reviewed = getContentReader(collectionsPath, getConfiguration().getReviewedFolderName());
    }

    /**
     * Reads content under a given collection root folder.
     * Tries finding content under in progress, completePath and reviewedPath folders respectively. Throws not found exception if not found
     *
     * @param path path of requested content under requested root folder
     * @return
     * @throws NotFoundException
     * @throws IOException
     */
    public Page getContent(String path) throws ZebedeeException, IOException {
        URI dataFilePath = URI.create(URIUtils.removeTrailingSlash(path) + "/").resolve(getConfiguration().getDataFileName());
        Resource resource = findResource(dataFilePath.toString());
        Page page = ContentUtil.deserialiseContent(resource.getData());
        page.setUri(URI.create(path)); //set on the fly, overwriting whatever is in the file
        return page;
    }


    public Resource getResource(String path) throws ZebedeeException, IOException {
        return findResource(path);
    }

    /**
     * @param path
     * @return uri-node mapping
     * @throws ZebedeeException
     * @throws IOException
     */
    public Map<URI, ContentNode> getChildren(String path) throws ZebedeeException, IOException {
        Map<URI, ContentNode> children = new HashMap<>();
        //TODO: Same document should not be in two different state, it should be safe to overwrite if it appears in multiple places?.
        // Is there a validation mechanism ? Might be needed
        children.putAll(getChildrenQuite(path, reviewed));
        children.putAll(getChildrenQuite(path, complete));//overwrites reviewed content if appears in both places
        children.putAll(getChildrenQuite(path, inProgress));//overwrites complete and reviewed content if appears in both places
        return children;
    }


    /**
     * @param path
     * @return uri-node mapping
     * @throws ZebedeeException
     * @throws IOException
     */
    public Map<URI, ContentNode> getParents(String path) throws ZebedeeException, IOException {
        Map<URI, ContentNode> parents = new HashMap<>();
        //TODO: Same document should not be in two different state, it should be safe to overwrite if it appears in multiple places?.
        // Is there a validation mechanism ? Might be needed
        parents.putAll(getParentsQuite(path, reviewed));
        parents.putAll(getParentsQuite(path, complete));//overwrites reviewed content if appears in both places
        parents.putAll(getParentsQuite(path, inProgress));//overwrites complete and reviewed content if appears in both places
        return parents;
    }


    private Resource findResource(String path) throws IOException, ZebedeeException {
        Resource resource = getQuite(path, inProgress);
        if (resource == null) {
            resource = getQuite(path, complete);
            if (resource == null) {
                resource = reviewed.getResource(path);
            }
        }
        return resource;
    }

    //If content not found with given reader do not shout
    private Resource getQuite(String path, ContentReader contentReader) throws ZebedeeException, IOException {
        try {
            return contentReader.getResource(path);
        } catch (NotFoundException e) {
            return null;
        }
    }


    //If content not found with given reader do not shout
    private Page getLatestQuite(String path, ContentReader contentReader) throws ZebedeeException, IOException {
        try {
            return contentReader.getLatestContent(path);
        } catch (NotFoundException e) {
            return null;
        }
    }

    //If content not found with given reader do not shout
    private Map<URI, ContentNode> getChildrenQuite(String path, ContentReader contentReader) throws ZebedeeException, IOException {
        try {
            return contentReader.getChildren(path);
        } catch (NotFoundException e) {
            return Collections.emptyMap();
        }
    }

    //If content not found with given reader do not shout
    private Map<URI, ContentNode> getParentsQuite(String path, ContentReader contentReader) throws ZebedeeException, IOException {
        try {
            return contentReader.getParents(path);
        } catch (NotFoundException e) {
            return Collections.emptyMap();
        }
    }

    //TODO: If collection folder names were ids or we saved cookie as collection's name we would not need to search collection, but just read the path

    //Finds collection name with given id
    private Path findCollectionPath(String collectionId) throws IOException, NotFoundException, CollectionNotFoundException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(collections,"*.{json}")) {
            for (Path path : stream) {
                if (Files.isDirectory(path)) {
                    continue;
                } else {
                    try (InputStream fileStream = Files.newInputStream(path)) {
                        Collection collection = ContentUtil.deserialise(fileStream, Collection.class);
                        if (StringUtils.equalsIgnoreCase(collection.getId(), collectionId)) {
                            return collections.resolve(collection.getName());

                        }
                    }
                }
            }
            throw new CollectionNotFoundException("Collection with given id not found, id:" + collectionId);
        }
    }


    public Page getLatestContent(String path) throws ZebedeeException, IOException {
        Page content = getLatestQuite(path, inProgress);
        if (content == null) {
            content = getLatestQuite(path, complete);
            if (content == null) {
                content = reviewed.getLatestContent(path);
            }
        }
        return content;
    }


    private ContentReader getContentReader(Path collectionPath, String folderName) {
        return new ContentReader(collectionPath.resolve(folderName));
    }
}
