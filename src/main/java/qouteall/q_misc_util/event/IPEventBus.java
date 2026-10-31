package qouteall.q_misc_util.event;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * A tiny event bus for Immersive Portals' own events
 * (portal ticks, client cleanup, dimension changes...).
 * Listeners registered for a class also receive events of its subclasses.
 */
public final class IPEventBus {
    public static final IPEventBus EVENT_BUS = new IPEventBus();
    
    private final Map<Class<?>, List<Consumer<Object>>> listeners = new ConcurrentHashMap<>();
    
    private IPEventBus() {}
    
    @SuppressWarnings("unchecked")
    public <E> void addListener(Class<E> eventClass, Consumer<E> listener) {
        listeners.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>())
            .add((Consumer<Object>) listener);
    }
    
    public <E> E post(E event) {
        for (Class<?> c = event.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            List<Consumer<Object>> list = listeners.get(c);
            if (list != null) {
                for (Consumer<Object> listener : list) {
                    listener.accept(event);
                }
            }
        }
        return event;
    }
}
