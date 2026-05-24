package dn.demedallo.admin.ui.nav;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publishes the current navigation trail (main tab → section → sub-tab).
 */
public final class WorkspaceNavigation {

    public record Crumb(String label, Runnable onNavigate) {
        public Crumb {
            Objects.requireNonNull(label, "label");
        }

        public boolean clickable() {
            return onNavigate != null;
        }
    }

    public interface Listener {
        void onTrailChanged(List<Crumb> trail);
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private List<Crumb> trail = List.of();

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public List<Crumb> trail() {
        return Collections.unmodifiableList(trail);
    }

    public void setTrail(Crumb... crumbs) {
        if (crumbs == null || crumbs.length == 0) {
            trail = List.of();
        } else {
            trail = List.of(crumbs);
        }
        fire();
    }

    public void setTrail(List<Crumb> crumbs) {
        trail = crumbs == null ? List.of() : List.copyOf(crumbs);
        fire();
    }

    public void setMainOnly(String mainLabel, Runnable selectMain) {
        setTrail(new Crumb(mainLabel, selectMain));
    }

    public void setMainAndSub(String mainLabel, Runnable selectMain, String subLabel) {
        List<Crumb> crumbs = new ArrayList<>(2);
        crumbs.add(new Crumb(mainLabel, selectMain));
        crumbs.add(new Crumb(subLabel, null));
        setTrail(crumbs);
    }

    public void setMainSectionSub(String mainLabel, Runnable selectMain,
            String sectionLabel, Runnable selectSection, String subLabel) {
        List<Crumb> crumbs = new ArrayList<>(3);
        crumbs.add(new Crumb(mainLabel, selectMain));
        crumbs.add(new Crumb(sectionLabel, selectSection));
        crumbs.add(new Crumb(subLabel, null));
        setTrail(crumbs);
    }

    private void fire() {
        List<Crumb> snapshot = trail;
        for (Listener l : listeners) {
            l.onTrailChanged(snapshot);
        }
    }
}
