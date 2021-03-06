package com.wireguard.android.model;

import android.content.SharedPreferences;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.support.annotation.NonNull;

import com.wireguard.android.Application.ApplicationScope;
import com.wireguard.android.BR;
import com.wireguard.android.backend.Backend;
import com.wireguard.android.configStore.ConfigStore;
import com.wireguard.android.model.Tunnel.State;
import com.wireguard.android.model.Tunnel.Statistics;
import com.wireguard.android.util.AsyncWorker;
import com.wireguard.android.util.ExceptionLoggers;
import com.wireguard.android.util.ObservableKeyedList;
import com.wireguard.android.util.ObservableSortedKeyedArrayList;
import com.wireguard.android.util.ObservableSortedKeyedList;
import com.wireguard.config.Config;

import java.util.Collection;
import java.util.Comparator;
import java.util.Set;

import javax.inject.Inject;

import java9.util.Comparators;
import java9.util.concurrent.CompletableFuture;
import java9.util.concurrent.CompletionStage;
import java9.util.stream.Collectors;
import java9.util.stream.StreamSupport;

/**
 * Maintains and mediates changes to the set of available WireGuard tunnels,
 */

@ApplicationScope
public final class TunnelManager extends BaseObservable {
    private static final Comparator<String> COMPARATOR = Comparators.<String>thenComparing(
            String.CASE_INSENSITIVE_ORDER, Comparators.naturalOrder());
    private static final String KEY_LAST_USED_TUNNEL = "last_used_tunnel";
    private static final String KEY_RESTORE_ON_BOOT = "restore_on_boot";
    private static final String KEY_RUNNING_TUNNELS = "enabled_configs";

    private final AsyncWorker asyncWorker;
    private final Backend backend;
    private final ConfigStore configStore;
    private final SharedPreferences preferences;
    private final ObservableSortedKeyedList<String, Tunnel> tunnels =
            new ObservableSortedKeyedArrayList<>(COMPARATOR);
    private Tunnel lastUsedTunnel;

    @Inject
    public TunnelManager(final AsyncWorker asyncWorker, final Backend backend,
                         final ConfigStore configStore, final SharedPreferences preferences) {
        this.asyncWorker = asyncWorker;
        this.backend = backend;
        this.configStore = configStore;
        this.preferences = preferences;
    }

    private Tunnel addToList(final String name, final Config config, final State state) {
        final Tunnel tunnel = new Tunnel(this, name, config, state);
        tunnels.add(tunnel);
        return tunnel;
    }

    public CompletionStage<Tunnel> create(@NonNull final String name, final Config config) {
        if (!Tunnel.isNameValid(name))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid name"));
        if (tunnels.containsKey(name)) {
            final String message = "Tunnel " + name + " already exists";
            return CompletableFuture.failedFuture(new IllegalArgumentException(message));
        }
        return asyncWorker.supplyAsync(() -> configStore.create(name, config))
                .thenApply(savedConfig -> addToList(name, savedConfig, State.DOWN));
    }

    CompletionStage<Void> delete(final Tunnel tunnel) {
        final State originalState = tunnel.getState();
        final boolean wasLastUsed = tunnel == lastUsedTunnel;
        // Make sure nothing touches the tunnel.
        if (wasLastUsed)
            setLastUsedTunnel(null);
        tunnels.remove(tunnel);
        return asyncWorker.runAsync(() -> {
            if (originalState == State.UP)
                backend.setState(tunnel, State.DOWN);
            try {
                configStore.delete(tunnel.getName());
            } catch (final Exception e) {
                if (originalState == State.UP)
                    backend.setState(tunnel, State.UP);
                // Re-throw the exception to fail the completion.
                throw e;
            }
        }).whenComplete((x, e) -> {
            if (e == null)
                return;
            // Failure, put the tunnel back.
            tunnels.add(tunnel);
            if (wasLastUsed)
                setLastUsedTunnel(tunnel);
        });
    }

    @Bindable
    public Tunnel getLastUsedTunnel() {
        return lastUsedTunnel;
    }

    CompletionStage<Config> getTunnelConfig(final Tunnel tunnel) {
        return asyncWorker.supplyAsync(() -> configStore.load(tunnel.getName()))
                .thenApply(tunnel::onConfigChanged);
    }

    CompletionStage<State> getTunnelState(final Tunnel tunnel) {
        return asyncWorker.supplyAsync(() -> backend.getState(tunnel))
                .thenApply(tunnel::onStateChanged);
    }

