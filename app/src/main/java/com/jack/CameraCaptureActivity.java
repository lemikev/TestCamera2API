package com.jack;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
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
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Size;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;

import android.util.Log;

public class CameraCaptureActivity extends AppCompatActivity  {
    private CameraCaptureActivity cameraCaptureActivity = this;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private static final int REQUEST_GPS_PERMISSION = 201;

    private ImageButton recordImageButton;
    private ImageButton reverseImageButton;
    private ImageButton playImageButton;
    private ImageButton forwardImageButton;

    private static int idle = 0;
    private static int recording = 1;
    private static int playing = 2;
    private int textureViewUsage = idle;

    private TextureView textureView;
    private MediaPlayer mediaPlayer;
    private View dividerView;
    private TextView timestampTextView;

    private String cameraId;
    private CameraDevice cameraDevice = null;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private MediaRecorder mediaRecorder;
    protected Context mainActivity;
    private String videoFileName;
    private int screenPosition;
    private int mediaPosition;
    private long startRecordingTimestamp = 0;
    long gpsOffset = 0;
    TimeZone timezone;

    // TODO remove
    String openTime = "", beforeStartTime = "", afterStartTime = "", onActiveTime = "", onConfiguredTime = "";
    Button printButton;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;

        textureView = (TextureView) findViewById(R.id.texture);
        dividerView = (View) findViewById(R.id.dividerView);
        mediaRecorder = new MediaRecorder();
        mediaPlayer = new MediaPlayer();
        timestampTextView = (TextView) findViewById(R.id.timestampTextView);;

        // Recording button
        recordImageButton = (ImageButton) findViewById(R.id.recordImageButton);
        recordImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (textureViewUsage == playing)
                    stopPlaying();

