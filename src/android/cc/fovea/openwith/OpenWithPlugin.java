package cc.fovea.openwith;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.ArrayList;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This is the entry point of the openwith plugin
 *
 * @author Jean-Christophe Hoelt
 */
public class OpenWithPlugin extends CordovaPlugin
{
    /** How the plugin name shows in logs */
    private final String PLUGIN_NAME = "OpenWithPlugin";

    /** Maximal verbosity, log everything */
    private final int DEBUG = 0;
    /** Default verbosity, log interesting stuff only */
    private final int INFO = 10;
    /** Low verbosity, log only warnings and errors  */
    private final int WARN = 20;
    /** Minimal verbosity, log only errors */
    private final int ERROR = 30;

    /** Current verbosity level, changed with setVerbosity */
    private int verbosity = INFO;
    /** Callback to the javascript onNewFile method */
    private CallbackContext handlerContext;
    /** Callback to the javascript logger method */
    private CallbackContext loggerContext;
    /** Intents added before the handler has been registered */
    private ArrayList pendingIntents = new ArrayList();
    private Serializer serializer;

    /**
     * Called after the plugin is initialized
     */
    protected void pluginInitialize()
    {
        this.serializer = new Serializer(this);
    }

    /** Log to the console if verbosity level is greater or equal to level */
    public void log(final int level, final String message)
    {
        switch(level) {
            case DEBUG: Log.d(PLUGIN_NAME, message); break;
            case INFO: Log.i(PLUGIN_NAME, message); break;
            case WARN: Log.w(PLUGIN_NAME, message); break;
            case ERROR: Log.e(PLUGIN_NAME, message); break;
        }

        if (level >= verbosity && loggerContext != null) {
            final PluginResult result = new PluginResult(
                    PluginResult.Status.OK,
                    String.format("%d:%s", level, message
            ));

            result.setKeepCallback(true);
            loggerContext.sendPluginResult(result);
        }
    }

    /**
     * Debug shortcut
     */
    public void debug(final String message)
    {
        this.log(DEBUG, message);
    }

    /**
     * Called when the WebView does a top-level navigation or refreshes.
     *
     * Plugins should stop any long-running processes and clean up internal state.
     *
     * Does nothing by default.
     */
    @Override
    public void onReset()
    {
        verbosity = INFO;
        handlerContext = null;
        loggerContext = null;
        pendingIntents.clear();
    }

    /**
     * @inheritdoc
     *
     * @param action
     * @param data
     * @param callbackContext
     * @return
     */
    @Override
    public boolean execute(final String action, final JSONArray data, final CallbackContext callbackContext)
    {
        this.debug("execute: called with action:" + action + " and options: " + data);

        if ("setVerbosity".equals(action)) {
            return setVerbosity(data, callbackContext);
        }
        if ("init".equals(action)) {
            return init(data, callbackContext);
        }
        if ("setHandler".equals(action)) {
            return setHandler(data, callbackContext);
        }
        if ("setLogger".equals(action)) {
            return setLogger(data, callbackContext);
        }
        if ("load".equals(action)) {
            return load(data, callbackContext);
        }
        if ("exit".equals(action)) {
            return exit(data, callbackContext);
        }

        this.debug("execute: did not recognize this action: " + action);

        return false;
    }

    public boolean setVerbosity(final JSONArray data, final CallbackContext context)
    {
        this.debug("setVerbosity() " + data);

        if (data.length() != 1) {
            log(WARN, "setVerbosity() -> invalidAction");
            return false;
        }

        try {
            verbosity = data.getInt(0);
            this.debug("setVerbosity() -> ok");
            return PluginResultSender.ok(context);
        }
        catch (JSONException ex) {
            log(WARN, "setVerbosity() -> invalidAction");
            return false;
        }
    }

    // Initialize the plugin
    public boolean init(final JSONArray data, final CallbackContext context)
    {
        this.debug("[OpenWithPlugin] init: " + data);

        if (data.length() != 0) {
            log(WARN, "[OpenWithPlugin] init: invalidAction");
            return false;
        }

        onNewIntent(cordova.getActivity().getIntent());

        this.debug("[OpenWithPlugin] init: ok");

        return PluginResultSender.ok(context);
    }

    // Exit after processing
    public boolean exit(final JSONArray data, final CallbackContext context)
    {
        this.debug("exit() " + data);
        if (data.length() != 0) {
            log(WARN, "exit() -> invalidAction");
            return false;
        }
        cordova.getActivity().moveTaskToBack(true);
        this.debug("exit() -> ok");
        return PluginResultSender.ok(context);
    }

