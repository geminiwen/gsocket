package xyz.geminiwen.gsocket.demo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import xyz.geminiwen.gsocket.Options;
import xyz.geminiwen.gsocket.WebSocket;


public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final WebSocket webSocket = new WebSocket(new Options(){{
            this.host = "192.168.1.21";
            this.port = 3000;
            this.scheme = "http";
        }});
        new Thread(new Runnable() {
            @Override
            public void run() {
                webSocket.open();
            }
        }).start();
    }
}