                if (textureViewUsage == idle)
                    startRecording();
                else
                    stopRecording();
            }
        });

        // Play button
        playImageButton = (ImageButton) findViewById(R.id.playImageButton);
        playImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (textureViewUsage == idle)
                    startPlaying();
                else if (textureViewUsage == playing) {
                    if (mediaPlayer.isPlaying())
                        pauseVideo();
                    else
                        restartVideo();
                }
            }
        });

        // Forward button
        forwardImageButton = (ImageButton) findViewById(R.id.forwardImageButton);
        forwardImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (textureViewUsage != playing)
                    return;

                if (mediaPlayer.isPlaying())
                    pauseVideo();

                int seekTo = mediaPlayer.getCurrentPosition() + 4000;
                if (seekTo > mediaPlayer.getDuration())
                    seekTo = mediaPlayer.getDuration();

                mediaPlayer.seekTo(seekTo, mediaPlayer.SEEK_CLOSEST);
                displaySeekTime(seekTo);
            }
        });

        // Reverse button
        reverseImageButton = (ImageButton) findViewById(R.id.reverseImageButton);
        reverseImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (textureViewUsage != playing)
                    return;

                if (mediaPlayer.isPlaying())
                    pauseVideo();

                int seekTo = mediaPlayer.getCurrentPosition() - 4000;
                if (seekTo < 0)
                    seekTo = 0;

                mediaPlayer.seekTo(seekTo, mediaPlayer.SEEK_CLOSEST);
                displaySeekTime(seekTo);
            }
        });

        textureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        pauseVideo();
                        screenPosition = (int) event.getX();
                        mediaPosition = mediaPlayer.getCurrentPosition();
                    break;

                    case MotionEvent.ACTION_MOVE:
                        int newScreenPosition = (int) event.getX();
                        int seekTo = mediaPosition + (newScreenPosition - screenPosition);

                        if (seekTo < 0)
                            seekTo = 0;
                        if (seekTo > mediaPlayer.getDuration())
                            seekTo = mediaPlayer.getDuration();

                        if (Math.abs(mediaPlayer.getCurrentPosition() - seekTo) > 33) {
                            mediaPlayer.seekTo(seekTo, mediaPlayer.SEEK_CLOSEST);
                            displaySeekTime(seekTo);
                        }
                        break;
                }
                return true;
            }
        });

        timezone = TimeZone.getDefault();
        getGpsOffset(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.camera_capture_activity_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.debugMenuItem:
                Log.i("MyDebug", "open time " + openTime);
                Log.i("MyDebug", "before start time = " + beforeStartTime);
                Log.i("MyDebug", "after start time = " + afterStartTime);
                Log.i("MyDebug", "onConfigured time = " + onConfiguredTime);
                Log.i("MyDebug", "onActive time = " + onActiveTime);
                return true;
        }
        return false;
    }

    // Upon a resume of this activity, open the camera
    @Override
    protected void onResume() {
        super.onResume();
        textureViewUsage = idle;
    }

    // Upon a pause of this activity, close the camera
    @Override
    protected void onPause() {
        if (textureViewUsage == recording)
            stopRecording();

        if (textureViewUsage == playing)
            stopPlaying();

        super.onPause();
    }

    // Message displayed if a user deny access to the camera
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION: {
                if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                    Toast.makeText(CameraCaptureActivity.this, "Vous devez donner les droits accèss à la caméra pour capturer des temps par vidéo.", Toast.LENGTH_LONG).show();
                    finish();
                }
            }

            case REQUEST_GPS_PERMISSION: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    boolean permissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
                    if (permissionGranted) {
                        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);
                    }
                }
            }
        }
    }

    // ====================================================================
    // Recording

    private void startRecording() {
        if (textureViewUsage != idle)
            return;

        try {
            createVideoFile();
        }
        catch(IOException e) {
            Toast.makeText(CameraCaptureActivity.this, "ERREUR, Lors de la création du fichier vidéo.", Toast.LENGTH_LONG).show();
            return;
        }

        openTime = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());

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

        textureViewUsage = recording;
        textureView.setVisibility(View.VISIBLE);
        dividerView.setVisibility(View.VISIBLE);
        playImageButton.setVisibility(View.INVISIBLE);
        forwardImageButton.setVisibility(View.INVISIBLE);
        reverseImageButton.setVisibility(View.INVISIBLE);
        recordImageButton.setImageResource(R.drawable.icon_video_stop);
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startCaptureSession();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            stopRecording();
        }
    };

    protected void startCaptureSession() {
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
    }

    private final CameraCaptureSession.StateCallback cameraCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            if (onConfiguredTime.isEmpty() && textureViewUsage == recording)
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
            if (textureViewUsage == recording) {
                startRecordingTimestamp = System.currentTimeMillis();
                startRecordingTimestamp += timezone.getOffset(startRecordingTimestamp) + gpsOffset;
                onActiveTime = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS").format(new Date());
            }
        }
    };


    protected void stopRecording() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }

        if (textureViewUsage == recording) {
            mediaRecorder.stop();
            mediaRecorder.reset();
        }

        textureViewUsage = idle;
        textureView.setVisibility(View.INVISIBLE);
        dividerView.setVisibility(View.INVISIBLE);
        playImageButton.setVisibility(View.VISIBLE);
        forwardImageButton.setVisibility(View.VISIBLE);
        reverseImageButton.setVisibility(View.VISIBLE);
        recordImageButton.setImageResource(R.drawable.icon_video_record);
    }

    // ====================================================================
    // Playing video

    // Start playing
    protected void startPlaying() {
        if (textureViewUsage != idle)
            return;

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
            }
        });

        textureViewUsage = playing;
        textureView.setVisibility(View.VISIBLE);
        dividerView.setVisibility(View.VISIBLE);
        playImageButton.setImageResource(R.drawable.icon_video_pause);
    }


    protected void stopPlaying() {
        if (textureViewUsage == playing) {
            mediaPlayer.stop();
            mediaPlayer.reset();
            textureViewUsage = idle;
        }

        textureView.setVisibility(View.INVISIBLE);
        dividerView.setVisibility(View.INVISIBLE);
        timestampTextView.setVisibility(View.INVISIBLE);
        playImageButton.setImageResource(R.drawable.icon_video_play);
    }

    protected void pauseVideo() {
        if (textureViewUsage != playing)
            return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            displaySeekTime(mediaPlayer.getCurrentPosition());
            timestampTextView.setVisibility(View.VISIBLE);
            playImageButton.setImageResource(R.drawable.icon_video_play);
        }
    }

    protected void restartVideo() {
        if (textureViewUsage != playing)
            return;

        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            timestampTextView.setVisibility(View.INVISIBLE);
            playImageButton.setImageResource(R.drawable.icon_video_pause);
        }
    }

    private void displaySeekTime(int seekTo) {
        long seekTime = startRecordingTimestamp + seekTo;
        String seekTime_s = String.format("%02d:%02d:%02d %03d (GPS delta %d)", seekTime / 3600000 % 24, seekTime / 60000 % 60, seekTime / 1000 % 60, seekTime % 1000, gpsOffset);
        timestampTextView.setText(seekTime_s);
    }

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

    // ====================================================================
    // GPS interaction

    private static LocationManager locationManager = null;
    private static LocationListener locationListener;

    // Enable the Location service to get single GPS fix
    @SuppressLint("MissingPermission")
    private void getGpsOffset(Context context) {
        if (locationManager == null) {
            locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            locationListener = new LocationListener() {

                public void onLocationChanged(android.location.Location location) {
                    long deviceTime = System.currentTimeMillis();
                    long gpsTime = location.getTime();
                    gpsOffset = gpsTime - deviceTime;
                }

                public void onStatusChanged(String provider, int status, android.os.Bundle extras) {
                }

                public void onProviderEnabled(String provider) {
                }

                public void onProviderDisabled(String provider) {
                }
            };

            boolean permissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (permissionGranted) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 0, locationListener);
            } else
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_GPS_PERMISSION);
        }
    }
}
