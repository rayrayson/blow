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

package blow.operation

import java.lang.annotation.Target
import java.lang.annotation.ElementType
import java.lang.annotation.Retention

import static java.lang.annotation.RetentionPolicy.RUNTIME

/**
 * Use this annotation to mark one (or more) method(s) as 'validation' method to be invoked during
 * the operation sanity check phase, to avoid to start clusters in inconsistent state
 *
 * @author Paolo Di Tommaso
 */


@Target( ElementType.METHOD )
@Retention(RUNTIME)
public @interface Validate {

}
