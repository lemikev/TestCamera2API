package com.jack;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import android.util.Log;

/* Call sequences

Upon create
  -> onSurfaceTextureAvailable
    -> startCamera()
      -> onOpened
        -> startViewing()
          -> onConfigured

Upon onResume
  -> startCamera()
    -> onOpened
      -> startViewing()
          -> onConfigured

Upon onPause
  -> stopCamera()

Upon start recording button
  -> startRecording()
    -> onConfigured

Upon stop recording button
  -> stopRecording()
  -> startViewing()
    -> onConfigured

Upon start playing button
  -> stopCamera()
  -> startPlaying()

Upon stop playing button
  -> stopPlaying()
  -> startCamera()
    -> onOpened
      -> startViewing()
        -> onConfigured
 */

public class CameraCaptureActivity extends AppCompatActivity {
    private CameraCaptureActivity cameraCaptureActivity = this;
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private ImageButton recordImageButton;
    private ImageButton playImageButton;

    private static int idle = 0;
    private static int viewing = 1;
    private static int recording = 2;
    private static int playing = 3;
    private int activityState = idle;

    private TextureView textureView;
    private MediaPlayer mediaPlayer;

    private String cameraId;
    private CameraDevice cameraDevice = null;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private MediaRecorder mediaRecorder;
    protected Context mainActivity;
    private String videoFileName;

    // TODO remove
    String openTime = "", beforeStartTime = "", afterStartTime = "", onActiveTime = "", onConfiguredTime = "";
    Integer nbrOnActive = 0;
    Button printButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;

