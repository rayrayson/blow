/*
 * Copyright (c) 2012, the authors.
 *
 *   This file is part of Blow.
 *
 *   Blow is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   Blow is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with Blow.  If not, see <http://www.gnu.org/licenses/>.
 */

package blow.eventbus;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.*;
import com.google.common.eventbus.DeadEvent;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.logging.Logger;

/**
 * NOTE: THIS FILE IS A DERIVATIVE WORK OF THE GOOGLE GUAVA LIBRARY
 *
 *
 * @author Paolo Di Tommaso
 */

@Beta
public class OrderedEventBus {

  /**
   * All registered event handlers, indexed by event type.
   */
  private final ListMultimap<Class<?>, EventHandler> handlersByType =
      Multimaps.newListMultimap(new ConcurrentHashMap<Class<?>, Collection<EventHandler>>(),
          new Supplier<List<EventHandler>>() {
            @Override
            public List<EventHandler> get() {
              return new CopyOnWriteArrayList<EventHandler>();
            }
          });

  /**
   * Logger for event dispatch failures.  Named by the fully-qualified name of
   * this class, followed by the identifier provided at construction.
   */
  private final Logger logger;

  /**
   * Strategy for finding handler methods in registered objects.  Currently,
   * only the {@link AnnotatedHandlerFinder} is supported, but this is
   * encapsulated for future expansion.
   */
  private final HandlerFindingStrategy finder = new AnnotatedHandlerFinder();

  /** queues of events for the current thread to dispatch */
  private final ThreadLocal<ConcurrentLinkedQueue<EventWithHandler>>
      eventsToDispatch =
      new ThreadLocal<ConcurrentLinkedQueue<EventWithHandler>>() {
    @Override protected ConcurrentLinkedQueue<EventWithHandler> initialValue() {
      return new ConcurrentLinkedQueue<EventWithHandler>();
    }
  };

  /** true if the current thread is currently dispatching an event */
  private final ThreadLocal<Boolean> isDispatching = new ThreadLocal<Boolean>() {
    @Override protected Boolean initialValue() {
      return false;
    }
  };

  /**
   * A thread-safe cache for flattenHierarch(). The Class class is immutable.
   */
  private LoadingCache<Class<?>, Set<Class<?>>> flattenHierarchyCache =
      CacheBuilder.newBuilder()
          .weakKeys()
          .build(new CacheLoader<Class<?>, Set<Class<?>>>() {
            @Override
            public Set<Class<?>> load(Class<?> concreteClass) throws Exception {
              List<Class<?>> parents = Lists.newLinkedList();
              Set<Class<?>> classes = Sets.newHashSet();

              parents.add(concreteClass);

              while (!parents.isEmpty()) {
                Class<?> clazz = parents.remove(0);
                classes.add(clazz);

                Class<?> parent = clazz.getSuperclass();
                if (parent != null) {
                  parents.add(parent);
                }

                for (Class<?> iface : clazz.getInterfaces()) {
                  parents.add(iface);
                }
              }

              return classes;
            }
          });

  /**
   * Creates a new EventBus named "default".
   */
  public OrderedEventBus() {
    this("default");
  }

  /**
   * Creates a new EventBus with the given {@code identifier}.
   *
   * @param identifier  a brief name for this bus, for logging purposes.  Should
   *                  be a valid Java identifier.
   */
  public OrderedEventBus(String identifier) {
    logger = Logger.getLogger(OrderedEventBus.class.getName() + "." + identifier);
  }

  /**
   * Registers all handler methods on {@code object} to receive events.
   * Handler methods are selected and classified using this EventBus's
   * {@link HandlerFindingStrategy}; the default strategy is the
   * {@link AnnotatedHandlerFinder}.
   *
   * @param object  object whose handler methods should be registered.
   */
  public void register(Object object) {
    handlersByType.putAll(finder.findAllHandlers(object));
  }

  /**
   * Unregisters all handler methods on a registered {@code object}.
   *
   * @param object  object whose handler methods should be unregistered.
   * @throws IllegalArgumentException if the object was not previously registered.
   */
  public void unregister(Object object) {
    Multimap<Class<?>, EventHandler> methodsInListener = finder.findAllHandlers(object);
    for (Entry<Class<?>, Collection<EventHandler>> entry : methodsInListener.asMap().entrySet()) {
      List<EventHandler> currentHandlers = getHandlersForEventType(entry.getKey());
      Collection<EventHandler> eventMethodsInListener = entry.getValue();
      
      if (currentHandlers == null || !currentHandlers.containsAll(entry.getValue())) {
        throw new IllegalArgumentException(
            "missing event handler for an annotated method. Is " + object + " registered?");
      }
      currentHandlers.removeAll(eventMethodsInListener);
    }
  }

