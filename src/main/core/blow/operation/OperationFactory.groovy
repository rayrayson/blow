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

import blow.DynLoader
import groovy.util.logging.Slf4j

import static blow.operation.OperationHelper.opName
import blow.exception.UnknownOperationException

/**
 * Create and initialize a operation instance
 * 
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
class OperationFactory {

	/**
	 * The custom class loader used by the operation factory
	 */
	private DynLoader loader;


    OperationFactory( DynLoader loader ) {
		assert loader != null, "The argument 'loader' cannot be null"
		this.loader = loader 
	}
	
	
	public Object create( String name ) {
        assert name

        Class clazz = loader.operationsClasses.find {  name == opName(it)  }
        if( clazz == null ) {
            throw new UnknownOperationException("Cannot found the definition for operation named: '${name}'")
        }

		try {

            /*
            * create an instance for this operation
            */
            log.debug "Creating operation '$name' -> '${clazz?.getName()}' "
            def result = clazz.newInstance()

		}
		catch( Exception e ) {
			log.error("Unable to instantiate operation named: '$name'", e)
			return null
		}
		
	}
	





}
