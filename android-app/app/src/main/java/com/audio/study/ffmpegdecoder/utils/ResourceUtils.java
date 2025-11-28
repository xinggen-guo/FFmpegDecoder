package com.audio.study.ffmpegdecoder.utils;

import android.content.ClipData;
import android.content.ClipData.Item;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;

import com.audio.study.ffmpegdecoder.App;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Locale;

import androidx.annotation.AnyRes;
import androidx.annotation.ArrayRes;
import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.annotation.DimenRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.FractionRes;
import androidx.annotation.IntegerRes;
import androidx.annotation.PluralsRes;
import androidx.annotation.StringRes;
import androidx.core.text.BidiFormatter;
import androidx.core.text.TextDirectionHeuristicsCompat;

/**
 * 资源操作工具
 */

public class ResourceUtils {

    private static Context getContext() {
        return App.INSTANCE;
    }

    /**
     * Application context为空时，返回空字符串，增加健壮性
     */
    public static String getQuantityString(@PluralsRes int id, int quantity, Object... formatArgs) {
        return getContext() != null ? getContext()
                .getResources().getQuantityString(id, quantity, formatArgs) : "";
    }

    /**
     * Application context为空时，返回空字符串，增加健壮性
     */
    public static String getQuantityString(@PluralsRes int id, int count) {
        return getContext() != null ? getContext()
                .getResources().getQuantityString(id, count, count) : "";
    }

    /**
     * Application context为空时，返回空字符串，增加健壮性
     */
    public static String getString(@StringRes int resId) {
        return getContext() != null ? getContext().getResources().getString(resId) : "";
    }

    /**
     * Application context为空时，返回-1
     */
    public static int getInt(@IntegerRes int resId) {
        return getContext() != null ? getContext().getResources().getInteger(resId) : -1;
    }

    public static float getDimension(@DimenRes int resId) {
        return getContext() != null ? getContext().getResources().getDimension(resId) : -1;
    }

    public static int getDimensionPixelSize(@DimenRes int resId) {
        return getContext() != null ? getContext().getResources().getDimensionPixelSize(resId) : -1;
    }

    public static float getFraction(@FractionRes int resId) {
        return getFraction(resId, 1);
    }

    public static float getFraction(@FractionRes int resId, int base) {
        return getFraction(resId, base, base);
    }

    public static float getFraction(@FractionRes int resId, int base, int pbase) {
        return getContext() != null ? getContext().getResources().getFraction(resId, base, pbase) : 0;
    }

    /**
     * Application context为空时，返回空字符串，增加健壮性
     */
    public static String getString(@StringRes int resId, Object... formatArgs) {
        try {
            return getContext() != null ? getContext().getResources().getString(resId, formatArgs) : "";
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String[] getStringArray(@ArrayRes int strArrayId) {
        try {
            return getContext() != null ? getContext().getResources().getStringArray(strArrayId) : new String[]{};
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[]{};
    }

    public static int[] getIntArray(@ArrayRes int intArrayId) {
        try {
            return getContext() != null ? getContext().getResources().getIntArray(intArrayId) : new int[]{};
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new int[]{};
    }

    /**
     * Application context为空时，返回黑色，增加健壮性
     */
    public static int getColor(@ColorRes int resId) {
        try {
            if (resId == -1) {
                return Color.BLACK;
            }
            return getContext() != null ? getContext().getResources().getColor(resId) : Color.BLACK;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Color.BLACK;
    }


    /**
     * @param color #ffffffff
     * @return
     */
    public static int getColorFromHex(String color) {
        try {
            return Color.parseColor(color);
        } catch (Exception e) {
            e.printStackTrace();
            return 0x00ffffff;
        }
    }

    @ColorInt
    public static int getColorByAttributeId(Context context, @AttrRes int attrIdForColor) {
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = null;
        if (context != null) {
            theme = context.getTheme();
        }
        if (theme != null) {
            theme.resolveAttribute(attrIdForColor, typedValue, true);
        }
        return typedValue.data;
    }

    /**
     * Application context为空时，返回透明图，增加健壮性
     */
    public static Drawable getDrawable(@DrawableRes int resId) {
        try {
            return getContext() != null ? getContext().getResources().getDrawable(resId) : new ColorDrawable(Color.TRANSPARENT);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new ColorDrawable(Color.TRANSPARENT);
    }

    public static Bitmap getBitmap(@DrawableRes int resId) {
        if (getContext() != null) {
            Drawable drawable = getContext().getResources().getDrawable(resId);
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }
        }
        return null;
    }

    public static Bitmap getBitmapForDensity(@DrawableRes int resId, int density) {
        if (getContext() != null) {
            Drawable drawable = getContext().getResources().getDrawableForDensity(resId, density);
            if (drawable instanceof BitmapDrawable) {
                return ((BitmapDrawable) drawable).getBitmap();
            }
        }
        return null;
    }

    public static @DrawableRes
    int getDrawableIdByName(String resName) {
        int resId = 0;

        if (getContext() != null) {
            try {
                resId = getContext().getResources().getIdentifier(resName, "drawable", getContext().getPackageName());
            } catch (Exception e) {

            }
        }

        return resId;
    }

    /**
     * dip转换成px
     *
     * @param dipValue
     * @return
     */
    public static int dip2px(float dipValue) {
        Context context = getContext();
        if (context == null) {
            return 0;
        }
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    public static int dip2px(int dipValue) {
        Context context = getContext();
        if (context == null) {
            return 0;
        }
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dipValue * scale + 0.5f);
    }

    public static float sp2px(float spValue) {
        final float fontScale = App.INSTANCE.getResources().getDisplayMetrics().scaledDensity;
        return (spValue * fontScale + 0.5f);
    }

    public static float getDensity() {
        Context context = getContext();
        if (context == null) {
            return 0;
        }
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        return displayMetrics.density;
    }

    public static int getDensityDpi() {
        Context context = getContext();
        if (context == null) {
            return 0;
        }
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        return displayMetrics.densityDpi;
    }

    public static String getDeviceDensity() {
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        float density = displayMetrics.density;
        String deviceDensity;
        if (density <= 1) {
            deviceDensity = "mdpi";
        } else if (density < 2) {
            deviceDensity = "hdpi";
        } else if (density < 2.5) {
            deviceDensity = "xhdpi";
        } else if (density <= 3) {
            deviceDensity = "xxhdpi";
        } else {
            deviceDensity = "xxxhdpi";
        }
        return deviceDensity;
    }


    public static LayoutInflater getLayoutInflater() {
        return LayoutInflater.from(getContext());
    }

    public static String getResourceUri(@AnyRes int resId) {
        Resources resources = getContext().getResources();
        return String.format("%s://%s/%s/%s",
                ContentResolver.SCHEME_ANDROID_RESOURCE,
                resources.getResourcePackageName(resId),
                resources.getResourceTypeName(resId),
                resources.getResourceEntryName(resId));
    }

    public static String assetFileToStr(Context c, String urlStr) {
        InputStream in = null;
        try {
            in = c.getAssets().open(urlStr);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(in));
            String line = null;
            StringBuilder sb = new StringBuilder();
            do {
                line = bufferedReader.readLine();
                if (line != null && !line.matches("^\\s*\\/\\/.*")) {
                    sb.append(line);
                }
            } while (line != null);

            bufferedReader.close();
            in.close();

            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                }
            }
        }
        return null;
    }

    public static void copy(String label, String content) {
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(label, content));
        }
    }

