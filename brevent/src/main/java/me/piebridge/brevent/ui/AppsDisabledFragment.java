package me.piebridge.brevent.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemProperties;
import android.preference.PreferenceManager;
import android.system.ErrnoException;
import android.system.Os;
import android.widget.Toast;

import java.io.File;

import me.piebridge.brevent.BuildConfig;
import me.piebridge.brevent.R;
import me.piebridge.brevent.override.HideApiOverride;
import me.piebridge.brevent.protocol.BreventConfiguration;

/**
 * Created by thom on 2017/2/5.
 */
public class AppsDisabledFragment extends AbstractDialogFragment
        implements DialogInterface.OnClickListener {

    private static final String TITLE = "title";

    private static final String USB_CONNECTED = "USB_CONNECTED";

    private static final int DEFAULT_TITLE = R.string.brevent_service_start;

    public AppsDisabledFragment() {
        setArguments(new Bundle());
    }

    private static Boolean root;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BreventActivity activity = (BreventActivity) getActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setIcon(BuildConfig.ICON);
        Bundle arguments = getArguments();
        builder.setTitle(getString(arguments.getInt(TITLE, DEFAULT_TITLE),
                BuildConfig.VERSION_NAME));
        boolean adbRunning = "running".equals(SystemProperties.get("init.svc.adbd", Build.UNKNOWN));
        String adbStatus = adbRunning ? getString(R.string.brevent_service_adb_running) : "";
        IntentFilter filter = new IntentFilter(HideApiOverride.ACTION_USB_STATE);
        Intent intent = activity.registerReceiver(null, filter);
        boolean usb = intent != null && intent.getBooleanExtra(HideApiOverride.USB_CONNECTED, false);
        arguments.putBoolean(USB_CONNECTED, usb);
        String commandLine = getBootstrapCommandLine(activity, usb);
        String usbStatus = usb ? getString(R.string.brevent_service_usb_connected) : "";
        builder.setMessage(getString(R.string.brevent_service_guide,
                adbStatus, usbStatus, commandLine));
        if (activity.canFetchLogs()) {
            builder.setNeutralButton(R.string.menu_logs, this);
        } else {
            builder.setNeutralButton(R.string.menu_guide, this);
        }
        if (allowRoot(activity)) {
            builder.setNegativeButton(R.string.brevent_service_run_as_root, this);
        } else {
            if (usb) {
                builder.setPositiveButton(R.string.brevent_service_copy_path, this);
            } else if (hasRoot()) {
                builder.setNegativeButton(R.string.brevent_service_run_as_root, this);
            } else {
                builder.setPositiveButton(R.string.brevent_service_open_development, this);
            }
        }
        return builder.create();
    }

    static boolean isEmulator() {
        return "1".equals(SystemProperties.get("ro.kernel.qemu", Build.UNKNOWN));
    }

    private static boolean allowRoot(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(BreventConfiguration.BREVENT_ALLOW_ROOT, false);
    }

    private static String getBootstrapCommandLine(BreventActivity activity, boolean usb) {
        BreventApplication application = (BreventApplication) activity.getApplication();
        String path = application.copyBrevent();
        if (path != null) {
            StringBuilder sb = new StringBuilder();
            if (isEmulator()) {
                sb.append("adb -e shell ");
            } else if (usb) {
                sb.append("adb -d shell ");
            } else {
                sb.append("adb shell ");
            }
            sb.append("sh ");
            sb.append(path);
            return sb.toString();
        } else {
            return activity.getString(R.string.unsupported_path);
        }
    }

    public void setTitle(int title) {
        getArguments().putInt(TITLE, title);
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        BreventActivity activity = (BreventActivity) getActivity();
        if (activity == null) {
            return;
        }
        if (which == DialogInterface.BUTTON_POSITIVE) {
            IntentFilter filter = new IntentFilter(HideApiOverride.ACTION_USB_STATE);
            Intent intent = activity.registerReceiver(null, filter);
            boolean usb = intent != null && intent.getBooleanExtra(HideApiOverride.USB_CONNECTED,
                    false);
            if (usb) {
                String commandLine = getBootstrapCommandLine(activity, true);
                activity.copy(commandLine);
                String message = getString(R.string.brevent_service_command_copied, commandLine);
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            } else {
                intent = new Intent();
                intent.setComponent(new ComponentName("com.android.settings",
                        "com.android.settings.DevelopmentSettings"));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                try {
                    startActivity(intent);
                    activity.finish();
                } catch (ActivityNotFoundException e) {
                    UILog.d("Can't find settings", e);
                }
            }
            activity.showDisabled(getArguments().getInt(TITLE, DEFAULT_TITLE), true);
        } else if (which == DialogInterface.BUTTON_NEUTRAL) {
            if (activity.canFetchLogs()) {
                activity.fetchLogs();
            } else {
                activity.openGuide("disabled");
            }
            dismiss();
        } else if (which == DialogInterface.BUTTON_NEGATIVE) {
            activity.runAsRoot();
            dismiss();
        }
    }

    public int getTitle() {
        return getArguments().getInt(TITLE);
    }

    public boolean isConnected() {
        return getArguments().getBoolean(USB_CONNECTED, false);
    }

    public static boolean hasRoot() {
        if (root != null) {
            return root;
        }
        for (String path : System.getenv("PATH").split(":")) {
            File su = new File(path, "su");
            try {
                if (Os.access(su.getPath(), 1)) {
                    UILog.d("has su: " + su);
                    root = true;
                    return true;
                }
            } catch (ErrnoException e) {
                UILog.d("Cannot access " + su, e);
            }
        }
        root = false;
        UILog.d("has no su");
        return false;
    }

}
