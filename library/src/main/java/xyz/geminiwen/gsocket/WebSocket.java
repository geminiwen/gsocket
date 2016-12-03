package xyz.geminiwen.gsocket;

import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocketListener;
import okio.ByteString;

/**
 * Created by geminiwen on 2016/12/3.
 */

public class WebSocket {
    public static final String NAME = WebSocket.class.getSimpleName();

    protected enum ReadyState {
        OPENING, OPEN, CLOSED, PAUSED;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }


    ReadyState mReadyState;

    private okhttp3.WebSocket mWebSocket;
    private boolean mWrittable;
    private Options mOptions;
    private HttpUrl mHttpUrl;

    public WebSocket(Options opts) {
        mOptions = opts;
        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        urlBuilder.host(opts.host)
                  .port(opts.port)
                .scheme(opts.scheme)
                .addQueryParameter("EIO", "3")
                .addPathSegments("engine.io/")
                .addQueryParameter("transport", "websocket");
        mHttpUrl = urlBuilder.build();
    }

    public void open() {
        if (this.mReadyState == ReadyState.CLOSED || this.mReadyState == null) {
            this.mReadyState = ReadyState.OPENING;
            this.doOpen();
        }
    }

    protected void doOpen() {
        OkHttpClient client = mOptions.httpClient;

        if (client == null) {
            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .connectTimeout(0, TimeUnit.MILLISECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .writeTimeout(0, TimeUnit.MILLISECONDS);
            client = clientBuilder.build();
        }

        Request.Builder builder = new Request.Builder().url(mHttpUrl.url());
        final Request request = builder.build();

        mWebSocket = client.newWebSocket(request, mWebSocketListener);
    }

    protected void write(Packet[] packets) throws UTF8Exception {
        final WebSocket self = this;
        this.mWrittable = false;


        final int[] total = new int[]{packets.length};
        for (Packet packet : packets) {
            if (this.mReadyState != ReadyState.OPENING && this.mReadyState != ReadyState.OPEN) {
                // Ensure we don't try to send anymore packets if the socket ends up being closed due to an exception
                break;
            }
            if (packet.data instanceof String) {
                String data = Parser.encodePacketString((Packet<String>)packet);
                mWebSocket.send(data);
            } else {
                byte[] data = Parser.encodePacketBinary((Packet<byte[]>)packet);
                mWebSocket.send(ByteString.of(data));
            }
        }
    }

    protected void doClose() {
        if (mWebSocket != null) {
            try {
                mWebSocket.close(1000, "");
            } catch (IllegalStateException e) {
                // websocket already closed
            }
        }
        if (mWebSocket != null) {
            mWebSocket.cancel();
        }
    }

    public void onOpen() {
        this.mReadyState = ReadyState.OPEN;
        this.mWrittable = true;
    }

    public void onData(String text) {
        Packet<String> packet = Parser.decodePacket(text);
        this.onPacket(packet);
    }

    public void onData(byte[] data) {
        Packet<byte[]> packet = Parser.decodePacket(data);
        this.onPacket(packet);
    }

    public void onPacket(Packet packet) {
    }

    public void onClose() {

    }

    public void onError(String message, Throwable t) {

    }


    private WebSocketListener mWebSocketListener = new WebSocketListener() {
        WebSocket mProxy;

        {
            mProxy = WebSocket.this;
        }

        @Override
        public void onOpen(okhttp3.WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);
            mProxy.onOpen();
        }

        @Override
        public void onMessage(okhttp3.WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            mProxy.onData(text);
        }

        @Override
        public void onMessage(okhttp3.WebSocket webSocket, ByteString bytes) {
            super.onMessage(webSocket, bytes);
            mProxy.onData(bytes.toByteArray());
        }

        @Override
        public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
            mProxy.onClose();
        }

        @Override
        public void onFailure(okhttp3.WebSocket webSocket, final Throwable t, Response response) {
            t.printStackTrace();
            mProxy.onError("WebSocket Error", t);
        }
    };






}
