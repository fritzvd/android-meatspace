package com.tec27.meatspace;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;


public class MeatspaceActivity extends Activity {
  private final String TAG = MeatspaceActivity.class.getSimpleName();

  private ListView chatList;
  private MessageAdapter messageAdapter;
  private Socket socket;
  private Camera camera;
  private Context ctx;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ctx = this;
    setContentView(R.layout.meatspace_activity);

    chatList = (ListView) findViewById(R.id.ChatList);
    messageAdapter = new MessageAdapter(this);
    chatList.setAdapter(messageAdapter);

    try {
      socket = IO.socket("http://192.168.1.22:3000");
    } catch (URISyntaxException ex) {
      throw Throwables.propagate(ex);
    }

    socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
      @Override
      public void call(Object... args) {
        Log.d("tec27", "connected!");
        socket.emit("join", "mp4");
      }
    }).on("message", new Emitter.Listener() {
      @Override
      public void call(Object... args) {
        final JSONObject message = (JSONObject) args[0];
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            messageAdapter.addItem(Message.fromJson(message));
          }
        });
      }
    });

    socket.connect();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    // Inflate the menu; this adds items to the action bar if it is present.
    getMenuInflater().inflate(R.menu.meatspace, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    int id = item.getItemId();
    if (id == R.id.action_settings) {
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  protected void onResume() {
      super.onResume();
      int numCams= Camera.getNumberOfCameras();

      if (numCams > 0) {
          try {
              releaseCamera();
              Camera.CameraInfo info = new Camera.CameraInfo();
              Camera.getCameraInfo(0, info);
              if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                camera = Camera.open(0);
              } else if (numCams > 1) {
                  camera = Camera.open(1);
              }
              camera.startPreview();
          } catch (RuntimeException e) {
              Toast.makeText(ctx, "Whoopsie camera broke", Toast.LENGTH_LONG).show();
          }
      }
  }

    private void releaseCamera () {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    public void sendMessage (View view) {
        EditText editText = (EditText) findViewById(R.id.edit_message);
        String messageText = editText.getText().toString();
        JSONObject message = new JSONObject();
        try {
            message.put("message", messageText);
            if (socket != null) {
                socket.emit("message", message);
            }
        } catch (JSONException e) {
            throw Throwables.propagate(e);
        }

    }
}
