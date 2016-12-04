package xyz.geminiwen.gsocket;

import org.json.JSONException;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
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
    public static final String ENGINE_IO_PROTOCOL = "3";

    protected enum ReadyState {
        OPENING, OPEN, CLOSED, PAUSED;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }


    private ReadyState mReadyState;
    private ScheduledExecutorService mHeartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    private Future mPingIntervalTimer;

    private okhttp3.WebSocket mWebSocket;
    private boolean mWrittable;
    private Options mOptions;
    private HttpUrl mHttpUrl;

    //TODO deal with session id
    private String mSessionId;
    private long mPingInterval;
    private long mPingTimeout;

    public WebSocket(Options opts) {
        mOptions = opts;
        HttpUrl.Builder urlBuilder = new HttpUrl.Builder();
        urlBuilder.host(opts.host)
                  .port(opts.port)
                .scheme(opts.scheme)
                .addQueryParameter("EIO", ENGINE_IO_PROTOCOL)
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

    public void send(Packet... packets) {
        if (this.mReadyState == ReadyState.OPEN) {
            try {
                this.write(packets);
            } catch (UTF8Exception err) {
                throw new RuntimeException(err);
            }
        } else {
            throw new RuntimeException("Transport not open");
        }
    }

    protected void write(Packet... packets) throws UTF8Exception {
        final WebSocket self = this;
        this.mWrittable = false;
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
            } finally {
                mWebSocket.cancel();
            }
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
        if (this.mReadyState == ReadyState.OPENING || this.mReadyState == ReadyState.OPEN) {
            if (Packet.OPEN.equals(packet.type)) {
                try {
                    this.onHandshake(new HandshakeData((String)packet.data));
                } catch (JSONException e) {
//                    this.emit(EVENT_ERROR, new EngineIOException(e));
                }
            } else if (Packet.PONG.equals(packet.type)) {
//                this.setPing();
//                this.emit(EVENT_PONG);
            } else if (Packet.ERROR.equals(packet.type)) {
//                EngineIOException err = new EngineIOException("server error");
//                err.code = packet.data;
//                this.onError(err);
            } else if (Packet.MESSAGE.equals(packet.type)) {
//                this.emit(EVENT_DATA, packet.data);
//                this.emit(EVENT_MESSAGE, packet.data);
            }
        } else {
//            logger.fine(String.format("packet received with socket readyState '%s'", this.readyState));
        }
    }

    public void onHandshake(HandshakeData data) {
        this.mSessionId = data.sid;
        this.mPingInterval = data.pingInterval;
        this.mPingTimeout = data.pingTimeout;
    }

    private void intervalPing() {
        if (mPingIntervalTimer != null) {
            mPingIntervalTimer.cancel(false);
            mPingIntervalTimer = null;
        }

        mPingIntervalTimer = mHeartbeatExecutor.schedule(new Runnable() {
            @Override
            public void run() {
                send(new Packet(Packet.PING));
            }
        }, this.mPingInterval, TimeUnit.MILLISECONDS);
    }


    public void onClose() {
        this.mReadyState = ReadyState.CLOSED;
    }

    public void onError(String message, Throwable t) {
        t.printStackTrace();
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
            mProxy.onError("WebSocket Error", t);
        }
    };






}
