package com.github.onsdigital.zebedee.keyring.store;

import com.github.onsdigital.zebedee.keyring.CollectionKey;
import com.github.onsdigital.zebedee.keyring.KeyringException;
import com.google.gson.GsonBuilder;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Base64;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertTrue;

public class CollectionKeyStoreImplTest {

    static final String TEST_COLLECTION_ID = "1234567890";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private CollectionKeyStore keyStore;
    private File keyringDir;

    @Before
    public void setUp() throws Exception {
        keyringDir = folder.newFolder("keyring");
    }

    @Test(expected = KeyringException.class)
    public void read_shouldThrowException_ifCollectionIDNull() throws Exception {
        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), null, null);

        CollectionKey key = null;
        try {
            key = keyStore.read(null);
        } catch (KeyringException ex) {
            assertThat(key, is(nullValue()));
            assertThat(ex.getMessage(), equalTo("collectionID required but was null or empty"));
            throw ex;
        }
    }

    @Test(expected = KeyringException.class)
    public void read_shouldThrowException_ifCollectionIDEmpty() throws Exception {
        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), null, null);

        CollectionKey key = null;
        try {
            key = keyStore.read("");
        } catch (KeyringException ex) {
            assertThat(key, is(nullValue()));
            assertThat(ex.getMessage(), equalTo("collectionID required but was null or empty"));
            throw ex;
        }
    }

    @Test(expected = KeyringException.class)
    public void read_shouldThrowException_ifKeyNotFound() throws Exception {
        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), null, null);

        CollectionKey key = null;
        try {
            key = keyStore.read(TEST_COLLECTION_ID);
        } catch (KeyringException ex) {
            assertThat(key, is(nullValue()));
            assertThat(ex.getMessage(), equalTo("collectionKey not found"));
            assertThat(ex.getCollectionID(), equalTo(TEST_COLLECTION_ID));
            throw ex;
        }
    }

    @Test(expected = KeyringException.class)
    public void testRead_shouldThrowException_ifFileInvalid() throws Exception {
        SecretKey masterKey = createNewSecretKey();
        IvParameterSpec iv = createNewInitVector();

        SecretKey wrappedKey = createNewSecretKey();
        createPlainTextCollectionKeyFile(TEST_COLLECTION_ID, wrappedKey);

        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), masterKey, iv);

        CollectionKey actual = null;

        try {
            actual = keyStore.read(TEST_COLLECTION_ID);
        } catch (KeyringException ex) {
            assertThat(actual, is(nullValue()));
            assertThat(ex.getMessage(), equalTo("error while decrypting collectionKey file"));
            assertThat(ex.getCollectionID(), equalTo(TEST_COLLECTION_ID));
            throw ex;
        }
    }

    @Test
    public void testRead_Success() throws Exception {
        // Create the key store
        SecretKey masterKey = createNewSecretKey();
        IvParameterSpec masterIV = createNewInitVector();
        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), masterKey, masterIV);

        // Create a collection key to add to the keystore.
        IvParameterSpec initVector = createNewInitVector();
        CollectionKey collectionKey = new CollectionKey(TEST_COLLECTION_ID, createNewSecretKey());

        // Encrypt a test message using the collection key.
        String plainText = "Blackened is the end, Winter it will send, Throwing all you see Into obscurity";
        byte[] encryptedMessage = encyrpt(plainText, collectionKey.getSecretKey(), initVector);

        // write the key to the store.
        keyStore.write(collectionKey);
        assertTrue(Files.exists(keyringDir.toPath().resolve(TEST_COLLECTION_ID + ".json")));

        // retieve the key from the store.
        CollectionKey actual = keyStore.read(TEST_COLLECTION_ID);
        assertThat(actual.getCollectionID(), equalTo(TEST_COLLECTION_ID));

        // attempt to decrypt the test message using the key retrieve from the store - if its working as expected the
        // decrypted message should equal the original input.
        byte[] decryptedBytes = decrypt(encryptedMessage, collectionKey.getSecretKey(), initVector);
        String decryptedMessage = new String(decryptedBytes);
        assertThat(decryptedMessage, equalTo(plainText));
    }

    @Test(expected = KeyringException.class)
    public void testWrite_shouldThrowException_ifCollectionKeyIsNull() throws Exception {
        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), null, null);

        try {
            keyStore.write(null);
        } catch (KeyringException ex) {
            assertThat(ex.getMessage(), equalTo("collectionKey required but was null"));
            assertThat(ex.getCollectionID(), is(nullValue()));
            throw ex;
        }
    }

    @Test(expected = KeyringException.class)
    public void testWrite_shouldThrowException_ifCollectionKeyIDIsNull() throws Exception {
        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), null, null);

        try {
            keyStore.write(new CollectionKey(null, null));
        } catch (KeyringException ex) {
            assertThat(ex.getMessage(), equalTo("collectionKey.ID required but was null or empty"));
            assertThat(ex.getCollectionID(), is(nullValue()));
            throw ex;
        }
    }

    @Test(expected = KeyringException.class)
    public void testWrite_shouldThrowException_ifCollectionKeySecretKeyIsNull() throws Exception {
        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), null, null);

        try {
            keyStore.write(new CollectionKey(TEST_COLLECTION_ID, null));
        } catch (KeyringException ex) {
            assertThat(ex.getMessage(), equalTo("collectionKey.secretKey required but was null"));
            assertThat(ex.getCollectionID(), equalTo(TEST_COLLECTION_ID));
            throw ex;
        }
    }

    @Test
    public void testWrite_success_shouldWriteKeyToEncryptedFile() throws Exception {
        // Create the key store
        SecretKey masterKey = createNewSecretKey();
        IvParameterSpec masterIV = createNewInitVector();
        keyStore = new CollectionKeyStoreImpl(keyringDir.toPath(), masterKey, masterIV);

        // Create a collection key and add to the keystore.
        CollectionKey input = new CollectionKey(TEST_COLLECTION_ID, createNewSecretKey());
        keyStore.write(input);

        // check the file exists
        File f = keyringDir.toPath().resolve(TEST_COLLECTION_ID + ".json").toFile();
        assertTrue(Files.exists(f.toPath()));

        // Attempt to decrypt the file - if successful we can assume the encryption was also successful.
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, masterKey, masterIV);

        CollectionKey result = null;
        try (
                FileInputStream fin = new FileInputStream(f);
                CipherInputStream cin = new CipherInputStream(fin, cipher);
                InputStreamReader reader = new InputStreamReader(cin)
        ) {
            result = new GsonBuilder()
                    .registerTypeAdapter(CollectionKey.class, new CollectionKeySerializer())
                    .create()
                    .fromJson(reader, CollectionKey.class);
        }

        // Verify the Collection key we created from decrypting the file and marshalling into the target object type
        // matches the origin input object.
        assertThat(result, equalTo(input));
    }

    CollectionKey createPlainTextCollectionKeyFile(String collectionID, SecretKey secretKey) throws Exception {
        CollectionKey key = new CollectionKey(collectionID, secretKey);
        byte[] json = new GsonBuilder()
                .registerTypeAdapter(CollectionKey.class, new CollectionKeySerializer())
                .setPrettyPrinting()
                .create().toJson(key).getBytes();

        Path p = keyringDir.toPath().resolve(collectionID + ".json");

        FileUtils.writeByteArrayToFile(p.toFile(), json);

        return key;
    }

    byte[] encyrpt(String plainText, SecretKey key, IvParameterSpec iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);

        return Base64.getEncoder().encode(cipher.doFinal(plainText.getBytes("UTF-8")));
    }

    byte[] decrypt(byte[] encryptedMessage, SecretKey key, IvParameterSpec iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, iv);

        byte[] bytes = Base64.getDecoder().decode(encryptedMessage);
        return cipher.doFinal(bytes);
    }

    SecretKey createNewSecretKey() throws Exception {
        return KeyGenerator.getInstance("AES").generateKey();
    }

    IvParameterSpec createNewInitVector() {
        byte[] iv = new byte[16];

        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);

        return new IvParameterSpec(iv);
    }
}
