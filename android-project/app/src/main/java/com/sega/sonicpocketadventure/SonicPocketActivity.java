package com.sega.sonicpocketadventure;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SonicPocketActivity extends Activity {

    private static final int PICK_ROM_REQUEST = 1001;
    static final String ROM_NAME = "sonic.ngc";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (romFile().exists()) {
            launchGame();
        } else {
            pickRom();
        }
    }

    private File romFile() {
        return new File(getFilesDir(), ROM_NAME);
    }

    private void pickRom() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, PICK_ROM_REQUEST);
    }

    private void launchGame() {
        startActivity(new Intent(this, GameActivity.class));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ROM_REQUEST) return;

        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }

        Uri uri = data.getData();
        File dest = romFile();
        File tmp = new File(getFilesDir(), ROM_NAME + ".tmp");

        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IllegalStateException("Could not open ROM");
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        } catch (Exception e) {
            e.printStackTrace();
            tmp.delete();
            Toast.makeText(this, "Failed to import ROM", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Only publish the file once it's fully copied, so a half-written ROM never exists
        if (dest.exists()) dest.delete();
        tmp.renameTo(dest);
        launchGame();
    }
}
