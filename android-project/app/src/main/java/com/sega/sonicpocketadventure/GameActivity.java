package com.sega.sonicpocketadventure;

import org.libsdl.app.SDLActivity;

import java.io.File;

public class GameActivity extends SDLActivity {

    @Override
    protected String[] getLibraries() {
        return new String[] { "SDL3", "sonic-pocket" };
    }

    @Override
    protected String[] getArguments() {
        return new String[] {
            new File(getFilesDir(), SonicPocketActivity.ROM_NAME).getAbsolutePath()
        };
    }
}
