package com.wireguard.android.activity;

import android.app.Activity;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;

import com.wireguard.android.Application;
import com.wireguard.android.R;
import com.wireguard.android.backend.WgQuickBackend;

/**
 * Interface for changing application-global persistent settings.
 */

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getFragmentManager().findFragmentById(android.R.id.content) == null) {
            getFragmentManager().beginTransaction()
                    .add(android.R.id.content, new SettingsFragment())
                    .commit();
        }
    }

    public static class SettingsFragment extends PreferenceFragment {
        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.preferences);
            if (Application.getComponent().getBackendType() != WgQuickBackend.class) {
                final Preference toolsInstaller =
                        getPreferenceManager().findPreference("tools_installer");
                getPreferenceScreen().removePreference(toolsInstaller);
            }
        }
    }
}
