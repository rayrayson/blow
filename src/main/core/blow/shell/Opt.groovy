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

package blow.shell

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Mark shell-method parameter as command line parameters.
 * For example:
 * <pre>
 * class AnyName {
 *
 *     @Cmd
 *     def void myCustomCommand ( @Opt(name='arg') def arg ) {
 *         :
 *     }
 *
 * }
 *  </Pre>
 *
 *
 *
 * @author Paolo Di Tommaso
 */

@Target( ElementType.PARAMETER )
@Retention(RetentionPolicy.RUNTIME)
public @interface Opt {
    String name()
    String argName() default ""
    String longOpt() default ""
    int args() default 0
    boolean optionalArg() default false
    boolean required() default false
    String valueSeparator() default ""
    String description() default ""
}