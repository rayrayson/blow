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

package blow.plugin

import java.lang.reflect.Field

/**
 *  Helper class to handle plugin fields marked as {@link Conf} attributes
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ConfHelper {


    /**
     * Look out all the plugin properties annotated with the 'Conf' annotation
     *
     * @param clazz the plugin class
     * @return a list of {@link java.lang.reflect.Field} instances, or an empty list if any field is annotated
     */
    def List<Field> getConfFields( Class clazz, List<Field> fields = [] ) {

        List<Field> result = clazz.getDeclaredFields().findAll { it.getAnnotation(Conf.class) }
        fields.addAll(result);

        return (clazz.getSuperclass() != Object.class)  ? getConfFields(clazz.getSuperclass(),fields) : fields
    }

    /**
     * The configuration properties are defined by the Plugin through the 'Conf' annotation
     * <p>
     * This method look for all the attributes defined in the plugin that need to inject with
     * values coming from the configuration file
     *
     * @param clazz the plugin class
     * @return A map associating the plugin properties with the relative configuration attributes.
     * For example:
     *
     * <pre>
     * class Bean {
     *
     *   Conf("image-id") def imageId
     *   Conf("region-id") def regionId
     *   Conf("zone-id") def zoneId
     *
     *
     * }
     * </pre>
     *
     * <pre>
     *   [
     *        "image-id": imageId
     *        "region-id": regionId
     *        "zone-id": zoneId
     *   ]
     * </pre>
     */
    def Map<String,String> getConfProperties(Class clazz) {
        List<Field> fields = getConfFields(clazz);
        Map<String,String> result = [:]
        fields.each {
            def configName = it.getName();
            Conf aa = it.getAnnotation(Conf.class)
            if( aa.value() ) {
                configName = aa.value()
            }
            result.putAt(configName,it.getName())
        }

        return result
    }

}
