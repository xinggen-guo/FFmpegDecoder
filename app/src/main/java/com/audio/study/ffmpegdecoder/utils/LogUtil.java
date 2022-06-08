package com.audio.study.ffmpegdecoder.utils;

import android.os.Process;
import android.text.TextUtils;
import android.util.Log;

public final class LogUtil {

    private static final String TAG = "StarMaker";
    /** Debug开关 */
    public static final boolean DEBUG = true;

    private static final boolean LOGV = DEBUG;
    private static final boolean LOGD = DEBUG;
    private static final boolean LOGI = DEBUG;
    private static final boolean LOGW = DEBUG;
    private static final boolean LOGE = DEBUG;

    private static final String TAG_CONTENT_PRINT = "%s:%s.%s:%d";


    /** default constructor */
    private LogUtil() {
    }

    /**
     * log for berbose
     * 
     * @param tag
     *            log tag
     * @param msg
     *            log msg
     */
    private static void v0(String tag, String msg) { // NO_UCD (use default)
        if (LOGV) {
            if(TextUtils.isEmpty(msg)){
                msg="";
            }
            if (TextUtils.isEmpty(tag)) {
                Log.v(TAG, getContent(getCurrentStackTraceElement()) + "->" + msg);
            } else {
                Log.v(tag, getContent(getCurrentStackTraceElement()) + "->" + msg);
            }
        }
    }
    public static void v(String tag, String msg){
        v0(tag,msg);
    }

    /**
     * log for berbose
     *
     * @param msg
     *            log msg
     */
    public static void v(String msg) {
        v0(TAG, msg);
    }

    /**
     * log for debug
     * 
     * @param tag
     *            log tag
     * @param msg
     *            log msg
     */
    private static void d0(String tag, String msg) {
        if (LOGD) {
            if(TextUtils.isEmpty(msg)){
                msg="";
            }
            if (TextUtils.isEmpty(tag)) {
                Log.d(TAG, getContent(getCurrentStackTraceElement()) + "->" + msg);
            } else {
                Log.d(tag, getContent(getCurrentStackTraceElement()) + "->" + msg);
            }
        }
    }
    public static void d(String tag, String msg) {
        d0(tag, msg);
    }

    /**
     * log for debug
     * 
     * @param msg
     *            log msg
     */
    public static void d(String msg) {
        d0(TAG, msg);
    }

    /**
     * log for information
     * 
     * @param tag
     *            log tag
     * @param msg
     *            log msg
     */
    private static void i0(String tag, String msg) {
        if (LOGI) {
            if(TextUtils.isEmpty(msg)){
                msg="";
            }
            if (TextUtils.isEmpty(tag)) {
                Log.i(TAG, getContent(getCurrentStackTraceElement()) + "->" + msg);
            } else {
                Log.i(tag, getContent(getCurrentStackTraceElement()) + "->" + msg);
            }
        }
    }

    public static void i(String tag, String msg) {
        i0(tag, msg);
    }

    /**
     * log for information
     *
     * @param msg
     *            log msg
     */
    public static void i(String msg) {
        i0(TAG, msg);
    }

    private static void i1(String tag, String msg,Object... args){
        if (LOGI) {
            if(TextUtils.isEmpty(msg)){
                msg="";
            }
            Log.i(tag, getContent(getCurrentStackTraceElement()) + "->" + fmt(msg, args));
        }
    }

    public static void i(String tag, String msg,Object... args){
        i1(tag, msg, args);
    }



    /**
     * log for warning
     * 
     * @param tag
     *            log tag
     * @param msg
     *            log msg
     */
    private static void w0(String tag, String msg) { // NO_UCD (unused code)
        if (LOGW) {
            if(TextUtils.isEmpty(msg)){
                msg="";
            }
            if (TextUtils.isEmpty(tag)) {
                Log.w(TAG, getContent(getCurrentStackTraceElement()) + "->" + msg);
            } else {
                Log.w(tag, getContent(getCurrentStackTraceElement()) + "->" + msg);
            }
        }
    }

    public static void w(String tag, String msg) {
        w0(tag,msg);
    }

    /**
     * log for warning
     *
     * @param msg
     *            log msg
     */
    public static void w(String msg) {
        w0(TAG, msg);
    }

