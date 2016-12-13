package xyz.geminiwen.gsocket;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;
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
import rx.Observable;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

/**
 * Created by geminiwen on 2016/12/3.
 */

public class WebSocket {
    public static final String NAME = WebSocket.class.getSimpleName();
    private static final String ENGINE_IO_PROTOCOL = "3";

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

    private List<WebSocketListener> mSocketListeners;

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
                .addPathSegments(opts.path)
                .addQueryParameter("EIO", ENGINE_IO_PROTOCOL)
                .addQueryParameter("transport", "websocket");
        mHttpUrl = urlBuilder.build();
        mSocketListeners = new ArrayList<>();
    }

    public void addSocketListener(WebSocketListener l) {
        this.mSocketListeners.add(l);
    }
    private  void openIfNeed() {
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
        this.mWrittable = false;
        for (Packet packet : packets) {
            if (this.mReadyState != ReadyState.OPENING && this.mReadyState != ReadyState.OPEN) {
                // Ensure we don't try to send anymore packets if the socket ends up being closed due to an exception
                break;
            }
            if (packet.data instanceof String) {
                String data = Parser.encodePacketString((Packet<String>) packet);
                mWebSocket.send(data);
            } else {
                byte[] data = Parser.encodePacketBinary((Packet<byte[]>) packet);
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

    private void checkReadyState() throws EngineIOException {
        if (this.mReadyState == ReadyState.OPENING || this.mReadyState == ReadyState.OPEN) {
            return;
        } else {
            throw new EngineIOException("unknown packet for illegal state");
        }
    }

    public void handlePacket(Packet packet, Subscriber<? super Packet> subscriber) {
        try {
            checkReadyState();
            if (Packet.ERROR.equals(packet.type)) {
                EngineIOException err = new EngineIOException("server error");
                subscriber.onError(err);
                subscriber.onCompleted();
            } else if (Packet.MESSAGE.equals(packet.type)) {
                subscriber.onNext(packet);
            }
        } catch (EngineIOException e) {
            subscriber.onError(e);
            subscriber.onCompleted();
        }
    }


    private void onPacketInternal(Packet packet) {
        try {
            checkReadyState();
            if (Packet.OPEN.equals(packet.type)) {
                try {
                    this.onHandshake(new HandshakeData((String) packet.data));
                } catch (JSONException e) {
                }
            }
        } catch (EngineIOException e) {

        }
    }


    void onHandshake(HandshakeData data) {
        this.mSessionId = data.sid;
        this.mPingInterval = data.pingInterval;
        this.mPingTimeout = data.pingTimeout;

        setupIntervalPing();
    }

    void setupIntervalPing() {
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


    void onClose() {
        this.mReadyState = ReadyState.CLOSED;
    }

    void onError(String message, Throwable t) {
        t.printStackTrace();
    }

    public Observable<Packet> onPacket() {
        return Observable.create(new Observable.OnSubscribe<Packet>() {
            @Override
            public void call(final Subscriber<? super Packet> subscriber) {
                openIfNeed();

                final WebSocketListener listener = new WebSocketListener() {
                    @Override
                    public void onMessage(okhttp3.WebSocket webSocket, String text) {
                        super.onMessage(webSocket, text);
                        Packet<String> packet = Parser.decodePacket(text);
                        handlePacket(packet, subscriber);
                    }

                    @Override
                    public void onMessage(okhttp3.WebSocket webSocket, ByteString bytes) {
                        super.onMessage(webSocket, bytes);
                        Packet<byte[]> packet = Parser.decodePacket(bytes.toByteArray());
                        handlePacket(packet, subscriber);
                    }
                };

                mSocketListeners.add(listener);

                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        mSocketListeners.remove(listener);
                    }
                }));

            }
        });
    }

    private WebSocketListener mWebSocketListener = new WebSocketListener() {
        WebSocket mProxy;

        {
            mProxy = WebSocket.this;
        }

        @Override
        public void onOpen(okhttp3.WebSocket webSocket, Response response) {
            super.onOpen(webSocket, response);
            for (WebSocketListener l : mSocketListeners) {
                l.onOpen(webSocket, response);
            }
            mProxy.onOpen();
        }

        @Override
        public void onMessage(okhttp3.WebSocket webSocket, String text) {
            super.onMessage(webSocket, text);
            Packet<String> packet = Parser.decodePacket(text);
            mProxy.onPacketInternal(packet);

            for (WebSocketListener l : mSocketListeners) {
                l.onMessage(webSocket, text);
            }
        }

        @Override
        public void onMessage(okhttp3.WebSocket webSocket, ByteString bytes) {
            super.onMessage(webSocket, bytes);
            Packet<byte[]> packet = Parser.decodePacket(bytes.toByteArray());
            mProxy.onPacketInternal(packet);

            for (WebSocketListener l : mSocketListeners) {
                l.onMessage(webSocket, bytes);
            }
        }

        @Override
        public void onClosed(okhttp3.WebSocket webSocket, int code, String reason) {
            super.onClosed(webSocket, code, reason);
            for (WebSocketListener l : mSocketListeners) {
                l.onClosed(webSocket, code, reason);
            }
            mProxy.onClose();
        }

        @Override
        public void onFailure(okhttp3.WebSocket webSocket, Throwable t, Response response) {
            super.onFailure(webSocket, t, response);
            for (WebSocketListener l : mSocketListeners) {
                l.onFailure(webSocket, t, response);
            }

            for (WebSocketListener l : mSocketListeners) {
                l.onOpen(webSocket, response);
            }
            mProxy.onError("WebSocket Error", t);
        }
    };
}