        // Create a TextureView
        textureView = (TextureView) findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);

        // Create a MediaRecorder
        mediaRecorder = new MediaRecorder();

        // Create a MediaPlayer
        mediaPlayer = new MediaPlayer();

        // Start & stop recording button
        recordImageButton = (ImageButton) findViewById(R.id.recordImageButton);
        recordImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (activityState == viewing) {
                    try {
                        createVideoFile();
                    }
                    catch(IOException e) {
                        Toast.makeText(CameraCaptureActivity.this, "ERREUR, Lors de la création du fichier vidéo.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    openTime = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
                    startRecording();

                    // Buttons state update
                    playImageButton.setVisibility(View.INVISIBLE);
                    recordImageButton.setImageResource(R.drawable.icon_video_stop);
                }

                else if (activityState == recording) {
                    stopRecording();
                    startViewing();

                    // Buttons state update
                    playImageButton.setVisibility(View.VISIBLE);
                    recordImageButton.setImageResource(R.drawable.icon_video_record);
                }
            }
        });

        // Start & stop playing button
        playImageButton = (ImageButton) findViewById(R.id.playImageButton);
        playImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (activityState == viewing) {
                    stopCamera();
                    startPlaying();

                    // Buttons state update
                    recordImageButton.setVisibility(View.INVISIBLE);
                    playImageButton.setImageResource(R.drawable.icon_video_stop);
                }

                else if (activityState == playing) {
                    stopPlaying();
                    startCamera();

                    // Buttons state update
                    recordImageButton.setVisibility(View.VISIBLE);
                    playImageButton.setImageResource(R.drawable.icon_video_play);
                }
            }
        });

        // Debug print button
        printButton = (Button) findViewById(R.id.printButton);
        printButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Log.i("MyDebug", "open time " + openTime);
                Log.i("MyDebug", "before start time = " + beforeStartTime);
                Log.i("MyDebug", "after start time = " + afterStartTime);
                Log.i("MyDebug", "onConfigured time = " + onConfiguredTime);
                Log.i("MyDebug", "onActive time = " + onActiveTime);
                Log.i("MyDebug", "Number of OnActive calls = " + nbrOnActive.toString());
            }
        });
    }

    // Upon a resume of this activity, open the camera
    @Override
    protected void onResume() {
        super.onResume();
        activityState = idle;

        if (textureView.isAvailable()) {
            startCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    // Upon a pause of this activity, close the camera
    @Override
    protected void onPause() {
        stopCamera();
        super.onPause();
    }

    // Message displayed if a user deny access to the camera
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(CameraCaptureActivity.this, "Vous devez donner les droits accèss à la caméra pour capturer des temps par vidéo.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // ====================================================================
    // Camera

    private void startCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            // Permet d'obtenir le ID de la caméra par defaut
            cameraId = manager.getCameraIdList()[0];

            // Permet d'obtenir la dimension de l'image par défault de cette
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageDimension = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class));
            //imageDimension = new Size(1920, 1080);

            // Demande d'accès a la caméra si ce n'est pas déja fait
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(CameraCaptureActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            // Open the camera
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void stopCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        activityState = idle;
    }

    // ====================================================================
    // Viewing

    protected void startViewing() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface previewSurface = new Surface(texture);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface), cameraCaptureSessionStateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        activityState = viewing;
    }

    // ====================================================================
    // Recording

    // Configuration and start of the TextureView and MediaRecorder
    protected void startRecording() {
        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Configuration de l'affichage dans le TextureView
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface previewSurface = new Surface(texture);
            captureRequestBuilder.addTarget(previewSurface);

            onConfiguredTime = "";
            onActiveTime = "";
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(videoFileName);
            mediaRecorder.setVideoEncodingBitRate(10000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(imageDimension.getWidth(), imageDimension.getHeight());
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setOrientationHint(90);
            mediaRecorder.prepare();
            Surface recordSurface = mediaRecorder.getSurface();
            captureRequestBuilder.addTarget(recordSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface), cameraCaptureSessionStateCallback, null);
            beforeStartTime = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            mediaRecorder.start();
            afterStartTime = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        } catch (IOException | CameraAccessException e) {
            e.printStackTrace();
        }
        activityState = recording;
    }

    // Stop recording
    protected void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.reset();
        activityState = idle;
    }

    // ====================================================================
    // Playing video

    // Start playing
    protected void startPlaying() {
        if (!textureView.isAvailable())
            return;
        mediaPlayer.setSurface(new Surface(textureView.getSurfaceTexture()));
        try {
            mediaPlayer.setDataSource(getFilesDir().getAbsolutePath() + File.separator + "myvideo.mp4");
        } catch (Exception e) {
            Toast.makeText(CameraCaptureActivity.this, "ERREUR, fichier vidéo invalid.", Toast.LENGTH_LONG).show();
            return;
        }
        mediaPlayer.prepareAsync();
        mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mediaPlayer.start();
                activityState = playing;
            }
        });
    }

    protected void stopPlaying() {
        if (activityState == playing) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            activityState = idle;
        }
    }

    // ====================================================================
    // Callbacks

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            if (activityState == idle)
                startCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startViewing();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            stopCamera();
        }
    };

    private final CameraCaptureSession.StateCallback cameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            if (onConfiguredTime.isEmpty() && activityState == recording)
                onConfiguredTime = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());

            try {
                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
            Toast.makeText(CameraCaptureActivity.this, "ERREUR, imposible de démaré la capture du vidéo", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onActive(@NonNull CameraCaptureSession cameraCaptureSession) {
            nbrOnActive++;
            if (onActiveTime.isEmpty() && activityState == recording)
                onActiveTime = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        }
    };

    // ====================================================================
    // Tools

    private File createVideoFile() throws IOException
    {
        // String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
        File file = new File(getFilesDir().getAbsolutePath() + File.separator + "myvideo.mp4");
        Files.deleteIfExists(file.toPath());
        file.createNewFile();
        videoFileName = file.getAbsolutePath();
        return file;
    }

    // Selection of highest video quality lower of equal to 1K
    private Size chooseOptimalSize(Size[] sizesSupported) {
        Size sizeSelected = null;

        for (Size size : sizesSupported) {
            if (size.getHeight() <= 1080 && size.getWidth() <= 1920) {
                if (sizeSelected == null)
                    sizeSelected = size;
                else {
                    if (sizeSelected.getHeight() < size.getHeight() && sizeSelected.getWidth() < size.getWidth())
                        sizeSelected = size;
                }
            }
        }

        if (sizeSelected != null)
            return sizeSelected;
        else
            return sizesSupported[0];
    }
}
