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

import com.typesafe.config.ConfigObject as ConfigObject

import blow.DynLoader
import blow.exception.BlowConfigException
import blow.exception.UnknownPluginException
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValue
import groovy.util.logging.Slf4j

/**
 * Create and initialize a plugin instance
 * 
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
@Mixin(ConfHelper)
class PluginFactory {
	
	/**
	 * The custom class loader used by the plugin factory
	 */
	private DynLoader loader;


    PluginFactory( DynLoader loader ) {
		assert loader != null, "The argument 'loader' cannot be null"
		this.loader = loader 
	}
	
	
	public Object create( String name, String strConf = null ) {
	
		try {
			ConfigObject objConf = strConf ? ConfigFactory.parseString(strConf).root() : null
			return create( name, objConf )
		}
		catch( Exception e ) {
			log.error("Unable to instantiate plugin named: '$name' with the following configuration: '$strConf'", e)
			return null
		}
		
	}
	
	/**
	 * Create an instance of the specified plugin 'name' and inejct the parameters 
	 * as defined in the configuration file.
	 * 
	 * @param name The plugin name e.g. <code>nfs</code>, <code>hadoop</code>, <code>sge</code>, etc. The plugin name is case-insensitive
	 * @return the plugin instance 
	 */
	public Object create( String name, ConfigObject conf ) {
		assert name, "Argument 'name' cannot be null"
		
		Class clazz = loader.pluginClasses.find { it.getSimpleName()?.equalsIgnoreCase(name) || it.getAnnotation(Plugin.class) ?.value()?.equalsIgnoreCase(name)  }
		if( clazz == null ) { 
			throw new UnknownPluginException("Cannot found the definition for plugin named: '${name}'")
		}

		/*
		 * create an instance for this plugin		
		 */
		log.debug "Creating plugin '$name' -> '${clazz?.getName()}' "
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
                        log.warn "Unknown attribute '${configKey}' for plugin '${name}'. This value will not be used."
                    }
                }
            }
		}


		return result
	} 

	
	/**
	 * Create a plugin instance starting with a generic configuration object
	 */
	public Object createWithConf( ConfigValue config ) {
		assert config != null, "Argument 'config' cannot be null" 
		
		
		def pluginName = null;
		def pluginConf = null;
		
		/*
		 * A config object can be an instance of:
		 * - ConfigString: in this case the string represent the plugin name and there is any configuration value for the plugin
		 * - ConfigObject: this object contains only one entry in which, the key defines the plugin name and the associated value
		 * 					MUST be a ConfigObjectdefining the option for the plugin instantiation. Other value will be discarded
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
					throw new BlowConfigException("Invalid plugin configuration at: " + config.origin() )
				}
				break;
			
			default:
				throw new BlowConfigException( "Invalid plugin declaration at: " + config.origin() )
			
		}
		
		
		create( pluginName, pluginConf )
			
	}
	


}
