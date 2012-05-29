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

package blow.util

import groovy.util.logging.Slf4j

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class InjectorHelper {


    /**
     * Inject the {@link blow.shell.BlowShell} and {@link blow.BlowSession} instances in the method's declaring object
     */
    private void injectFields(def obj, def instances) {
        assert obj
        assert instances

        log.debug("Injecting to ${obj} fields: ${instances}")

        obj.getMetaClass().getProperties().each { MetaProperty field ->

            for( def it : instances ) {
                if ( it?.getClass() == field.type ) {
                    field.setProperty(obj, it)
                    break
                }
            }
        }
    }
}
