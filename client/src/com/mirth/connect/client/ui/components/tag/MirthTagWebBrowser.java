/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui.components.tag;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.client.ui.PlatformUI;

import netscape.javascript.JSObject;

public class MirthTagWebBrowser extends Region {

    private WebView webView;
    private WebEngine webEngine;

    private MirthTagWebController webController;

    private AutoCompletionPopupWindow popupWindow;

    /*
     * Written on the JavaFX thread once the page's scripts have initialised, read on the Swing
     * thread when a channel is saved. False means getTags() does not reflect the channel.
     */
    private volatile boolean ready;

    private Logger logger = LogManager.getLogger(this);

    public MirthTagWebBrowser(AutoCompletionPopupWindow popupWindow, List<Map<String, String>> userTags, Map<String, Map<String, String>> attributeMap, boolean channelContext) throws Exception {
        this.popupWindow = popupWindow;

        setHeight(24);
        setPrefWidth(200);

        initComponents(userTags, attributeMap, channelContext);

        getChildren().add(webView);
    }

    private void initComponents(List<Map<String, String>> userTags, Map<String, Map<String, String>> attributeMap, final boolean channelContext) throws Exception {
        webView = new WebView();
        webView.setContextMenuEnabled(false);

        webView.focusedProperty().addListener(new ChangeListener<Boolean>() {
            @Override
            public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                if (!newValue) {
                    setFocus(false);
                }
            }
        });

        webEngine = webView.getEngine();

        final String tagData = convertToJSON(userTags);
        final String attributeData = convertToJSON(attributeMap);
        final String context = convertToJSON(channelContext);

        /*
         * Load the page by its own URL rather than injecting its markup with loadContent(). A
         * loadContent() document has an "about:blank" origin, and WebKit refuses to fetch the
         * page's jar:-scheme stylesheets and scripts from that origin, so jQuery and
         * bootstrap-tokenfield never load and every call into the page fails. Loading by URL
         * gives the document the same origin as its resources (IRT-2431).
         */
        webEngine.load(getClass().getResource("MirthTagField.html").toExternalForm());

        webEngine.getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
            @Override
            public void changed(ObservableValue<? extends State> ov, State oldState, State newState) {
                if (newState == State.FAILED) {
                    /*
                     * Only the page itself failing to load lands here; a missing stylesheet
                     * or script still reaches SUCCEEDED and surfaces as a JSException in the
                     * branch below. Left unlogged, either one is an inert tag field with no
                     * explanation.
                     */
                    logger.error("The tag field page failed to load.",
                            webEngine.getLoadWorker().getException());
                }

                if (newState == State.SUCCEEDED) {
                    try {
                        JSObject init = (JSObject) webEngine.executeScript("window");
                        init.setMember("clickController", webController);
                        init.call("updateTags", attributeData, context);
                        init.call("setUserTags", tagData);
                        ready = true;
                    } catch (Exception e) {
                        /*
                         * Without this the exception is thrown on the JavaFX thread, where
                         * nothing reports it and the tag field silently does nothing.
                         */
                        logger.error("Error initializing the tag field.", e);
                    }
                }
            }
        });

        webController = new MirthTagWebController(popupWindow);
        popupWindow.setWebEngine(webEngine);
    }

    public void updateTags(Map<String, Map<String, String>> tagAtrributeMap, boolean channelContext) {
        doCall("updateTags", convertToJSON(tagAtrributeMap), convertToJSON(channelContext));
    }

    public void setFocus(boolean focus) {
        doCall("setFocus", convertToJSON(focus));
    }

    public void setEnabled(boolean enable) {
        doCall("setEnabled", convertToJSON(enable));
    }

    public void setUserTags(List<Map<String, String>> tags, boolean updateController) {
        doCall("setUserTags", convertToJSON(tags), updateController);
    }

    public void insertTag(String tagName) {
        doCall("insertTag", tagName);
    }

    public String getTags() {
        return webController.getTags();
    }

    /**
     * Whether the page loaded and its scripts initialised, so that getTags() reflects what the
     * user sees. Anything that saves tags must check this first (IRT-2431).
     */
    public boolean isReady() {
        return ready;
    }

    public Map<String, Color> getTagColors() {
        return webController.getTagColors();
    }

    public void clear() {
        setUserTags(new ArrayList<Map<String, String>>(), true);
        webController.setTags("");
    }

    private String convertToJSON(Object object) {
        String jsonData = "";
        try {
            ObjectMapper mapper = new ObjectMapper();
            jsonData = mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e.getCause(), "Error converting to JSON");
        }

        return jsonData;
    }

    private void doCall(final String method, final Object... args) {
        try {
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    try {
                        JSObject tokenField = (JSObject) webEngine.executeScript("window");
                        tokenField.call(method, args);
                    } catch (Exception e) {
                        logger.error("Error calling tokenField JS method: " + method, e);
                    }
                }
            });
        } catch (Exception e) {
            PlatformUI.MIRTH_FRAME.alertThrowable(PlatformUI.MIRTH_FRAME, e.getCause(), "Error in tagfield");
        }
    }
}