    CompletionStage<Statistics> getTunnelStatistics(final Tunnel tunnel) {
        return asyncWorker.supplyAsync(() -> backend.getStatistics(tunnel))
                .thenApply(tunnel::onStatisticsChanged);
    }

    public ObservableKeyedList<String, Tunnel> getTunnels() {
        return tunnels;
    }

    public void onCreate() {
        asyncWorker.supplyAsync(configStore::enumerate)
                .thenAcceptBoth(asyncWorker.supplyAsync(backend::enumerate), this::onTunnelsLoaded)
                .whenComplete(ExceptionLoggers.E);
    }

    private void onTunnelsLoaded(final Iterable<String> present, final Collection<String> running) {
        for (final String name : present)
            addToList(name, null, running.contains(name) ? State.UP : State.DOWN);
        final String lastUsedName = preferences.getString(KEY_LAST_USED_TUNNEL, null);
        if (lastUsedName != null)
            setLastUsedTunnel(tunnels.get(lastUsedName));
    }

    public CompletionStage<Void> restoreState() {
        if (!preferences.getBoolean(KEY_RESTORE_ON_BOOT, false))
            return CompletableFuture.completedFuture(null);
        final Set<String> previouslyRunning = preferences.getStringSet(KEY_RUNNING_TUNNELS, null);
        if (previouslyRunning == null)
            return CompletableFuture.completedFuture(null);
        return CompletableFuture.allOf(StreamSupport.stream(tunnels)
                .filter(tunnel -> previouslyRunning.contains(tunnel.getName()))
                .map(tunnel -> setTunnelState(tunnel, State.UP))
                .toArray(CompletableFuture[]::new));
    }

    public void saveState() {
        final Set<String> runningTunnels = StreamSupport.stream(tunnels)
                .filter(tunnel -> tunnel.getState() == State.UP)
                .map(Tunnel::getName)
                .collect(Collectors.toUnmodifiableSet());
        preferences.edit().putStringSet(KEY_RUNNING_TUNNELS, runningTunnels).apply();
    }

    private void setLastUsedTunnel(final Tunnel tunnel) {
        if (tunnel == lastUsedTunnel)
            return;
        lastUsedTunnel = tunnel;
        notifyPropertyChanged(BR.lastUsedTunnel);
        if (tunnel != null)
            preferences.edit().putString(KEY_LAST_USED_TUNNEL, tunnel.getName()).apply();
        else
            preferences.edit().remove(KEY_LAST_USED_TUNNEL).apply();
    }

    CompletionStage<Config> setTunnelConfig(final Tunnel tunnel, final Config config) {
        return asyncWorker.supplyAsync(() -> {
            final Config appliedConfig = backend.applyConfig(tunnel, config);
            return configStore.save(tunnel.getName(), appliedConfig);
        }).thenApply(tunnel::onConfigChanged);
    }

    CompletionStage<String> setTunnelName(final Tunnel tunnel, final String name) {
        if (!Tunnel.isNameValid(name))
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid name"));
        if (tunnels.containsKey(name)) {
            final String message = "Tunnel " + name + " already exists";
            return CompletableFuture.failedFuture(new IllegalArgumentException(message));
        }
        final State originalState = tunnel.getState();
        final boolean wasLastUsed = tunnel == lastUsedTunnel;
        // Make sure nothing touches the tunnel.
        if (wasLastUsed)
            setLastUsedTunnel(null);
        tunnels.remove(tunnel);
        return asyncWorker.supplyAsync(() -> {
            if (originalState == State.UP)
                backend.setState(tunnel, State.DOWN);
            configStore.rename(tunnel.getName(), name);
            final String newName = tunnel.onNameChanged(name);
            if (originalState == State.UP)
                backend.setState(tunnel, State.UP);
            return newName;
        }).whenComplete((newName, e) -> {
            // On failure, we don't know what state the tunnel might be in. Fix that.
            if (e != null)
                getTunnelState(tunnel);
            // Add the tunnel back to the manager, under whatever name it thinks it has.
            tunnels.add(tunnel);
            if (wasLastUsed)
                setLastUsedTunnel(tunnel);
        });
    }

    CompletionStage<State> setTunnelState(final Tunnel tunnel, final State state) {
        // Ensure the configuration is loaded before trying to use it.
        return tunnel.getConfigAsync().thenCompose(x ->
                asyncWorker.supplyAsync(() -> backend.setState(tunnel, state))
        ).whenComplete((newState, e) -> {
            // Ensure onStateChanged is always called (failure or not), and with the correct state.
            tunnel.onStateChanged(e == null ? newState : tunnel.getState());
            if (e == null && newState == State.UP)
                setLastUsedTunnel(tunnel);
        });
    }
}
