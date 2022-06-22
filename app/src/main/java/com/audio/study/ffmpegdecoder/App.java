package com.audio.study.ffmpegdecoder;

import android.app.Application;

import com.audio.study.ffmpegdecoder.utils.LogUtil;

import androidx.annotation.Keep;

/**
 * Created by quan.zhou on 2017/8/5.
 */

@Keep
public class App {

    @Keep
    public static Application INSTANCE ;//= AppGlobals.getInitialApplication();

    static {
        Application app = null;
        try {
            app = (Application) Class.forName("android.app.AppGlobals").getMethod("getInitialApplication").invoke(null);
            if (app == null) {
                throw new IllegalStateException("Static initialization of Applications must be on main thread.");
            }
        } catch (final Exception e) {
            LogUtil.e("Failed to get current application from AppGlobals." + e.getMessage());
            try {
                app = (Application) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            } catch (final Exception ex) {
                LogUtil.e("Failed to get current application from ActivityThread." + e.getMessage());
            }
        } finally {
            INSTANCE = app;
        }
    }
}
