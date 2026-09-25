package com.il2cpp.dumper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import android.util.Log;
import java.io.*;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends Activity {
    private Uri soUri;
    private Uri metadataUri;
    private Uri outputUri;
    private static final int PICK_SO = 1;
    private static final int PICK_METADATA = 2;
    private static final int CREATE_ZIP = 3;
    private static final String TAG = "Il2CppDumper";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSo = findViewById(R.id.btnSelectSo);
        Button btnMetadata = findViewById(R.id.btnSelectMetadata);
        Button btnDump = findViewById(R.id.btnStartDump);

        btnSo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_SO);
        });

        btnMetadata.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_METADATA);
        });

        btnDump.setOnClickListener(v -> {
            if (soUri != null && metadataUri != null) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/zip");
                intent.putExtra(Intent.EXTRA_TITLE, "Il2CppDump_" + System.currentTimeMillis() + ".zip");
                startActivityForResult(intent, CREATE_ZIP);
            } else {
                Toast.makeText(this, "Pehle dono files select karein!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == PICK_SO) {
                soUri = data.getData();
                Toast.makeText(this, "libil2cpp.so select ho gayi", Toast.LENGTH_SHORT).show();
            } else if (requestCode == PICK_METADATA) {
                metadataUri = data.getData();
                Toast.makeText(this, "global-metadata.dat select ho gaya", Toast.LENGTH_SHORT).show();
            } else if (requestCode == CREATE_ZIP) {
                outputUri = data.getData();
                new Thread(this::processFiles).start();
            }
        }
    }

    private void processFiles() {
        try {
            File cacheDir = getCacheDir();
            File filesDir = getFilesDir();
            
            File soFile = new File(cacheDir, "libil2cpp.so");
            File metaFile = new File(cacheDir, "global-metadata.dat");
            File outDir = new File(cacheDir, "dump_output");
            outDir.mkdirs();
            
            File exeFile = new File(filesDir, "il2cppdumper");
            File linkerFile = new File(filesDir, "linker64");
            File libDir = new File(filesDir, "lib");
            libDir.mkdirs();
            
            File libLz4 = new File(libDir, "liblz4.so");
            File libCpp = new File(libDir, "libc++_shared.so");

            copyUriToFile(soUri, soFile);
            copyUriToFile(metadataUri, metaFile);

            extractAsset("il2cppdumper", exeFile);
            extractAsset("linker64", linkerFile);
            extractAsset("liblz4.so", libLz4);
            extractAsset("libc++_shared.so", libCpp);

            Runtime.getRuntime().exec("chmod 755 " + exeFile.getAbsolutePath()).waitFor();
            Runtime.getRuntime().exec("chmod 755 " + linkerFile.getAbsolutePath()).waitFor();

            ProcessBuilder pb = new ProcessBuilder(
                linkerFile.getAbsolutePath(),
                exeFile.getAbsolutePath(),
                soFile.getAbsolutePath(),
                metaFile.getAbsolutePath(),
                outDir.getAbsolutePath()
            );
            
            Map<String, String> env = pb.environment();
            env.put("LD_LIBRARY_PATH", libDir.getAbsolutePath());
            pb.redirectErrorStream(true);
            
            Process p = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d(TAG, line);
            }
            p.waitFor();

            File zipFile = new File(cacheDir, "dump.zip");
            zipDirectory(outDir, zipFile);
            saveZipToUri(outputUri, zipFile);

            runOnUiThread(() -> Toast.makeText(this, "Dump complete! Downloads folder check karein.", Toast.LENGTH_LONG).show());

        } catch (Exception e) {
            Log.e(TAG, "Error processing files", e);
            runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private void copyUriToFile(Uri uri, File dest) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private void extractAsset(String assetName, File dest) throws IOException {
        try (InputStream in = getAssets().open(assetName);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private void zipDirectory(File dir, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    try (FileInputStream fis = new FileInputStream(file)) {
                        zos.putNextEntry(new ZipEntry(file.getName()));
                        byte[] buf = new byte[8192];
                        int len;
                        while ((len = fis.read(buf)) > 0) {
                            zos.write(buf, 0, len);
                        }
                        zos.closeEntry();
                    }
                }
            }
        }
    }

    private void saveZipToUri(Uri uri, File zipFile) throws IOException {
        try (InputStream in = new FileInputStream(zipFile);
             OutputStream out = getContentResolver().openOutputStream(uri)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }
}
