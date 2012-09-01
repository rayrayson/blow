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

import org.codehaus.groovy.util.HashCodeHelper

import java.lang.reflect.Field

/**
 *  Helper class for Operation classes
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class OperationHelper {


    /**
     * Look out all the operation properties annotated with the 'Conf' annotation
     *
     * @param clazz the operation class
     * @return a list of {@link java.lang.reflect.Field} instances, or an empty list if any field is annotated
     */
    static def List<Field> getConfFields( Class clazz, List<Field> fields = [] ) {

        List<Field> result = clazz.getDeclaredFields().findAll { it.getAnnotation(Conf.class) }
        fields.addAll(result);

        return (clazz.getSuperclass() != Object.class)  ? getConfFields(clazz.getSuperclass(),fields) : fields
    }


    /**
     * Calculate the hash code for an operation, including only
     * the field marked with a {@code Conf} annotation
     *
     * @param op
     * @return An integer representing te hash code for the operation instance
     */
    def static int opHashCode( def op ) {
        Class clazz = op.getClass();
        List<Field> fields = getConfFields(clazz)

        def hash = HashCodeHelper.initHash()
        fields.each { Field field ->
            field.setAccessible(true)
            hash = HashCodeHelper.updateHash(hash, field.get(op) )
        }

        return hash
    }

    /**
     * The map containing the value for each conf attribute
     *
     * @param op
     * @return
     */
    def static Map getConfValues( def op ) {
        assert op

        def result = [:]

        def fields = getConfFields(op.getClass())
        fields ?.each { Field it ->
            it.setAccessible(true)
            result.put( it.name, it.get(op) )
        }

        return result
    }

    def static String opToString( def op ) {
        assert op

        def currentMap = getConfValues(op)
        def defaultMap = getConfValues(op.getClass().newInstance())

        def conf = []
        currentMap?.each { key, value ->
            // include only value different from the default
            if( value != defaultMap.get(key) ) {
                if( value instanceof CharSequence ) {
                    value = "'${value}'"
                }
                conf << "$key: $value"
            }
        }

        def result = opName(op)
        if( conf ) {
            result += "( ${conf.join(', ')} )"
        }
        else {
            result += '()'
        }

        return result
    }

    /**
     * The name of the operation
     *
     * @param op
     * @return
     */
    def static String opName( def op ) {
        assert op

        def clazz = op instanceof Class ? op : op.getClass()
        Operation annotation = clazz.getAnnotation(Operation.class)
        def result = annotation?.value()

        // fall back to the simple class name if no {@link Operation} annotation is specified
        if( !result ) {
            result = clazz.getSimpleName()
        }

        return result
    }

}
