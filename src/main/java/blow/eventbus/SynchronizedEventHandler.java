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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * NOTE THIS FILE IS A DERIVATIVE WORK ORIGINALLY INCLUDED IN THE GOOGLE GUAVA LIBRARY
 *
 *
 * Wraps a single-argument 'handler' method on a specific object, and ensures
 * that only one thread may enter the method at a time.
 *
 * <p>Beyond synchronization, this class behaves identically to
 * {@link EventHandler}.
 *
 * @author Paolo Di Tommaso
 * @author Cliff Biffle
 */
class SynchronizedEventHandler extends EventHandler {
  /**
   * Creates a new SynchronizedEventHandler to wrap {@code method} on
   * {@code target}.
   *
   * @param target  object to which the method applies.
   * @param method  handler method.
   */
  public SynchronizedEventHandler(Object target, Method method) {
    super(target, method);
  }

  @Override public synchronized void handleEvent(Object event) throws InvocationTargetException {
    super.handleEvent(event);
  }

}