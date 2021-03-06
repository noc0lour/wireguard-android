package com.wireguard.android.activity;

import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.wireguard.android.R;
import com.wireguard.android.fragment.TunnelDetailFragment;
import com.wireguard.android.fragment.TunnelEditorFragment;
import com.wireguard.android.fragment.TunnelListFragment;
import com.wireguard.android.model.Tunnel;

import java9.util.stream.Stream;

/**
 * CRUD interface for WireGuard tunnels. This activity serves as the main entry point to the
 * WireGuard application, and contains several fragments for listing, viewing details of, and
 * editing the configuration and interface state of WireGuard tunnels.
 */

public class MainActivity extends BaseActivity {
    private static final String KEY_STATE = "fragment_state";
    private static final String TAG = "WireGuard/" + MainActivity.class.getSimpleName();
    private State state = State.EMPTY;

    private boolean moveToState(final State nextState) {
        Log.i(TAG, "Moving from " + state.name() + " to " + nextState.name());
        if (nextState == state) {
            return false;
        } else if (nextState.layer > state.layer + 1) {
            moveToState(State.ofLayer(state.layer + 1));
            moveToState(nextState);
            return true;
        } else if (nextState.layer == state.layer + 1) {
            final Fragment fragment = Fragment.instantiate(this, nextState.fragment);
            final FragmentTransaction transaction = getFragmentManager().beginTransaction()
                    .replace(R.id.master_fragment, fragment)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            if (state.layer > 0)
                transaction.addToBackStack(null);
            transaction.commit();
        } else if (nextState.layer == state.layer - 1) {
            if (getFragmentManager().getBackStackEntryCount() == 0)
                return false;
            getFragmentManager().popBackStack();
        } else if (nextState.layer < state.layer - 1) {
            moveToState(State.ofLayer(state.layer - 1));
            moveToState(nextState);
            return true;
        }
        state = nextState;
        if (state.layer <= State.LIST.layer)
            setSelectedTunnel(null);
        updateActionBar();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (!moveToState(State.ofLayer(state.layer - 1)))
            super.onBackPressed();
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        if (savedInstanceState != null && savedInstanceState.getString(KEY_STATE) != null)
            state = State.valueOf(savedInstanceState.getString(KEY_STATE));
        if (state == State.EMPTY) {
            State initialState = getSelectedTunnel() != null ? State.DETAIL : State.LIST;
            if (getIntent() != null && getIntent().getStringExtra(KEY_STATE) != null)
                initialState = State.valueOf(getIntent().getStringExtra(KEY_STATE));
            moveToState(initialState);
        }
        updateActionBar();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // The back arrow in the action bar should act the same as the back button.
                moveToState(State.ofLayer(state.layer - 1));
                return true;
            case R.id.menu_action_edit:
                if (getSelectedTunnel() != null)
                    moveToState(State.EDITOR);
                return true;
            case R.id.menu_action_save:
                // This menu item is handled by the editor fragment.
                return false;
            case R.id.menu_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        outState.putString(KEY_STATE, state.name());
        super.onSaveInstanceState(outState);
    }


    @Override
    protected void onSelectedTunnelChanged(final Tunnel oldTunnel, final Tunnel newTunnel) {
        moveToState(newTunnel != null ? State.DETAIL : State.LIST);
    }

    private void updateActionBar() {
        if (getActionBar() != null)
            getActionBar().setDisplayHomeAsUpEnabled(state.layer > State.LIST.layer);
    }

    private enum State {
        EMPTY(null, 0),
        LIST(TunnelListFragment.class, 1),
        DETAIL(TunnelDetailFragment.class, 2),
        EDITOR(TunnelEditorFragment.class, 3);

        private final String fragment;
        private final int layer;

        State(final Class<? extends Fragment> fragment, final int layer) {
            this.fragment = fragment != null ? fragment.getName() : null;
            this.layer = layer;
        }

        private static State ofLayer(final int layer) {
            return Stream.of(State.values()).filter(s -> s.layer == layer).findFirst().get();
        }
    }
}
