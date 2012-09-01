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

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import com.thoughtworks.xstream.converters.MarshallingContext
import com.thoughtworks.xstream.converters.UnmarshallingContext
import com.thoughtworks.xstream.converters.collections.AbstractCollectionConverter
import com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper
import com.thoughtworks.xstream.io.HierarchicalStreamReader
import com.thoughtworks.xstream.io.HierarchicalStreamWriter
import com.thoughtworks.xstream.mapper.Mapper

/**
 *  XStream converter for collection of type {@link }
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ArrayListMultimapConverter extends AbstractCollectionConverter {

    ArrayListMultimapConverter(Mapper mapper) {
        super(mapper)
    }

    @Override
    boolean canConvert(Class aClass) {
        return ArrayListMultimap.class.equals(aClass)
    }

    @Override
    protected Object createCollection(Class type) {
        ArrayListMultimap.create()
    }

    public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
        ArrayListMultimap map = (ArrayListMultimap) source;
        for (Iterator iterator = map.asMap().entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();
            // starts the 'entry' element
            ExtendedHierarchicalStreamWriterHelper.startNode(writer, mapper().serializedClass(Map.Entry.class), Map.Entry.class);

            // write the key
            writeItem(entry.getKey(), context, writer);

            writer.startNode('values')
            for (Iterator values = entry.getValue().iterator(); values.hasNext();) {
                Object item = values.next();
                writeItem(item, context, writer);
            }
            writer.endNode()

            // close the 'entry' tag
            writer.endNode();
        }
    }

    public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
        ArrayListMultimap map = (ArrayListMultimap) createCollection(context.getRequiredType());
        populateMap(reader, context, map);
        return map;
    }

    protected void populateMap(HierarchicalStreamReader reader, UnmarshallingContext context, Multimap map) {

        while (reader.hasMoreChildren()) {
            reader.moveDown();
            putCurrentEntryIntoMap(reader, context, map, map);
            reader.moveUp();
        }
    }


    protected void putCurrentEntryIntoMap(HierarchicalStreamReader reader, UnmarshallingContext context, Multimap map, Multimap target) {

        reader.moveDown();
        Object key = readItem(reader, context, map);
        reader.moveUp();

        reader.moveDown();
        while (reader.hasMoreChildren()) {
            reader.moveDown();
            def value = readItem(reader, context, map);
            reader.moveUp();
            target.put(key,value)
        }
        reader.moveUp();

    }



}
