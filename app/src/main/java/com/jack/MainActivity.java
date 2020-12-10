package com.jack;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CAMERA_PERMISSION = 200;

    private TextureView textureView;
    private ImageButton recordImageButton;
    private boolean recording = false;

    private String cameraId;
    private CameraDevice cameraDevice = null;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private MediaRecorder mediaRecorder;
    protected Context mainActivity;
    private File videoFolder;
    private String videoFileName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mainActivity = this;
        createVideoFolder();

        // TextureView
        textureView = (TextureView) findViewById(R.id.texture);
        textureView.setSurfaceTextureListener(textureListener);

        // MediaRecorder
        mediaRecorder = new MediaRecorder();

        // Start record ImageButton
        recordImageButton = (ImageButton) findViewById(R.id.recordImageButton);

        recordImageButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (recording) {
                    recording = false;
                    recordImageButton.setImageResource(R.drawable.icon_video_record);
                    stopRecording();
                    startPreview();
                }
                else {
                    try {
                        createVideoFileName();
                    }
                    catch(IOException e) {
                        Toast.makeText(MainActivity.this, "ERREUR, Lors de la création du fichier vidéo.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    recording = true;
                    recordImageButton.setImageResource(R.drawable.icon_video_stop);
                    startRecording();
                }
            }
        });

    }

    // Callback appelé lors de la remise en avant de cette activité
    @Override
    protected void onResume() {
        super.onResume();

        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    // Callback appelé lors de la mise en pause de cette activité
    @Override
    protected void onPause() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        super.onPause();
    }

    // Callback appelé suite a une demande d'accès à la vidéo
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, "Désolé, vous ne pouvez utuliser cette application sans donner les droits access.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    // Callback utulisé pour attendre l'activation du TextureView avant ouvrir la caméra
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera();
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

    // Callback utulisé pour attendre l'ouvreture la caméra avant d'initialisé le préview
    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    };

    // Ouverture de la caméra
    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            // Permet d'obtenir le ID de la caméra par defaut
            cameraId = manager.getCameraIdList()[0];

            // Permet d'obtenir la dimension de l'image par défault de cette
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            imageDimension = new Size(1920, 1080);

            // Demande d'accès a la caméra si ce n'est pas déja fait
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            // Open the camera
            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Configuration et démarage du préview dans le TextureView
    protected void startPreview() {
        try {
            // Configuration de la dimension de l'image selon la dimension de la caméra
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface previewSurface = new Surface(texture);

            // Configuration d'un CaptureRequest.Builder
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if (null == cameraDevice)
                        return;

                    // Configuration d'une CameraCaptureSession
                    cameraCaptureSessions = cameraCaptureSession;
                    captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                    try {
                        cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "ERREUR, imposible de démaré le vidéo", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Configuration et démarage du TextureView et du MediaRecorder
    protected void startRecording() {
        try {
            // Configuration de la classe MediaRecorder
            //setUpMediaRecorder();

            // Configuration de l'affichage dans le TextureView
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(imageDimension.getWidth(), imageDimension.getHeight());
            Surface previewSurface = new Surface(texture);

            // Configuration de l'enregistrement dans le MediaRecorder
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setOutputFile(videoFileName);
            mediaRecorder.setVideoEncodingBitRate(1000000);
            mediaRecorder.setVideoFrameRate(30);
            mediaRecorder.setVideoSize(imageDimension.getWidth(), imageDimension.getHeight());
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mediaRecorder.setOrientationHint(90);
            mediaRecorder.prepare();
            Surface recordSurface = mediaRecorder.getSurface();
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            // Ajout des deux targets dans le CaptureRequest.Builder
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(recordSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            try {
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, null);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(MainActivity.this, "ERREUR, imposible de démaré la capture du vidéo", Toast.LENGTH_SHORT).show();
                        }
                    }, null);

            mediaRecorder.start();

        } catch (IOException | CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Arrêt de l'enregistrement
    protected void stopRecording() {
        mediaRecorder.stop();
        mediaRecorder.reset();
    }

    // Création du répertoire Heat dans le répertoire partagé Movies
    private void createVideoFolder(){
        File movieFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        videoFolder = new File(movieFile, "Heat");
        if(!videoFolder.exists()) {
            videoFolder.mkdirs();
        }
    }

    // Création du nom du ficher vidéo et overture du fichier
    private File createVideoFileName() throws IOException
    {
        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File file = File.createTempFile(fileName, ".mp4", videoFolder);
        videoFileName = file.getAbsolutePath();
        return file;
    }
}