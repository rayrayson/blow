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

import java.lang.reflect.Field

/**
 *  Helper class to inject field values in to a class instance.
 *
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class InjectorHelper {

    /**
     * Inject the {@link blow.shell.BlowShell} and {@link blow.BlowSession} instances in the method's declaring object
     */
    public void injectFields(def obj, def instancesToInject) {
        assert obj
        assert instancesToInject

        log.trace("Injecting to ${obj} fields: ${instancesToInject}")
        inject( obj.getClass(), obj, instancesToInject )

    }
    
    private static void inject( Class clazz, def obj, def values ) {
        log.trace "Inject for class: ${clazz.name} fields: ${clazz.declaredFields *. name} with values: ${values}"
        clazz.getDeclaredFields() .each{ Field field ->

            for( def val : values ) {
                if ( val?.getClass() == field.type ) {
                    log.trace "Injecting field: ${clazz.getSimpleName()}#${field.name}"
                    field.setAccessible(true)
                    field.set(obj,val)
                    break
                }
            }
        }

        // visit super-class
        if( clazz.getSuperclass() != Object ) {
            inject(clazz.getSuperclass(), obj, values)
        }


    }
    
}
