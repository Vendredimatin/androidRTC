package fr.pchab.androidrtc;

import android.content.Context;
import android.util.Log;

import com.loopj.android.http.*;

import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import cz.msebera.android.httpclient.HttpEntity;
import cz.msebera.android.httpclient.entity.StringEntity;

public class RestClient {
    //private static final String BASE_URL = "http://10.157.12.122:5001/";
    private static final String BASE_URL = "http://10.150.139.20:5006/";

    private static AsyncHttpClient client = new AsyncHttpClient();

    private static SyncHttpClient syncHttpClient = new SyncHttpClient();

    public static void post(Context context, String url, JSONObject obj, AsyncHttpResponseHandler responseHandler) {
        StringEntity entity = new StringEntity(obj.toString(), Charset.forName("UTF-8"));
        client.post(context, getAbsoluteUrl(url), entity, "application/json", responseHandler);
    }

    public static void get(Context context, String url, AsyncHttpResponseHandler responseHandler) {
        client.get(context, getAbsoluteUrl(url), responseHandler);
    }

    private static String getAbsoluteUrl(String relativeUrl) {
        return BASE_URL + relativeUrl;
    }

}