  /**
   * Posts an event to all registered handlers.  This method will return
   * successfully after the event has been posted to all handlers, and
   * regardless of any exceptions thrown by handlers.
   *
   * <p>If no handlers have been subscribed for {@code event}'s class, and
   * {@code event} is not already a {@link DeadEvent}, it will be wrapped in a
   * DeadEvent and reposted.
   *
   * @param event  event to post.
   */
  public void post(Object event) throws InvocationTargetException {
    Set<Class<?>> dispatchTypes = flattenHierarchy(event.getClass());

    boolean dispatched = false;
    for (Class<?> eventType : dispatchTypes) {
      List<EventHandler> wrappers = getHandlersForEventType(eventType);

      if (wrappers != null && !wrappers.isEmpty()) {
        dispatched = true;
        for (EventHandler wrapper : wrappers) {
          enqueueEvent(event, wrapper);
        }
      }
    }

    if (!dispatched && !(event instanceof DeadEvent)) {
      post(new DeadEvent(this, event));
    }

    dispatchQueuedEvents();
  }

  /**
   * Queue the {@code event} for dispatch during
   * {@link #dispatchQueuedEvents()}. Events are queued in-order of occurrence
   * so they can be dispatched in the same order.
   */
  protected void enqueueEvent(Object event, EventHandler handler) {
    eventsToDispatch.get().offer(new EventWithHandler(event, handler));
  }

  /**
   * Drain the queue of events to be dispatched. As the queue is being drained,
   * new events may be posted to the end of the queue.
   */
  protected void dispatchQueuedEvents() throws InvocationTargetException {
    // don't dispatch if we're already dispatching, that would allow reentrancy
    // and out-of-order events. Instead, leave the events to be dispatched
    // after the in-progress dispatch is complete.
    if (isDispatching.get()) {
      return;
    }

    isDispatching.set(true);
    try {
      while (true) {
        EventWithHandler eventWithHandler = eventsToDispatch.get().poll();
        if (eventWithHandler == null) {
          break;
        }

        dispatch(eventWithHandler.event, eventWithHandler.handler);
      }
    } finally {
      isDispatching.set(false);
    }
  }

  /**
   * Dispatches {@code event} to the handler in {@code wrapper}.  This method
   * is an appropriate override point for subclasses that wish to make
   * event delivery asynchronous.
   *
   * @param event  event to dispatch.
   * @param wrapper  wrapper that will call the handler.
   */
  protected void dispatch(Object event, EventHandler wrapper) throws InvocationTargetException {
      wrapper.handleEvent(event);
  }

  /**
   * Retrieves a mutable set of the currently registered handlers for
   * {@code type}.  If no handlers are currently registered for {@code type},
   * this method may either return {@code null} or an empty set.
   *
   * @param type  type of handlers to retrieve.
   * @return currently registered handlers, or {@code null}.
   */
  List<EventHandler> getHandlersForEventType(Class<?> type) {
    return handlersByType.get(type);
  }

  /**
   * Creates a new Set for insertion into the handler map.  This is provided
   * as an override point for subclasses. The returned set should support
   * concurrent access.
   *
   * @return a new, mutable set for handlers.
   */
  protected List<EventHandler> newHandlerSet() {
    return new CopyOnWriteArrayList<EventHandler>();
  }

  /**
   * Flattens a class's type hierarchy into a set of Class objects.  The set
   * will include all superclasses (transitively), and all interfaces
   * implemented by these superclasses.
   *
   * @param concreteClass  class whose type hierarchy will be retrieved.
   * @return {@code clazz}'s complete type hierarchy, flattened and uniqued.
   */
  @VisibleForTesting
  Set<Class<?>> flattenHierarchy(Class<?> concreteClass) {
    try {
      return flattenHierarchyCache.get(concreteClass);
    } catch (ExecutionException e) {
      throw Throwables.propagate(e.getCause());
    }
  }

  /** simple struct representing an event and it's handler */
  static class EventWithHandler {
    final Object event;
    final EventHandler handler;
    public EventWithHandler(Object event, EventHandler handler) {
      this.event = event;
      this.handler = handler;
    }
  }
}