    public boolean setHandler(final JSONArray data, final CallbackContext context)
    {
        this.debug("setHandler() " + data);
        if (data.length() != 0) {
            log(WARN, "setHandler() -> invalidAction");
            return false;
        }
        handlerContext = context;
        this.debug("setHandler() -> ok");
        return PluginResultSender.noResult(context, true);
    }

    public boolean setLogger(final JSONArray data, final CallbackContext context)
    {
        this.debug("setLogger() " + data);
        if (data.length() != 0) {
            log(WARN, "setLogger() -> invalidAction");
            return false;
        }
        loggerContext = context;
        this.debug("setLogger() -> ok");
        return PluginResultSender.noResult(context, true);
    }

    /**
     * Load intent item into a tmp file and return path
     */
    public boolean load(final JSONArray data, final CallbackContext context)
    {
        this.debug("[OpenWithPlugin] load");

        if (data.length() != 1) {
            this.log(WARN, "[OpenWithPlugin] load: invalidAction");

            return false;
        }

        /* Execute in thread */
        OpenWithPlugin plugin = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    final JSONObject fileDescriptor = data.getJSONObject(0);
                    final Uri uri = Uri.parse(fileDescriptor.getString("uri"));
                    final String data = plugin.getTmpPathFromURI(uri);
                    final PluginResult result = new PluginResult(PluginResult.Status.OK, data);

                    plugin.debug("[OpenWithPlugin] load: ok: " + result);

                    context.sendPluginResult(result);
                }
                catch (JSONException exc) {
                    final PluginResult result = new PluginResult(PluginResult.Status.ERROR, exc.getMessage());

                    plugin.debug("[OpenWithPlugin] load: error: " + exc.getMessage());

                    context.sendPluginResult(result);
                }
            }
        });

        return true;
    }

    /**
     * Return a temporary file path
     */
    public String getTmpPathFromURI(final Uri uri)
    {
        final ContentResolver contentResolver = this.cordova.getActivity().getApplicationContext().getContentResolver();

        this.debug("[OpenWithPlugin] getTmpPathFromURI: uri" + uri.toString());

        try {
            File outputDir = this.cordova.getActivity().getApplicationContext().getCacheDir();
            File outputFile = File.createTempFile("openwith", "tmp", outputDir);
            final InputStream input = contentResolver.openInputStream(uri);
            final FileOutputStream output = new FileOutputStream(outputFile);

            byte[] buffer = new byte[4 * 1024];
            int read;

            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }

            output.flush();

            this.debug("[OpenWithPlugin] getTmpPathFromURI: success:" + outputFile.getAbsolutePath());

            return outputFile.getAbsolutePath();
        }
        catch (IOException exc) {
            this.debug("[OpenWithPlugin] getTmpPathFromURI: error:" + exc.getMessage());

            return "";
        }
    }

    /**
     * Return data contained at a given Uri as Base64. Defaults to null.
     */
    public String getDataFromURI(
            final ContentResolver contentResolver,
            final Uri uri
    ) {
        try {
            final InputStream inputStream = contentResolver.openInputStream(uri);
            final byte[] bytes = ByteStreams.toByteArray(inputStream);

            return Base64.encodeToString(bytes, Base64.NO_WRAP);
        }
        catch (IOException e) {
            return "";
        }
    }

    /**
     * This is called when a new intent is sent while the app is already opened.
     *
     * We also call it manually with the cordova application intent when the plugin
     * is initialized (so all intents will be managed by this method).
     */
    @Override
    public void onNewIntent(final Intent intent) {
        this.debug("onNewIntent() " + intent.getAction());

        final JSONObject json = toJSONObject(intent);
        if (json != null) {
            pendingIntents.add(json);
        }
        else {
            this.debug("onNewIntent(): null");
        }

        processPendingIntents();
    }

    /**
     * When the handler is defined, call it with all attached files.
     */
    private void processPendingIntents() {
        this.debug("processPendingIntents()");

        if (handlerContext == null) {
            return;
        }
        for (int i = 0; i < pendingIntents.size(); i++) {
            sendIntentToJavascript((JSONObject) pendingIntents.get(i));
        }
        pendingIntents.clear();
    }

    /** Calls the javascript intent handlers. */
    private void sendIntentToJavascript(final JSONObject intent)
    {
        final PluginResult result = new PluginResult(PluginResult.Status.OK, intent);

        result.setKeepCallback(true);
        handlerContext.sendPluginResult(result);
    }

    /**
     * Converts an intent to JSON
     */
    private JSONObject toJSONObject(final Intent intent)
    {
        debug("toJSONObject");

        try {
            return this.serializer.toJSONObject(intent);
        }
        catch (JSONException e) {
            log(ERROR, "Error converting intent to JSON: " + e.getMessage());
            log(ERROR, Arrays.toString(e.getStackTrace()));

            return null;
        }
    }
}
