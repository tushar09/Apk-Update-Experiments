package com.captaindroid.apkinstall;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.captaindroid.apkinstall.databinding.ActivityMainBinding;
import com.captaindroid.apkinstall.databinding.DownloaderDialogBinding;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {


    private File directory;
    private File f;
    private Intent finalIntent;

    private DownloaderDialogBinding downloaderDialogBinding;

    private AlertDialog dialog;

    private ActivityMainBinding binding;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseStorage storage = FirebaseStorage.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.tvVersion.setText(BuildConfig.VERSION_NAME + "(" + BuildConfig.VERSION_CODE + ")");

        directory = getDir("myFiles", Context.MODE_PRIVATE);
        directory.mkdir();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:".concat(getPackageName()))));
        }else{
            db.collection("app")
                    .document("appUpdate")
                    .get()
                    .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                        @Override
                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                            if(task.getResult().getLong("versionCode") > BuildConfig.VERSION_CODE){
                                StorageReference pathReference = storage.getReference().child("apps").child("ApkV2.apk");
                                pathReference.getDownloadUrl().addOnSuccessListener(new OnSuccessListener<Uri>() {
                                    @Override
                                    public void onSuccess(Uri uri) {
                                        f = new File(getFilesDir().getPath() + "/myApk" + ".apk");
                                        try {
                                            f.createNewFile();
                                            downloaderDialogBinding = DownloaderDialogBinding.inflate(getLayoutInflater());
                                            dialog = new MaterialAlertDialogBuilder(MainActivity.this)
                                                    .setIcon(R.mipmap.ic_launcher)
                                                    .setTitle("Downloading")
                                                    .setMessage("Please wait")
                                                    .setView(downloaderDialogBinding.getRoot())
                                                    .show();
                                            new DownloadTask().execute(uri.toString(), "", "");
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }).addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Log.e("path fail", e.toString());
                                    }
                                });

                            }else{

                            }
                        }
                    });
        }
    }

    private class DownloadTask extends AsyncTask<String, Integer, String> {

        @Override
        protected String doInBackground(String... sUrl) {
            InputStream input = null;
            OutputStream output = null;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(sUrl[0]);
                connection = (HttpURLConnection) url.openConnection();
                connection.connect();

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    return "Server returned HTTP " + connection.getResponseCode()
                            + " " + connection.getResponseMessage();
                }

                int fileLength = connection.getContentLength();

                input = connection.getInputStream();
                output = new FileOutputStream(f);

                byte data[] = new byte[4096];
                long total = 0;
                int count;
                while ((count = input.read(data)) != -1) {
                    Log.e("while", "done");
                    if (isCancelled()) {
                        input.close();
                        return null;
                    }
                    total += count;
                    if (fileLength > 0)
                        publishProgress((int) (total * 100 / fileLength));
                    output.write(data, 0, count);
                }
            } catch (Exception e) {
                return e.toString();
            } finally {
                try {
                    if (output != null)
                        output.close();
                    if (input != null)
                        input.close();
                } catch (IOException ignored) {
                }

                if (connection != null)
                    connection.disconnect();
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            downloaderDialogBinding.progress.setProgressCompat(values[0], true);
            if(values[0] >= 100){
                dialog.dismiss();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            super.onPostExecute(s);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = FileProvider.getUriForFile(MainActivity.this, BuildConfig.APPLICATION_ID + ".provider", f);
                finalIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
                finalIntent.setData(apkUri);
                finalIntent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                Uri apkUri = Uri.fromFile(f);
                finalIntent = new Intent(Intent.ACTION_VIEW);
                finalIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                finalIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            startActivity(finalIntent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == Activity.RESULT_OK){
            startActivity(finalIntent);
        }
    }
}