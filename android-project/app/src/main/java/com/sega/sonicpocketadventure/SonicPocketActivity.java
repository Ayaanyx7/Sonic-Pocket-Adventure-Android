package com.glitchkiss.sonicpocket;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import org.libsdl.app.SDLActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class SonicPocketActivity extends SDLActivity {
    private static final int PICK_ROM_REQUEST = 1001;
    private File romFile;

    @Override
    protected String[] getLibraries() {
        return new String[] {
            "SDL3",
            "sonic-pocket"
        };
    }

    @Override
    protected String[] getArguments() {
        return new String[] {
            romFile.getAbsolutePath()
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        romFile = new File(getFilesDir(), "sonic.ngc");
        if (!romFile.exists()) {
            // Delay SDL initialization until user selects the ROM
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, PICK_ROM_REQUEST);
            return;
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_ROM_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(romFile)) {
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                // Restart activity now that sonic.ngc is in internal storage
                recreate();
            } catch (Exception e) {
                e.printStackTrace();
                finish();
            }
        } else if (requestCode == PICK_ROM_REQUEST) {
            finish();
        }
    }
}
