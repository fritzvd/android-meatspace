package com.tec27.meatspace;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;


public class MeatspaceActivity extends Activity {
  private final String TAG = MeatspaceActivity.class.getSimpleName();

  private ListView chatList;
  private MessageAdapter messageAdapter;
  private Socket socket;
  private Camera camera;
  private Context ctx;
  private Preview preview;
  private int photocount;
  private ArrayList<String> vid;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    ctx = this;
    setContentView(R.layout.meatspace_activity);

    chatList = (ListView) findViewById(R.id.ChatList);
    messageAdapter = new MessageAdapter(this);
    chatList.setAdapter(messageAdapter);

    photocount = 9;
    preview = new Preview(this, (SurfaceView)findViewById(R.id.surfaceView));
    ((RelativeLayout) findViewById(R.id.layout)).addView(preview);

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
        preview.setCameraDisplayOrientation(this, 1, camera);
        camera.startPreview();
        preview.setCamera(camera);
      } catch (RuntimeException e) {
        Toast.makeText(ctx, "Whoopsie camera brokesies", Toast.LENGTH_LONG).show();
      }
    }
  }

  @Override
  protected void onPause() {
    releaseCamera();
    super.onPause();
  }

  private void releaseCamera () {
    if (camera != null) {
      camera.stopPreview();
      preview.setCamera(null);
      camera.release();
      camera = null;
    }
  }



  Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
    public void onShutter() {
      // does nothing at the moment but's necessary apparently?
    }
  };
  Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
    public void onPictureTaken(byte[] data, Camera camera) {
      // does nothing at the moment but's necessary apparently?
    }
  };
  Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
    public void onPictureTaken(byte[] data, Camera camera) {
      SaveImageTask saveImage = new SaveImageTask();
      new SaveImageTask().execute(data);
    }
  };

  public interface OnTaskCompleted{
    void onTaskCompleted();
  }

  public class SendMessageActivity implements OnTaskCompleted {
    public void onTaskCompleted () {
      photocount--;
      if (photocount == 0) {
        sendMessage();
      } else {
        camera.takePicture(shutterCallback, rawCallback, jpegCallback);
      }

    }

  }
  private static String encodeTobase64(Bitmap image)
  {
    Bitmap immagex=image;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    immagex.compress(Bitmap.CompressFormat.JPEG, 100, baos);
    byte[] b = baos.toByteArray();
    String imageEncoded = Base64.encodeToString(b, Base64.DEFAULT);

    Log.e("LOOK", imageEncoded);
    return imageEncoded;
  }

  public static Bitmap decodeBase64(byte[] input)
  {
    byte[] decodedByte = Base64.decode(input, 0);
    return BitmapFactory.decodeByteArray(decodedByte, 0, decodedByte.length);
  }

  private class SaveImageTask extends AsyncTask<byte[], Void, Void> {
    private OnTaskCompleted listener;

    public void SendMessageTask(OnTaskCompleted listener) {
      this.listener = listener;
    }



    @Override
    protected Void doInBackground(byte[]... data) {
      FileOutputStream outStream = null;
      Bitmap ogImage = BitmapFactory.decodeByteArray(data[0], 0, data[0].length);
      Bitmap image = Bitmap.createScaledBitmap(ogImage, 200, 150, false);
      vid.add(encodeTobase64(image));

      listener.onTaskCompleted();

      return null;
    }

  }

  public void startMessage (View view) {
      camera.takePicture(shutterCallback, rawCallback, jpegCallback);
  }



  private void sendMessage() {
    EditText editText = (EditText) findViewById(R.id.edit_message);
    String messageText = editText.getText().toString();
    JSONObject message = new JSONObject();
    try {
      message.put("message", messageText);
      message.put("fingerprint", "fritzieee");
      message.put("media", vid);
      if (socket != null) {
        socket.emit("message", message);
        cleanUp();
      }
    } catch (JSONException e) {
      throw Throwables.propagate(e);
    }
  }

  private void cleanUp () {
    vid.clear();
    photocount = 9;
  }


}
