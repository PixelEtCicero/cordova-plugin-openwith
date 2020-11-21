package cc.fovea.openwith;

import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Handle serialization of Android objects ready to be sent to javascript.
 */
class Serializer
{
    private OpenWithPlugin plugin;
    private Context context;
    private ContentResolver contentResolver;

    /**
     * Constructor.
     */
    public Serializer(OpenWithPlugin plugin)
    {
        this.plugin = plugin;
        this.context = plugin.cordova.getActivity().getApplicationContext();
        this.contentResolver = this.context.getContentResolver();
    }

    /**
     * Convert an intent to JSON.
     *
     * This actually only exports stuff necessary to see file content
     * (streams or clip data) sent with the intent.
     * If none are specified, null is return.
     */
    public JSONObject toJSONObject(
            final Intent intent)
            throws JSONException {
        JSONArray items = null;

        this.plugin.debug("[Serializer] toJSONObject");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            items = itemsFromClipData(intent.getClipData());
        }
        if (items == null || items.length() == 0) {
            items = itemsFromExtras(intent.getExtras());
        }
        if (items == null || items.length() == 0) {
            items = itemsFromData(intent.getData());
        }
        if (items == null) {
            this.plugin.debug("[Serializer] toJSONObject: null");

            return null;
        }
        final JSONObject action = new JSONObject();
        action.put("action", translateAction(intent.getAction()));
        action.put("exit", readExitOnSent(intent.getExtras()));
        action.put("items", items);
        return action;
    }

    public String translateAction(final String action)
    {
        if ("android.intent.action.SEND".equals(action) || "android.intent.action.SEND_MULTIPLE".equals(action)) {
            return "SEND";
        }
        else if ("android.intent.action.VIEW".equals(action)) {
            return "VIEW";
        }

        return action;
    }

    /**
     * Read the value of "exit_on_sent" in the intent's extra.
     *
     * Defaults to false.
     */
    public boolean readExitOnSent(final Bundle extras)
    {
        if (extras == null) {
            return false;
        }

        return extras.getBoolean("exit_on_sent", false);
    }

    /**
     * Extract the list of items from clip data (if available).
     *
     * Defaults to null.
     */
    public JSONArray itemsFromClipData(
            final ClipData clipData
    ) throws JSONException {
        if (clipData != null) {
            final int clipItemCount = clipData.getItemCount();

            this.plugin.debug("[Serializer] itemsFromClipData: found " + Integer.toString(clipItemCount) + " items");

            JSONObject[] items = new JSONObject[clipItemCount];
            for (int i = 0; i < clipItemCount; i++) {
                items[i] = toJSONObject(clipData.getItemAt(i).getUri());
            }
            return new JSONArray(items);
        }

        this.plugin.debug("[Serializer] itemsFromClipData: null");

        return null;
    }

    /** Extract the list of items from the intent's extra stream.
     *
     * See Intent.EXTRA_STREAM for details. */
    public JSONArray itemsFromExtras(
            final Bundle extras) throws JSONException
    {
        if (extras != null) {
            this.plugin.debug("[Serializer] itemsFromExtras");

            final JSONObject item = toJSONObject((Uri) extras.get(Intent.EXTRA_STREAM));

            if (item != null) {
                final JSONObject[] items = new JSONObject[1];
                items[0] = item;
                return new JSONArray(items);
            }
        }

        this.plugin.debug("[Serializer] itemsFromExtras: null");

        return null;
    }

    /** Extract the list of items from the intent's getData
     *
     * See Intent.ACTION_VIEW for details. */
    public JSONArray itemsFromData(
            final Uri uri) throws JSONException
    {
        if (uri != null) {
            this.plugin.debug("[Serializer] itemsFromData: uri" + uri.toString());

            final JSONObject item = toJSONObject(uri);

            if (item != null) {
                final JSONObject[] items = new JSONObject[1];
                items[0] = item;
                return new JSONArray(items);
            }
        }

        this.plugin.debug("[Serializer] itemsFromData: null");

        return null;
    }

    /** Convert an Uri to JSON object.
     *
     * Object will include:
     *    "type" of data;
     *    "uri" itself;
     *    "path" to the file, if applicable.
     *    "data" for the file.
     */
    public JSONObject toJSONObject(
        final Uri uri) throws JSONException
    {
        if (uri != null) {
            final JSONObject json = new JSONObject();
            final String type = this.contentResolver.getType(uri);
            final String path = this.getRealPathFromURI(uri);
            json.put("type", type);
            json.put("uri", uri);
            json.put("path", path);

            this.plugin.debug("[Serializer] toJSONObject: uri: " + uri.toString());
            this.plugin.debug("[Serializer] toJSONObject: type: " + type);
            this.plugin.debug("[Serializer] toJSONObject: path: " + path);

            return json;
        }

        this.plugin.debug("[Serializer] toJSONObject: null");

        return null;
    }

    /**
     * Convert the Uri to the direct file system path of the image file.
     */
    public String getRealPathFromURI(final Uri uri)
    {
        final String[] proj = { MediaStore.Images.Media.DATA };
        final Cursor cursor = this.contentResolver.query(uri, proj, null, null, null);

        if (cursor == null) {
            return "";
        }

        final int column_index = cursor.getColumnIndex(MediaStore.Images.Media.DATA);

        if (column_index < 0) {
            cursor.close();
            return "";
        }

        cursor.moveToFirst();
        final String result = cursor.getString(column_index);
        cursor.close();

        return result;
    }
}