    public static void clearClipboard() {
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboardManager != null) {
            clipboardManager.setPrimaryClip(ClipData.newPlainText(null, null));
        }
    }

    public static String paste() {
        try {
            ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboardManager != null && clipboardManager.hasPrimaryClip()) {
                ClipData primaryClip = clipboardManager.getPrimaryClip();
                if (primaryClip != null && primaryClip.getItemCount() > 0) {
                    Item item = primaryClip.getItemAt(0);
                    if (item != null && item.getText() != null) {
                        return item.getText().toString();
                    }
                }
            }
        }catch (Throwable e){
            e.printStackTrace();
        }
        return "";
    }

    //判断是否镜像
    public static boolean isLayoutRtl() {
        Configuration mConfig = getContext().getResources().getConfiguration();
        return mConfig.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    //判断是否是右到左语言
    public static boolean isLanguageRtl() {
        // return Locale.getDefault().getLanguage().equalsIgnoreCase("ar");
        char ch = Locale.getDefault().getDisplayName().charAt(0);
        final int directionality = Character.getDirectionality(ch);
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT ||
                directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
    }

    private static final BidiFormatter BIDI_INSTANCE = BidiFormatter.getInstance(Locale.getDefault());

    //处理阿拉伯语下的混合文案,英文标点显示问题
    public static CharSequence getRtlText(CharSequence text) {
        if (isLanguageRtl()) {
            return BIDI_INSTANCE
                    .unicodeWrap(text, TextDirectionHeuristicsCompat.ANYRTL_LTR);
        }
        return text;
    }

    public static CharSequence getRtlTexttFirstStrong(CharSequence text) {
        if (isLanguageRtl()) {
            return BIDI_INSTANCE
                    .unicodeWrap(text, TextDirectionHeuristicsCompat.FIRSTSTRONG_LTR);
        }
        return text;
    }

    public static CharSequence getLocaleText(CharSequence text) {
        return BIDI_INSTANCE.unicodeWrap(text, TextDirectionHeuristicsCompat.LOCALE);
    }

    /**
     * 扩大View的触摸和点击响应范围,最大不超过其父View范围
     *
     * @param view
     * @param top
     * @param bottom
     * @param left
     * @param right
     */
    public static void expandViewTouchDelegate(final View view, final int top,
                                               final int bottom, final int left, final int right) {

        ((View) view.getParent()).post(() -> {
            Rect bounds = new Rect();
            view.setEnabled(true);
            view.getHitRect(bounds);

            bounds.top -= top;
            bounds.bottom += bottom;
            bounds.left -= left;
            bounds.right += right;

            TouchDelegate touchDelegate = new TouchDelegate(bounds, view);

            if (View.class.isInstance(view.getParent())) {
                ((View) view.getParent()).setTouchDelegate(touchDelegate);
            }
        });
    }

}
