package me.lukiiy.wayTrick;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.function.Function;

public class LocatorBar implements AutoCloseable { // well this is cool!
    private final Plugin plugin;
    private final Map<UUID, TrackedPoint> waypoints = new LinkedHashMap<>();
    private final Map<UUID, Player> viewers = new LinkedHashMap<>();
    private Tracker tracker;

    public LocatorBar(Plugin plugin) {
        this.plugin = plugin;
        this.tracker = new Tracker(this);
    }

    public TrackedPoint add(Location location) {
        return add(new TrackedPoint(this, location, null, null));
    }

    public TrackedPoint add(Function<Player, Location> position) {
        return add(new TrackedPoint(this, null, null, position));
    }

    public TrackedPoint track(Player target) {
        return add(new TrackedPoint(this, null, target, null));
    }

    public Collection<TrackedPoint> waypoints() {
        return new ArrayList<>(waypoints.values());
    }

    Collection<TrackedPoint> pointsView() {
        return waypoints.values();
    }

    public Collection<Player> getViewers() {
        return new ArrayList<>(viewers.values());
    }

    public void showTo(Player viewer) {
        UUID id = viewer.getUniqueId();
        boolean added = !viewers.containsKey(id);

        viewers.put(id, viewer);
        if (added) tracker.showAllTo(viewer);
    }

    public void hideFrom(Player viewer) {
        viewers.remove(viewer.getUniqueId());
        tracker.hideAllFrom(viewer);
    }

    public boolean isViewer(Player player) {
        return viewers.containsKey(player.getUniqueId());
    }

    public Plugin plugin() {
        return plugin;
    }

    public Tracker tracker() {
        return tracker;
    }

    boolean contains(TrackedPoint trackedPoint) {
        return waypoints.get(trackedPoint.getId()) == trackedPoint;
    }

    private TrackedPoint add(TrackedPoint trackedPoint) {
        waypoints.put(trackedPoint.getId(), trackedPoint);
        tracker.register(trackedPoint);

        return trackedPoint;
    }

    void remove(TrackedPoint trackedPoint) {
        if (waypoints.remove(trackedPoint.getId(), trackedPoint)) tracker.unregister(trackedPoint);
    }

    @Override
    public void close() {
        if (tracker == null) return;

        tracker.close();
        waypoints.clear();
        viewers.clear();

        tracker = null;
    }
}
