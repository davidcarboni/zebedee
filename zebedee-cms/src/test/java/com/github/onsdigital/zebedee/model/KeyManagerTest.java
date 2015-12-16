package com.github.onsdigital.zebedee.model;

import com.github.davidcarboni.cryptolite.Random;
import com.github.davidcarboni.httpino.Serialiser;
import com.github.onsdigital.zebedee.Builder;
import com.github.onsdigital.zebedee.Zebedee;
import com.github.onsdigital.zebedee.exceptions.ZebedeeException;
import com.github.onsdigital.zebedee.json.CollectionDescription;
import com.github.onsdigital.zebedee.json.Credentials;
import com.github.onsdigital.zebedee.json.Session;
import com.github.onsdigital.zebedee.json.User;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.security.PublicKey;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;

/**
 * Created by thomasridd on 18/11/15.
 */
public class KeyManagerTest {
    Zebedee zebedee;
    Builder builder;

    @Before
    public void setUp() throws Exception {
        builder = new Builder(this.getClass());
        zebedee = new Zebedee(builder.zebedee, false);
    }

    @After
    public void tearDown() throws Exception {
        builder.delete();
    }

    @Test
    public void isEncrypted_whenCollectionGenerated_isSetToFalse() throws ZebedeeException, IOException {
        // Given
        // a collection is created
        Session session = zebedee.openSession(builder.publisher1Credentials);
        CollectionDescription collectionDescription = new CollectionDescription();
        collectionDescription.name = this.getClass().getSimpleName() + "-" + Random.id();
        Collection.create(collectionDescription, zebedee, session);

        // When
        // we reload it
        Collection reloaded = zebedee.collections.list().getCollection(collectionDescription.id);

        // Then
        // isEncrypted is false
        assertEquals(false, reloaded.description.isEncrypted);
    }

    @Test
    public void isEncrypted_whenSetToTrue_persists() throws IOException, ZebedeeException {
        // Given
        // a collection is created, isEncrypted is set, and is set to true
        CollectionDescription collectionDescription = createCollection(true);


        // When
        // we reload the collection
        Collection reloaded = zebedee.collections.list().getCollection(collectionDescription.id);

        // Then
        // isEncrypted is true
        assertEquals(true, reloaded.description.isEncrypted);
    }

    private CollectionDescription createCollection(boolean isEncrypted) throws IOException, ZebedeeException {
        Session session = zebedee.openSession(builder.publisher1Credentials);
        CollectionDescription collectionDescription = new CollectionDescription();
        collectionDescription.name = this.getClass().getSimpleName() + "-" + Random.id();
        collectionDescription.isEncrypted = isEncrypted;
        Collection.create(collectionDescription, zebedee, session);
        return collectionDescription;
    }

    @Test
    public void userKeyring_whenCollectionGenerated_hasKeyForCollection() throws ZebedeeException, IOException {
        // Given
        // a user session
        Session session = zebedee.openSession(builder.publisher1Credentials);
        CollectionDescription collectionDescription = new CollectionDescription();
        collectionDescription.name = this.getClass().getSimpleName() + "-" + Random.id();
        assertEquals(0, builder.publisher1.keyring.size());

        // When
        // we generate the collection
        Collection.create(collectionDescription, zebedee, session);

        // Then
        // the user has a key for the collection
        User user = zebedee.users.get(session.email);
        assertEquals(1, user.keyring.size());

        // and in the keyringCache
        assertEquals(1, zebedee.keyringCache.get(session).keys.size());
    }

    @Test
    public void otherPublisherKeyring_whenCollectionGenerated_hasKeyForCollection() throws ZebedeeException, IOException {
        // Given
        // publisher A
        Session session = zebedee.openSession(builder.publisher1Credentials);
        CollectionDescription collectionDescription = new CollectionDescription();
        collectionDescription.name = this.getClass().getSimpleName() + "-" + Random.id();
        assertEquals(0, builder.publisher2.keyring.size());

        // When
        // a collection is generated by publisher A
        Collection.create(collectionDescription, zebedee, session);

        // Then
        // publisher B gets a key for the collection
        User user = zebedee.users.get(builder.publisher2.email);
        assertEquals(1, user.keyring.size());
    }

    private CollectionDescription publishCollection(Session session) throws IOException, ZebedeeException {
        CollectionDescription collectionDescription = new CollectionDescription();
        collectionDescription.name = this.getClass().getSimpleName() + "-" + Random.id();
        Collection.create(collectionDescription, zebedee, session);
        return collectionDescription;
    }

    @Test
    public void publisherKeyring_whenPasswordReset_receivesAllCollections() throws ZebedeeException, IOException {
        // Given
        // publisher A and details for publisher B
        Session sessionA = zebedee.openSession(builder.administratorCredentials);
        CollectionDescription collection = publishCollection(sessionA);

        assertEquals(1, zebedee.users.get(builder.administrator.email).keyring().size());
        assertEquals(1, zebedee.users.get(builder.publisher1.email).keyring().size());


        // When
        // publisher A resets password

        Credentials credentials = builder.publisher1Credentials;
        credentials.password = "Adam Bob Charlie Danny";
        zebedee.users.setPassword(sessionA, credentials);

        // Then
        // publisher A retains keys
        User user = zebedee.users.get(builder.publisher1.email);
        assertTrue(user.keyring.unlock(credentials.password));
        assertEquals(1, user.keyring().size());

    }