    /**
     * log for error
     * 
     * @param tag
     *            log tag
     * @param msg
     *            log msg
     */
    private static void e0(String tag, String msg) {
        if (LOGE) {
            if(TextUtils.isEmpty(msg)){
                msg="";
            }
            if (TextUtils.isEmpty(tag)) {
                Log.e(TAG, getContent(getCurrentStackTraceElement()) + "->" + msg);
            } else {
                Log.e(tag, getContent(getCurrentStackTraceElement()) + "->" + msg);
            }
        }
    }

    public static void e(String tag, String msg) {
        e0(tag, msg);
    }

    /**
     * log for error
     *
     * @param msg
     *            log msg
     */
    public static void e(String msg) {
        e0(TAG, msg);
    }

    /**
     * cf
     * 
     * @param str
     *            msg
     */
    public static void logd(String str) {
        if (LOGD) {
            Log.i(TAG, getTAG() + "---" + str + "#pid=" + Process.myPid());
        }
    }

    /**
     * cf
     * 
     * @param tag
     *            log tag
     * @param str
     *            msg
     */
    public static void logd(String tag, String str) { // NO_UCD (unused code)
        if (LOGD) {
            Log.i(tag, getTAG() + "---" + str);
        }
    }

    /**
     * cf
     * 
     * @param str
     *            msg
     */
    public static void errord(String str) {
        if (LOGE) {
            Log.e(TAG, getTAG() + "---" + str);
        }
    }

    /**
     * cf
     * 
     * @param tag
     *            log tag
     * @param str
     *            msg
     */
    public static void errord(String tag, String str) { // NO_UCD (unused code)
        if (LOGE) {
            Log.e(tag, getTAG() + "---" + str);
        }
    }

    /**
     * cf
     */
    public static void mark() {
        if (DEBUG) {
            Log.w(TAG, getTAG());
        }
    }

    /**
     * cf
     * 
     * @param str
     *            msg
     */
    public static void mark(String str) { // NO_UCD (unused code)
        if (DEBUG) {
            Log.w(TAG, getTAG() + "---" + str);
        }
    }

    /**
     * cf
     */
    public static void traces() {
        if (DEBUG) {
            StackTraceElement[] stacks = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            if (stacks != null) {
                final int NUMBER_3 = 3;
                final int NUMBER_4 = 4;
                final int NUMBER_15 = 15;
                StackTraceElement ste = stacks[NUMBER_3];
                sb.append(ste.getClassName() + "." + ste.getMethodName() + "#line=" + ste.getLineNumber() + "的调用：\n");
                for (int i = NUMBER_4; i < stacks.length && i < NUMBER_15; i++) {
                    ste = stacks[i];
                    sb.append((i - NUMBER_4)
                              + "--"
                              + ste.getClassName()
                              + "."
                              + ste.getMethodName()
                              + "(...)#line:"
                              + ste.getLineNumber()
                              + "\n");
                }
            }
            Log.w(TAG, getTAG() + "--" + sb.toString());
        }
    }

    /**
     * cf
     * 
     * @return tag
     */
    public static String getTAG() { // NO_UCD (use private)
        // XXX this not work with proguard, maybe we cannot get the line number
        // with a proguarded jar file.
        // I add a try/catch as a quick fixing.
        try {
            final int NUMBER_4 = 4;
            final int NUMBER_5 = 5;
            StackTraceElement[] stacks = Thread.currentThread().getStackTrace();
            StringBuilder sb = new StringBuilder();
            if (stacks != null) {
                StackTraceElement ste = stacks[NUMBER_4];
                sb.append(ste.getFileName().subSequence(0, ste.getFileName().length() - NUMBER_5)
                          + "."
                          + ste.getMethodName()
                          + "#"
                          + ste.getLineNumber());
            }
            return sb.toString();
        } catch (NullPointerException e) {
            return "PROGUARDED";
        }
    }

    //获取LOG
    private static String getContent(StackTraceElement trace) {
        return String.format(TAG_CONTENT_PRINT, TAG,
                trace.getClassName(), trace.getMethodName(),
                trace.getLineNumber());
    }

    private static StackTraceElement getCurrentStackTraceElement() {
        return Thread.currentThread().getStackTrace()[5];
    }

    private static String fmt(String msg, Object... args) {
        String log;
        try {
            log = String.format(msg, args);
        } catch (Exception e) {
            return msg;
        }
        return log;
    }

}
