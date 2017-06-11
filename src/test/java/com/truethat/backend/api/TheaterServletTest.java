package com.truethat.backend.api;

import com.google.appengine.api.datastore.DatastoreService;
import com.google.appengine.api.datastore.DatastoreServiceFactory;
import com.google.appengine.api.datastore.Entity;
import com.google.appengine.tools.development.testing.LocalDatastoreServiceTestConfig;
import com.google.appengine.tools.development.testing.LocalServiceTestHelper;
import com.google.gson.reflect.TypeToken;
import com.truethat.backend.common.Util;
import com.truethat.backend.model.Scene;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Proudly created by ohad on 01/06/2017.
 */
public class TheaterServletTest {
    private static final LocalServiceTestHelper HELPER           =
            new LocalServiceTestHelper(new LocalDatastoreServiceTestConfig());
    private static final long                   DIRECTOR_ID      = 10;
    private static final Date                   CREATED          = new Date();
    private static final String                 IMAGE_SIGNED_URL = "viruses.com";
    private static DatastoreService    datastoreService;
    @Mock
    private        HttpServletRequest  mockRequest;
    @Mock
    private        HttpServletResponse mockResponse;
    private        StringWriter        responseWriter;
    private        TheaterServlet      theaterServlet;


    /**
     * Starts the local Datastore emulator.
     */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        HELPER.setUp();
        datastoreService = DatastoreServiceFactory.getDatastoreService();
        theaterServlet = new TheaterServlet();

        responseWriter = new StringWriter();
        when(mockResponse.getWriter()).thenReturn(new PrintWriter(responseWriter));

    }


    /**
     * Stops the local Datastore emulator.
     */
    @After
    public void tearDown() throws Exception {
        HELPER.tearDown();
    }

    @Test
    public void doGet() throws Exception {
        // Add a scene to datastore.
        Entity entity = new Entity(Scene.DATASTORE_KIND);
        entity.setProperty(Scene.DATASTORE_CREATED, CREATED);
        entity.setProperty(Scene.DATASTORE_DIRECTOR_ID, DIRECTOR_ID);
        entity.setProperty(Scene.DATASTORE_IMAGE_SIGNED_URL, IMAGE_SIGNED_URL);
        datastoreService.put(entity);
        // Created a new scene from the entity
        Scene scene = new Scene(entity);
        // Sends the GET request
        theaterServlet.doGet(mockRequest, mockResponse);
        String response = responseWriter.toString();
        List<Scene> scenes = Util.GSON.fromJson(response, new TypeToken<List<Scene>>() {
        }.getType());
        assertEquals(1, scenes.size());
        assertEquals(scene, scenes.get(0));
    }

    @Test
    public void doGet_multipleScenes() throws Exception {
        // Add 11 scenes to datastore.
        for (int i = 0; i < TheaterServlet.SCENES_LIMIT + 1; i++) {
            Entity entity = new Entity(Scene.DATASTORE_KIND);
            entity.setProperty(Scene.DATASTORE_CREATED, new Date(CREATED.getTime() + i));
            entity.setProperty(Scene.DATASTORE_DIRECTOR_ID, DIRECTOR_ID);
            entity.setProperty(Scene.DATASTORE_IMAGE_SIGNED_URL, IMAGE_SIGNED_URL);
            datastoreService.put(entity);
        }
        // Sends the GET request
        theaterServlet.doGet(mockRequest, mockResponse);
        String response = responseWriter.toString();
        List<Scene> scenes = Util.GSON.fromJson(response, new TypeToken<List<Scene>>() {
        }.getType());
        // Asserts no more than TheaterServlet.SCENES_LIMIT are responded.
        assertEquals(TheaterServlet.SCENES_LIMIT, scenes.size());
        // Asserts the scenes are sorted by recency.
        for (int i = TheaterServlet.SCENES_LIMIT; i > 0; i--) {
            assertEquals(new Date(CREATED.getTime() + i), scenes.get(TheaterServlet.SCENES_LIMIT - i).getCreated());
        }
    }
}