    @Test
    public void publisherKeyring_whenPasswordResetByAdmin_receivesNewPublicKey() throws ZebedeeException, IOException {
        // Given
        // admin A and details for publisher B
        Session sessionA = zebedee.openSession(builder.administratorCredentials);
        PublicKey initialPublicKey = builder.publisher2.keyring.getPublicKey();

        // When
        // admin A resets password
        Credentials credentials = builder.publisher2Credentials;
        credentials.password = "Adam Bob Charlie Danny";
        zebedee.users.setPassword(sessionA, credentials);

        // Then
        // publisher B gets a new public key
        PublicKey secondPublicKey = zebedee.users.get(builder.publisher2.email).keyring.getPublicKey();
        assertNotEquals(initialPublicKey.toString(), secondPublicKey.toString());
    }

    @Test
    public void publisherKeyring_whenPasswordResetBySelf_reencryptsKey() throws ZebedeeException, IOException {
        // Given
        // publisher A
        Session sessionA = zebedee.openSession(builder.publisher1Credentials);
        String oldPassword = builder.publisher1Credentials.password;
        assertTrue(builder.publisher1.keyring().unlock(oldPassword));

        // When
        // A resets own password
        Credentials credentials = builder.publisher1Credentials;
        credentials.oldPassword = credentials.password;
        credentials.password = "Adam Bob Charlie Danny";
        zebedee.users.setPassword(sessionA, credentials);

        // Then
        // A can unlock their keyring with the new password and not the old
        User reloaded =  zebedee.users.get(builder.publisher1.email);
        assertTrue(reloaded.keyring.unlock(credentials.password));
        assertFalse(reloaded.keyring.unlock(oldPassword));
    }

    @Test
    public void assignKeyToUser_givenUserWithoutKeyring_doesNothing() throws IOException, ZebedeeException {
        // Given
        // a publisher user without a key
        Session session = zebedee.openSession(builder.administratorCredentials);
        User user = Serialiser.deserialise("{\"name\":\"Alison Davies\",\"email\":\"a.davies@ons.gov.uk\",\"passwordHash\":\"VewEkE+p3X4zuLQP6fMBkhrPgY99y2ajXwWfTAYifH71CfROf3I8XU/K0Ps0dakJ\"}", User.class);
        zebedee.users.create(user, builder.administrator.email);
        zebedee.permissions.addEditor(user.email, session);

        // When
        // we publish a collection
        CollectionDescription collectionDescription = new CollectionDescription();
        collectionDescription.name = this.getClass().getSimpleName() + "-" + Random.id();
        Collection.create(collectionDescription, zebedee, session);

        // Then
        // they dont get a key
        user = zebedee.users.get(user.email);
        assertNull(user.keyring);
    }

    @Test
    public void publisherKeyring_onCreation_receivesAllCollections() throws ZebedeeException, IOException {
        // Given
        // An administrator and a collection
        Session sessionA = zebedee.openSession(builder.administratorCredentials);
        publishCollection(sessionA);
        assertEquals(1, zebedee.users.get(builder.administrator.email).keyring().size());

        // When
        // a new user is created and assigned Publisher permissions
        User test = new User();
        test.name = "Test User";
        test.email = Random.id() + "@example.com";
        test.inactive = false;
        zebedee.users.create(sessionA, test);

        Credentials credentials = new Credentials();
        credentials.email = test.email;
        credentials.password = "password";
        zebedee.users.setPassword(sessionA, credentials);

        zebedee.permissions.addEditor(test.email, sessionA);

        // Then
        // publisher A retains keys
        User user = zebedee.users.get(test.email);
        assertTrue(user.keyring.unlock("password"));
        assertEquals(1, user.keyring().size());

    }

    @Test
    public void schedulerKeyring_whenUserLogsIn_populates() throws IOException, ZebedeeException {
        // Given
        // an instance of zebedee with two collections but an empty scheduler key cache
        // (this simulates when zebedee restarts)
        Session sessionA = zebedee.openSession(builder.administratorCredentials);
        publishCollection(sessionA);
        publishCollection(sessionA);
        zebedee.keyringCache.schedulerCache = new ConcurrentHashMap<>();
        assertEquals(0, zebedee.keyringCache.schedulerCache.size());

        // When
        // a publisher signs in
        Session sessionB = zebedee.openSession(builder.publisher1Credentials);

        // Then
        // the key cache recovers the secret keys
        assertEquals(2, zebedee.keyringCache.schedulerCache.size());

    }

    @Test
    public void schedulerKeyring_whenCollectionCreated_getsSecretKey() throws IOException, ZebedeeException {
        // Given
        // a user that can create publications
        Session sessionA = zebedee.openSession(builder.administratorCredentials);
        assertEquals(0, zebedee.keyringCache.schedulerCache.size());

        // When
        // they create a couple of collections
        publishCollection(sessionA);
        publishCollection(sessionA);

        // Then
        // keys are added to the schedulerCache keyring
        assertEquals(2, zebedee.keyringCache.schedulerCache.size());

    }
}