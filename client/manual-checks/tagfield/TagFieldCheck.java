/*
 * Manual verification for the Administrator's channel tag field (IRT-2431).
 *
 * This is deliberately NOT a JUnit test and is not wired into any ant target. The tag field is
 * a JavaFX WebView, so it needs a real display and a JavaFX-bearing JRE; CI runners have
 * neither a display nor xvfb, and anything under client/test matching *Test.class is collected
 * and run automatically. See README.md for why this matters and how to run it.
 *
 * It drives the real MirthTagWebBrowser rather than a copy of its logic, so it fails both when
 * a JavaFX update breaks resource loading and when someone reintroduces the loadContent()
 * approach this check exists to guard against.
 */

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;

import com.mirth.connect.client.ui.components.tag.AutoCompletionPopupWindow;
import com.mirth.connect.client.ui.components.tag.MirthTagWebBrowser;

public class TagFieldCheck {

    private static final int TIMEOUT_SECONDS = 60;

    public static void main(String[] args) throws Exception {
        System.out.println("java.version           = " + System.getProperty("java.version"));
        System.out.println("java.vendor            = " + System.getProperty("java.vendor"));
        System.out.println("javafx.version         = " + javafxVersion());

        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_SECONDS * 1000L);
            } catch (InterruptedException ignored) {
            }
            System.out.println("RESULT: TIMEOUT - the page never finished loading");
            Runtime.getRuntime().halt(3);
        });
        watchdog.setDaemon(true);
        watchdog.start();

        JFXPanel panel = new JFXPanel();

        Platform.runLater(() -> {
            try {
                check(panel);
            } catch (Throwable t) {
                System.out.println("RESULT: FAIL - could not construct MirthTagWebBrowser");
                t.printStackTrace(System.out);
                Runtime.getRuntime().halt(1);
            }
        });

        Thread.sleep((TIMEOUT_SECONDS + 30) * 1000L);
    }

    /** Mirrors MirthTagField.initFX: build the browser, put it in a scene, hand it to Swing. */
    private static void check(JFXPanel panel) throws Exception {
        List<Map<String, String>> userTags = new ArrayList<Map<String, String>>();

        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put("background", "#c0c0c0");
        attributes.put("color", "#000000");
        attributes.put("type", "TAG");
        Map<String, Map<String, String>> attributeMap = new HashMap<String, Map<String, String>>();
        attributeMap.put("check-tag", attributes);

        AutoCompletionPopupWindow popupWindow = new AutoCompletionPopupWindow();
        MirthTagWebBrowser browser = new MirthTagWebBrowser(popupWindow, userTags, attributeMap, false);

        Group root = new Group();
        Scene scene = new Scene(root);
        root.getChildren().add(browser);
        panel.setScene(scene);

        Field field = MirthTagWebBrowser.class.getDeclaredField("webEngine");
        field.setAccessible(true);
        final WebEngine engine = (WebEngine) field.get(browser);

        engine.getLoadWorker().stateProperty().addListener((ov, oldState, newState) -> {
            if (newState == State.FAILED) {
                System.out.println("RESULT: FAIL - load worker failed: "
                        + engine.getLoadWorker().getException());
                Runtime.getRuntime().halt(2);
            }
            if (newState == State.SUCCEEDED) {
                assertPageUsable(engine);
            }
        });
    }

    private static void assertPageUsable(WebEngine engine) {
        try {
            System.out.println("userAgent              = " + engine.executeScript("navigator.userAgent"));

            /*
             * 4 on a working runtime: three external stylesheets plus the page's inline
             * <style> block. 1 means the external resources were refused and only the inline
             * block survived, which is the IRT-2431 failure.
             */
            /*
             * Every expression here reads through window rather than naming a bare global. A
             * bare "$" throws ReferenceError when the scripts did not load, which would lose
             * the diagnostics in exactly the case this check exists to report.
             */
            Object sheets = engine.executeScript("document.styleSheets.length");
            Object jquery = engine.executeScript("typeof window.jQuery");
            Object tokenfield = engine.executeScript(
                    "typeof (window.jQuery && window.jQuery.fn && window.jQuery.fn.tokenfield)");
            Object updateTags = engine.executeScript("typeof window.updateTags");

            System.out.println("document.styleSheets   = " + sheets + " (expected 4)");
            System.out.println("typeof jQuery          = " + jquery);
            System.out.println("typeof $.fn.tokenfield = " + tokenfield);
            System.out.println("typeof updateTags      = " + updateTags);

            boolean ok = "function".equals(jquery) && "function".equals(tokenfield)
                    && "function".equals(updateTags);

            if (ok) {
                System.out.println("RESULT: PASS - the tag field's scripts and styles loaded");
                Runtime.getRuntime().halt(0);
            }

            System.out.println("RESULT: FAIL - the page loaded but its scripts did not."
                    + " The tag field will be inert in the Administrator.");
            Runtime.getRuntime().halt(2);
        } catch (Throwable t) {
            System.out.println("RESULT: FAIL - could not evaluate the loaded page");
            t.printStackTrace(System.out);
            Runtime.getRuntime().halt(2);
        }
    }

    private static String javafxVersion() {
        String version = System.getProperty("javafx.runtime.version");
        return version != null ? version : "(not reported by this runtime)";
    }
}
