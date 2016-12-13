package xyz.geminiwen.gsocket.demo;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;

import rx.functions.Action1;
import xyz.geminiwen.gsocket.Options;
import xyz.geminiwen.gsocket.Packet;
import xyz.geminiwen.gsocket.WebSocket;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        WebSocket webSocket = new WebSocket(new Options() {{
            this.host = "192.168.1.21";
            this.port = 3000;
            this.scheme = "http";
            this.path = "engine.io/";
        }});

        webSocket.onPacket()
                .subscribe(new Action1<Packet>() {
                    @Override
                    public void call(Packet packet) {

                    }
                });
    }
}
