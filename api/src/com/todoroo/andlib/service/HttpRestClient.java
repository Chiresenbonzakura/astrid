package com.todoroo.andlib.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.params.ConnManagerPNames;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.util.Log;

/**
 * RestClient allows Android to consume web requests.
 * <p>
 * Portions by Praeda:
 * http://senior.ceng.metu.edu.tr/2009/praeda/2009/01/11/a-simple
 * -restful-client-at-android/
 *
 * @author Tim Su <tim@todoroo.com>
 *
 */
public class HttpRestClient implements RestClient {

    private static final int HTTP_UNAVAILABLE_END = 599;
    private static final int HTTP_UNAVAILABLE_START = 500;
    private static final int HTTP_OK = 200;

    private static final int TIMEOUT_MILLIS = 60000;

    private WeakReference<HttpClient> httpClient = null;

    protected boolean debug = false;
    private int timeout = TIMEOUT_MILLIS;

    public HttpRestClient() {
        DependencyInjectionService.getInstance().inject(this);
    }

    public HttpRestClient(int timeout) {
        super();
        this.timeout = timeout;
    }

    private static String convertStreamToString(InputStream is) {
        /*
         * To convert the InputStream to String we use the
         * BufferedReader.readLine() method. We iterate until the BufferedReader
         * return null which means there's no more data to read. Each line will
         * appended to a StringBuilder and returned as String.
         */
        BufferedReader reader = new BufferedReader(new InputStreamReader(is), 16384);
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n"); //$NON-NLS-1$
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return sb.toString();
    }

    @SuppressWarnings("nls")
    private synchronized HttpClient getClient() {
        if (httpClient == null || httpClient.get() == null) {
            SchemeRegistry schemeRegistry = new SchemeRegistry();
            schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            schemeRegistry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));

            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(params, timeout);
            HttpConnectionParams.setSoTimeout(params, timeout);
            params.setParameter(ConnManagerPNames.MAX_TOTAL_CONNECTIONS, 30);
            params.setParameter(ConnManagerPNames.MAX_CONNECTIONS_PER_ROUTE, new ConnPerRouteBean(30));
            params.setParameter(HttpProtocolParams.USE_EXPECT_CONTINUE, false);
            HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);

            ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(params, schemeRegistry);
            HttpClient client = new DefaultHttpClient(cm, params);
            httpClient = new WeakReference<HttpClient>(client);
            return client;
        } else {
            return httpClient.get();
        }
    }

    private String processHttpResponse(HttpResponse response) throws IOException {
        int statusCode = response.getStatusLine().getStatusCode();
        if(statusCode >= HTTP_UNAVAILABLE_START && statusCode <= HTTP_UNAVAILABLE_END) {
            throw new HttpUnavailableException();
        } else if(statusCode != HTTP_OK) {
            throw new HttpErrorException(response.getStatusLine().getStatusCode(),
                    response.getStatusLine().getReasonPhrase());
        }

        HttpEntity entity = response.getEntity();

        if (entity != null) {
            InputStream contentStream = entity.getContent();
            try {
                return convertStreamToString(contentStream);
            } finally {
                contentStream.close();
            }
        }

        return null;
    }

    /**
     * Issue an HTTP GET for the given URL, return the response
     *
     * @param url url with url-encoded params
     * @return response, or null if there was no response
     * @throws IOException
     */
    public synchronized String get(String url) throws IOException {
        if(debug)
            Log.d("http-rest-client-get", url); //$NON-NLS-1$

        try {
            HttpGet httpGet = new HttpGet(url);
            HttpResponse response = getClient().execute(httpGet);

            return processHttpResponse(response);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        }
    }

    /**
     * Issue an HTTP POST for the given URL, return the response
     *
     * @param url
     * @param data
     *            url-encoded data
     * @throws IOException
     */
    public synchronized String post(String url, HttpEntity data) throws IOException {
        if(debug)
            Log.d("http-rest-client-post", url + " | " + data); //$NON-NLS-1$ //$NON-NLS-2$

        try {
            HttpPost httpPost = new HttpPost(url);
            httpPost.setEntity(data);
            HttpResponse response = getClient().execute(httpPost);

            return processHttpResponse(response);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            IOException ioException = new IOException(e.getMessage());
            ioException.initCause(e);
            throw ioException;
        }
    }

}
