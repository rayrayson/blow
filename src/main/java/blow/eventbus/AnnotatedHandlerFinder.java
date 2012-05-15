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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.eventbus.AllowConcurrentEvents;
import com.google.common.eventbus.Subscribe;

import java.lang.reflect.Method;

/**
 * NOTE: THIS FILE IS A DERIVATIVE WORK OF THE GOOGLE GUAVA LIBRARY
 *
 */
class AnnotatedHandlerFinder implements HandlerFindingStrategy {

	  /**
	   * {@inheritDoc}
	   *
	   * This implementation finds all methods marked with a {@link Subscribe}
	   * annotation.
	   */
	  @Override
	  public Multimap<Class<?>, EventHandler> findAllHandlers(Object listener) {
	    Multimap<Class<?>, EventHandler> methodsInListener = HashMultimap.create();
	    Class<?> clazz = listener.getClass();
	    while (clazz != null) {
	      for (Method method : clazz.getMethods()) {
	        Subscribe annotation = method.getAnnotation(Subscribe.class);

	        if (annotation != null) {
	          Class<?>[] parameterTypes = method.getParameterTypes();
	          if (parameterTypes.length != 1) {
	            throw new IllegalArgumentException(
	                "Method " + method + " has @Subscribe annotation, but requires " +
	                parameterTypes.length + " arguments.  Event handler methods " +
	                "must require a single argument.");
	          }
	          Class<?> eventType = parameterTypes[0];
	          EventHandler handler = makeHandler(listener, method);

	          methodsInListener.put(eventType, handler);
	        }
	      }
	      clazz = clazz.getSuperclass();
	    }
	    return methodsInListener;
	  }

	  /**
	   * Creates an {@code EventHandler} for subsequently calling {@code method} on
	   * {@code listener}.
	   * Selects an EventHandler implementation based on the annotations on
	   * {@code method}.
	   *
	   * @param listener  object bearing the event handler method.
	   * @param method  the event handler method to wrap in an EventHandler.
	   * @return an EventHandler that will call {@code method} on {@code listener}
	   *       when invoked.
	   */
	  private static EventHandler makeHandler(Object listener, Method method) {
	    EventHandler wrapper;
	    if (methodIsDeclaredThreadSafe(method)) {
	      wrapper = new EventHandler(listener, method);
	    } else {
	      wrapper = new SynchronizedEventHandler(listener, method);
	    }
	    return wrapper;
	  }

	  /**
	   * Checks whether {@code method} is thread-safe, as indicated by the
	   * {@link AllowConcurrentEvents} annotation.
	   *
	   * @param method  handler method to check.
	   * @return {@code true} if {@code handler} is marked as thread-safe,
	   *       {@code false} otherwise.
	   */
	  private static boolean methodIsDeclaredThreadSafe(Method method) {
	    return method.getAnnotation(AllowConcurrentEvents.class) != null;
	  }
	}