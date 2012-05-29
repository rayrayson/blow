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

import com.typesafe.config.ConfigObject as ConfigObject

import blow.DynLoader
import blow.exception.BlowConfigException

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import groovy.util.logging.Slf4j
import java.lang.reflect.Field
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
	
	
	public Object create( String name, String strConf = null ) {
	
		try {
			ConfigObject objConf = strConf ? ConfigFactory.parseString(strConf).root() : null
			return create( name, objConf )
		}
		catch( Exception e ) {
			log.error("Unable to instantiate operation named: '$name' with the following configuration: '$strConf'", e)
			return null
		}
		
	}
	
	/**
	 * Create an instance of the specified operation 'name' and inejct the parameters
	 * as defined in the configuration file.
	 * 
	 * @param name The operation name e.g. <code>nfs</code>, <code>hadoop</code>, <code>sge</code>, etc. The operation name is case-insensitive
	 * @return the operation instance
	 */
	public Object create( String name, ConfigObject conf ) {
		assert name, "Argument 'name' cannot be null"
		
		Class clazz = loader.operationsClasses.find { it.getSimpleName()?.equalsIgnoreCase(name) || it.getAnnotation(Operation.class) ?.value()?.equalsIgnoreCase(name)  }
		if( clazz == null ) { 
			throw new UnknownOperationException("Cannot found the definition for operation named: '${name}'")
		}

		/*
		 * create an instance for this operation
		 */
		log.debug "Creating operation '$name' -> '${clazz?.getName()}' "
		def result = clazz.newInstance()
		
		/*
		 * inject the parameters defined in the configuration
		 */
		if( conf ) {
            /*
             * sets the configuration properties though the {@link ConfHolder} interface
             */
            if( ConfHolder.isAssignableFrom(result.getClass()))  {

              conf.entrySet().each {
                  (result as ConfHolder) .setConfProperty( it.getKey(), it.getValue().unwrapped() )
              }

            }

            /*
             * .. otherwise the configuration properties are defined by the Plugin through the 'Conf' annotation
             */
            else {

                def clazzMap = getConfProperties(result.getClass())

                conf.keySet().each {  configKey ->
                    if( clazzMap.containsKey(configKey) ) {
                        def propertyName = clazzMap.get(configKey)
                        def propertyValue = conf.get(configKey).unwrapped()
                        result.setProperty( propertyName, propertyValue  )
                    }
                    else {
                        log.warn "Unknown attribute '${configKey}' for operation '${name}'. This value will not be used."
                    }
                }
            }
		}


		return result
	} 

	
	/**
	 * Create a operation instance starting with a generic configuration object
	 */
	public Object createWithConf( ConfigValue config ) {
		assert config != null, "Argument 'config' cannot be null" 
		
		
		def pluginName = null;
		def pluginConf = null;
		
		/*
		 * A config object can be an instance of:
		 * - ConfigString: in this case the string represent the operation name and there is any configuration value for the operation
		 * - ConfigObject: this object contains only one entry in which, the key defines the operation name and the associated value
		 * 					MUST be a ConfigObjectdefining the option for the operation instantiation. Other value will be discarded
		 */

		
		switch ( config.valueType() ) {
			case com.typesafe.config.ConfigValueType.STRING:
				pluginName = config.unwrapped()
				break;
				
			case com.typesafe.config.ConfigValueType.OBJECT:
				def elem = config.entrySet().find()
				pluginName = elem.getKey()
				pluginConf = elem.getValue();
				if( !pluginConf instanceof com.typesafe.config.ConfigObject ) {
					throw new BlowConfigException("Invalid operation configuration at: " + config.origin() )
				}
				break;
			
			default:
				throw new BlowConfigException( "Invalid operation declaration at: " + config.origin() )
			
		}
		
		
		create( pluginName, pluginConf )
			
	}




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
     * The configuration properties are defined by the Plugin through the 'Conf' annotation
     * <p>
     * This method look for all the attributes defined in the operation that need to inject with
     * values coming from the configuration file
     *
     * @param clazz the operation class
     * @return A map associating the operation properties with the relative configuration attributes.
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
    static def Map<String,String> getConfProperties(Class clazz) {
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
