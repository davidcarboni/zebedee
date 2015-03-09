package com.github.onsdigital.zebedee;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;

import com.github.davidcarboni.restolino.json.Serialiser;
import com.github.onsdigital.zebedee.json.CollectionDescription;

/**
 * This is a utility class to build a known {@link Zebedee} structure for
 * testing.
 * 
 * @author david
 *
 */
public class Builder {

	String[] collectionNames = { "Inflation Q2 2015", "Labour Market Q2 2015" };
	Path parent;
	Path zebedee;
	List<Path> collections;
	List<String> contentUris;

	Builder(Class<?> name) throws IOException {

		// Create the structure:
		parent = Files.createTempDirectory(name.getSimpleName());
		zebedee = createZebedee(parent);

		// Create the collections:
		collections = new ArrayList<>();
		for (String collectionName : collectionNames) {
			Path collection = createCollection(collectionName, zebedee);
			collections.add(collection);
		}

		// Create some published content:
		Path folder = zebedee.resolve(Zebedee.PUBLISHED);
		contentUris = new ArrayList<>();
		String contentUri;
		Path contentPath;

		// Something for Economy:
		contentUri = "/economy/inflationandpriceindices/bulletins/consumerpriceinflationjune2014.html";
		contentPath = folder.resolve(contentUri.substring(1));
		Files.createDirectories(contentPath.getParent());
		Files.createFile(contentPath);
		contentUris.add(contentUri);

		// Something for Labour market:
		contentUri = "/employmentandlabourmarket/peopleinwork/earningsandworkinghours/bulletins/uklabourmarketjuly2014.html";
		contentPath = folder.resolve(contentUri.substring(1));
		Files.createDirectories(contentPath.getParent());
		Files.createFile(contentPath);
		contentUris.add(contentUri);
	}

	void delete() throws IOException {
		FileUtils.deleteDirectory(parent.toFile());
	}

	/**
	 * Creates a published file.
	 * 
	 * @param uri
	 *            The URI to be created.
	 * @throws IOException
	 *             If a filesystem error occurs.
	 */
	void isPublished(String uri) throws IOException {

		Path published = zebedee.resolve(Zebedee.PUBLISHED);
		Path content = published.resolve(uri.substring(1));
		Files.createDirectories(content.getParent());
		Files.createFile(content);
	}

	/**
	 * Creates an approved file.
	 * 
	 * @param uri
	 *            The URI to be created.
	 * @throws IOException
	 *             If a filesystem error occurs.
	 */
	void isApproved(String uri) throws IOException {

		Path approved = collections.get(1).resolve(Collection.APPROVED);
		Path content = approved.resolve(uri.substring(1));
		Files.createDirectories(content.getParent());
		Files.createFile(content);
	}

	/**
	 * Creates an approved file in a different {@link Collection}.
	 * 
	 * @param uri
	 *            The URI to be created.
	 * @param collection
	 *            The {@link Collection} in which to create the content.
	 * @throws IOException
	 *             If a filesystem error occurs.
	 */
	void isBeingEditedElsewhere(String uri, int collection) throws IOException {

		Path approved = collections.get(collection)
				.resolve(Collection.APPROVED);
		Path content = approved.resolve(uri.substring(1));
		Files.createDirectories(content.getParent());
		Files.createFile(content);
	}

	/**
	 * Creates an in-progress file.
	 * 
	 * @param uri
	 *            The URI to be created.
	 * @throws IOException
	 *             If a filesystem error occurs.
	 */
	void isInProgress(String uri) throws IOException {

		Path inProgress = collections.get(1).resolve(Collection.IN_PROGRESS);
		Path content = inProgress.resolve(uri.substring(1));
		Files.createDirectories(content.getParent());
		Files.createFile(content);
	}

	/**
	 * This method creates the expected set of folders for a Zebedee structure.
	 * This code is intentionaly copied from {@link Zebedee#create(Path)}. This
	 * ensures there's a fixed expectation, rather than relying on a method that
	 * will be tested as part of the test suite.
	 * 
	 * @param parent
	 *            The parent folder, in which the {@link Zebedee} structure will
	 *            be built.
	 * @return The root {@link Zebedee} path.
	 * @throws IOException
	 *             If a filesystem error occurs.
	 */
	private Path createZebedee(Path parent) throws IOException {
		Path path = Files.createDirectory(parent.resolve(Zebedee.ZEBEDEE));
		Files.createDirectory(path.resolve(Zebedee.PUBLISHED));
		Files.createDirectory(path.resolve(Zebedee.COLLECTIONS));
		return path;
	}

	/**
	 * This method creates the expected set of folders for a Zebedee structure.
	 * This code is intentionaly copied from
	 * {@link Collection#create(String, Zebedee)}. This ensures there's a fixed
	 * expectation, rather than relying on a method that will be tested as part
	 * of the test suite.
	 * 
	 * @param root
	 *            The root of the {@link Zebedee} structure
	 * @param name
	 *            The name of the {@link Collection}.
	 * @return The root {@link Collection} path.
	 * @throws IOException
	 *             If a filesystem error occurs.
	 */
	private Path createCollection(String name, Path root) throws IOException {

		String filename = PathUtils.toFilename(name);
		Path collections = root.resolve(Zebedee.COLLECTIONS);

		// Create the folders:
		Path collection = collections.resolve(filename);
		Files.createDirectory(collection);
		Files.createDirectory(collection.resolve(Collection.APPROVED));
		Files.createDirectory(collection.resolve(Collection.IN_PROGRESS));

		// Create the description:
		Path collectionDescription = collections.resolve(filename + ".json");
		CollectionDescription description = new CollectionDescription();
		description.name = name;
		try (OutputStream output = Files.newOutputStream(collectionDescription)) {
			Serialiser.serialise(output, description);
		}

		return collection;
	}

}
