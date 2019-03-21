package com.github.onsdigital.zebedee.reader.configuration;

import com.github.onsdigital.zebedee.util.URIUtils;

import static com.github.onsdigital.zebedee.logging.ReaderLogger.info;
import static com.github.onsdigital.zebedee.util.VariableUtils.getVariableValue;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;

/**
 * Content reader configuration
 */
public class ReaderConfiguration {

    private final static String ZEBEDEE_ROOT_ENV = "zebedee_root";
    private final static String CONTENT_DIR_ENV = "content_dir";
    /*Zebedee folder layout*/
    private static final String IN_PROGRESS_FOLDER_NAME = "inprogress";
    private static final String COMPLETE_FOLDER_NAME = "complete";
    private static final String REVIEWED_FOLDER_NAME = "reviewed";
    private static final String COLLECTIONS_FOLDER_NAME = "collections";
    private static final String PUBLISHED_FOLDER_NAME = "master";
    private static final String BULLETINS_FOLDER_NAME = "bulletins";
    private static final String ARTICLES_FOLDER_NAME = "articles";
    private static final String COMPENDIUM_FOLDER_NAME = "compendium";

    private static String datasetAPIHost;
    private static String datasetAPIAuthToken;
    private static String datasetAPIServiceToken;

    private static ReaderConfiguration instance;
    private static String collectionsFolder;
    private static String contentDir;

    private ReaderConfiguration() {

    }

    public static ReaderConfiguration getConfiguration() {
        if (instance == null) {
            synchronized (ReaderConfiguration.class) {
                if (instance == null) {
                    init();
                }
            }
        }
        return instance;
    }

    /**
     * Initialize configuration with environment variables
     */
    private static void init() {
        if (instance == null) {
            doInit(null);
            instance = new ReaderConfiguration();
        }
    }

    /**
     * Initialize with given zebedee root dir
     *
     * @param zebedeeRoot
     */
    public static void init(String zebedeeRoot) {
        if (instance == null) {
            doInit(zebedeeRoot);
            instance = new ReaderConfiguration();
        }
    }

    private static void doInit(String zebedeeRoot) {
        String zebedeeRootDir = defaultIfBlank(zebedeeRoot, getVariableValue(ZEBEDEE_ROOT_ENV));
        String contentDirValue = getVariableValue(CONTENT_DIR_ENV);

        datasetAPIHost = "http://localhost:22000";
        datasetAPIAuthToken = "FD0108EA-825D-411C-9B1D-41EF7727F465";
        datasetAPIServiceToken = "Bearer fc4089e2e12937861377629b0cd96cf79298a4c5d329a2ebb96664c88df77b67";

        /*Zebedee Root takes precedence over content dir*/
        if (zebedeeRootDir != null) {
            zebedeeRootDir = URIUtils.removeTrailingSlash(zebedeeRootDir);
            collectionsFolder = zebedeeRootDir + "/zebedee/" + COLLECTIONS_FOLDER_NAME;
            contentDir = zebedeeRootDir + "/zebedee/" + PUBLISHED_FOLDER_NAME;
        } else if (contentDirValue != null) {
            contentDir = URIUtils.removeTrailingSlash(contentDirValue) + "/";
        } else {
            //todo:can not prevent server startup if error just yet, need startup order for Restolino
            System.err.println("Please set either zebedee_root or content_dir");
        }

        dumpConfiguration();

    }

    /**
     * Prints configuration into console
     */
    public static void dumpConfiguration() {
        info().data("collections_dir", collectionsFolder)
                .data("published_content_dir", contentDir)
                .log("Zebedee reader configuration");
    }

    /**
     * Returns collections folder under zebedee root
     *
     * @return
     */
    public String getCollectionsFolder() {
        return collectionsFolder;
    }

    public String getContentDir() {
        return contentDir;
    }

    public String getInProgressFolderName() {
        return IN_PROGRESS_FOLDER_NAME;
    }

    public String getCompleteFolderName() {
        return COMPLETE_FOLDER_NAME;
    }

    public String getReviewedFolderName() {
        return REVIEWED_FOLDER_NAME;
    }

    public String getBulletinsFolderName() {
        return BULLETINS_FOLDER_NAME;
    }

    public String getArticlesFolderName() {
        return ARTICLES_FOLDER_NAME;
    }

    public String getCompendiumFolderName() {
        return COMPENDIUM_FOLDER_NAME;
    }

    public static String getDatasetAPIHost() {
        return datasetAPIHost;
    }

    public static String getDatasetAPIAuthToken() {
        return datasetAPIAuthToken;
    }

    public static String getDatasetAPIServiceToken() {
        return datasetAPIServiceToken;
    }
}
