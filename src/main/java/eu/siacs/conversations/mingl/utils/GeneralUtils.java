package eu.siacs.conversations.mingl.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.inputmethod.InputMethodManager;

import java.util.Map;

import eu.siacs.conversations.R;

public class GeneralUtils {
    public static final String TAG = GeneralUtils.class.getSimpleName();
    public static final String PREFERENCES = "_MyPref";
    public static Context context;

    public GeneralUtils(Context context) {
        GeneralUtils.context = context;
    }

    public static SharedPreferences initSharedPreference() {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    public static void saveOrUpdatePreference(String KEY, String value) {
        SharedPreferences sharedPreferences = initSharedPreference();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY, value);
        editor.commit();
    }

    public static String getPreferenceValue(String KEY) {
        SharedPreferences sharedPreferences = initSharedPreference();
        return sharedPreferences.getString(KEY, "");
    }

    public static boolean hasPreferenceKey(String KEY) {
        SharedPreferences sharedPreferences = initSharedPreference();
        return sharedPreferences.contains(KEY);
    }

    public static void saveOrUpdateBooleanPreference(String KEY, boolean value) {
        SharedPreferences sharedPreferences = initSharedPreference();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(KEY, value);
        editor.commit();
    }

    public static boolean getBooleanPreferenceValue(String KEY, boolean defaultValue) {
        SharedPreferences sharedPreferences = initSharedPreference();
        return sharedPreferences.getBoolean(KEY, defaultValue);
    }

    public static void deletePreferenceKey(String KEY) {
        SharedPreferences sharedPreferences = initSharedPreference();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.remove(KEY);
        editor.commit();
    }

    public static void deletePreferenceKeys(String[] KEYS) {
        SharedPreferences sharedPreferences = initSharedPreference();
        SharedPreferences.Editor editor = sharedPreferences.edit();
        for (String KEY : KEYS) {
            editor.remove(KEY);
        }
        editor.commit();
    }

    public static void hideSoftKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(activity.getWindow().getDecorView().getRootView().getWindowToken(), 0);
    }

//    public static void showHidePassword(ImageView imageView, EditText editText) {
//        if (editText.getTransformationMethod().equals(HideReturnsTransformationMethod.getInstance())) {
//            imageView.setImageResource(R.drawable.ic_eye_show);
//            //Hide Password
//            editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
//        } else {
//            imageView.setImageResource(R.drawable.ic_eye_hide);
//            //Show Password
//            editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
//        }
//        if (editText.getText() != null)
//            editText.setSelection(editText.getText().length());
//    }

    public <T> T openActivity(Activity activity, Class<T> valueType, Map<String, String> values) {
        return this.openActivity(activity, valueType, values, false);
    }

    public <T> T openActivity(Activity activity, Class<T> valueType, Map<String, String> values, boolean clearTop) {
        Intent intent = new Intent(activity, valueType);
        if (clearTop) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            //intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (values != null && !values.isEmpty()) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                intent.putExtra(entry.getKey(), entry.getValue());
            }
        }

        activity.startActivity(intent);
        activity.overridePendingTransition(R.anim.left_in, R.anim.left_out);
        return null;
    }
}
