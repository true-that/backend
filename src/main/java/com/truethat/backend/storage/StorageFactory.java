package com.truethat.backend.storage;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.StorageScopes;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;

/**
 * This class manages the details of creating a Storage service, including auth.
 * <p>
 * Taken from:
 * https://github.com/GoogleCloudPlatform/java-docs-samples/blob/master/storage/json-api/src/main/java/StorageFactory.java
 */
class StorageFactory {
    private static Storage instance = null;

    static synchronized Storage getService() throws IOException, GeneralSecurityException {
        if (instance == null) {
            instance = buildService();
        }
        return instance;
    }

    private static Storage buildService() throws IOException, GeneralSecurityException {
        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = new JacksonFactory();
        GoogleCredential credential = GoogleCredential.getApplicationDefault(transport, jsonFactory);

        if (credential.createScopedRequired()) {
            Collection<String> scopes = StorageScopes.all();
            credential = credential.createScoped(scopes);
        }

        return new Storage.Builder(transport, jsonFactory, credential)
                .setApplicationName(System.getenv("APPLICATION_NAME"))
                .build();
    }
}