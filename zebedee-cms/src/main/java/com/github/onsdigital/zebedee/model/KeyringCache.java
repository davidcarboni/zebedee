package com.github.onsdigital.zebedee.model;

import com.github.onsdigital.zebedee.json.Keyring;
import com.github.onsdigital.zebedee.session.model.Session;
import com.github.onsdigital.zebedee.session.service.Sessions;
import com.github.onsdigital.zebedee.user.model.User;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.github.onsdigital.zebedee.logging.CMSLogEvent.info;

/**
 * Provides an basic in-memory cache for {@link Keyring} instances.
 */
@Deprecated
public class KeyringCache {

    // Publisher keyring keeps all available secret keys available
    private com.github.onsdigital.zebedee.keyring.KeyringCache schedulerCache;
    private Map<Session, Keyring> keyringMap;
    private Sessions sessions;

    @Deprecated
    public KeyringCache(Sessions sessions, com.github.onsdigital.zebedee.keyring.KeyringCache schedulerCache) {
        this.sessions = sessions;
        this.schedulerCache = schedulerCache;
        this.keyringMap = new ConcurrentHashMap<>();
    }

    /**
     * Stores the specified user's keyring, if unlocked, in the cache.
     *
     * @param user The user whose {@link Keyring} is to be stored.
     * @throws IOException If a general error occurs.
     */
    @Deprecated
    public void put(User user, Session session) throws IOException {
        if (user != null && user.keyring() != null && user.keyring().isUnlocked()) {
            if (session != null) {
                // add the keyring by session
                keyringMap.put(session, user.keyring());

                // populate the scheduler keyring
                List<String> cached = new ArrayList<>();
                for (String collectionId : user.keyring().list()) {
                    if (schedulerCache.get(collectionId) == null) {
                        cached.add(collectionId);
                        schedulerCache.add(collectionId, user.keyring().get(collectionId));
                    }
                }
                if (!cached.isEmpty()) {
                    info().data("collections", cached).log("added collections to publish scheduler cache");
                }
            }
        }
    }

    /**
     * Gets the specified user's keyring, if present in the cache.
     *
     * @param user The user whose {@link Keyring} is to be retrieved.
     * @return The {@link Keyring} if present, or null.
     * @throws IOException If a general error occurs.
     */
    @Deprecated
    public Keyring get(User user) throws IOException {
        Keyring result = null;

        if (user != null) {
            Session session = sessions.find(user.getEmail());
            if (session != null) {
                result = keyringMap.get(session);
            }
        }

        return result;
    }

    /**
     * Gets the specified session's keyring, if present in the cache.
     *
     * @param session The session whose {@link Keyring} is to be retrieved.
     * @return The {@link Keyring} if present, or null.
     * @throws IOException If a general error occurs.
     */
    @Deprecated
    public Keyring get(Session session) throws IOException {
        Keyring result = null;

        if (session != null) {
            result = keyringMap.get(session);
        }
        return result;
    }

    /**
     * Removes a keyring, if present, from the cache, based on an expired session.
     *
     * @param session The expired {@link Session} for which the {@link Keyring} is to be removed.
     * @throws IOException If a general error occurs.
     */
    @Deprecated
    public void remove(Session session) throws IOException {
        if (session != null) {
            keyringMap.remove(session);
        }
    }
}
