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

import com.google.common.collect.Multimap;

/**
 * NOTE THIS FILE IS A DERIVATIVE WORK OF THE GOOGLE GUAVA LIBRARY
 *
 *
 * A method for finding event handler methods in objects, for use by
 * {@link EventBus}.
 *
 * @author Cliff Biffle
 */
interface HandlerFindingStrategy {

  /**
   * Finds all suitable event handler methods in {@code source}, organizes them
   * by the type of event they handle, and wraps them in {@link EventHandler}s.
   *
   * @param source  object whose handlers are desired.
   * @return EventHandler objects for each handler method, organized by event
   *       type.
   *
   * @throws IllegalArgumentException if {@code source} is not appropriate for
   *       this strategy (in ways that this interface does not define).
   */
  Multimap<Class<?>, EventHandler> findAllHandlers(Object source);

}