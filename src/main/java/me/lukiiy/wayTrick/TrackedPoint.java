package me.lukiiy.wayTrick;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Function;

public class TrackedPoint {
    private final UUID id = UUID.randomUUID();
    private final LocatorBar bar;
    private Location location;
    private Player target;
    private Function<Player, Location> positionFunction;
    private Color color;
    private NamespacedKey style;

    TrackedPoint(LocatorBar bar, Location location, Player target, Function<Player, Location> positionFunction) {
        this.bar = bar;
        this.location = location == null ? null : location.clone();
        this.target = target;
        this.positionFunction = positionFunction;
    }

    public UUID getId() {
        return id;
    }

    public TrackedPoint position(Location location) {
        this.location = location.clone();
        target = null;
        positionFunction = null;

        bar.tracker().changed(this);
        return this;
    }

    public TrackedPoint track(Player target) {
        this.target = target;
        location = null;
        positionFunction = null;

        bar.tracker().changed(this);
        return this;
    }

    public TrackedPoint position(Function<Player, Location> positionFunction) {
        this.positionFunction = positionFunction;
        location = null;
        target = null;

        bar.tracker().changed(this);
        return this;
    }

    public TrackedPoint color(Color color) {
        this.color = color;

        bar.tracker().changed(this);
        return this;
    }

    public TrackedPoint style(NamespacedKey style) {
        this.style = style;

        bar.tracker().changed(this);
        return this;
    }

    public TrackedPoint showTo(Player viewer) {
        bar.tracker().showTo(this, viewer);

        return this;
    }

    public TrackedPoint showToAll() {
        bar.getViewers().forEach(this::showTo);

        return this;
    }

    public TrackedPoint hideFrom(Player viewer) {
        bar.tracker().hideFrom(this, viewer);

        return this;
    }

    public TrackedPoint hideFromAll() {
        bar.tracker().hideFromAll(this);

        return this;
    }

    public void remove() {
        bar.remove(this);
    }

    public Location resolvePosition(Player viewer) {
        if (positionFunction != null) {
            Location result = positionFunction.apply(viewer);

            return result == null ? null : result.clone();
        }

        if (target != null) return target.isOnline() ? target.getLocation() : null;

        return location == null ? null : location.clone();
    }

    public Player targetPlayer() {
        return target;
    }

    public boolean isClientPosition() {
        return positionFunction != null;
    }

    public Color color() {
        return color;
    }

    public NamespacedKey style() {
        return style;
    }
}