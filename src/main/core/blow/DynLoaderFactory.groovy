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

package blow

import groovy.util.logging.Slf4j

/**
 * The default strategy to load external script and dependencies
 * 
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
class DynLoaderFactory {

	private static File extensionPath = new File("./src/main/extensions");

	private static volatile DynLoader instance;

	
	/**
	 * Singlton accessor method 
	 * 
	 * @return The {@link DynLoader} instance
	 */
	static DynLoader get() {
		 
		def result = instance;
		if( result ) return result;
		
		synchronized(this) {
			result = instance
			if( result == null ) {
				result = instance = create()	
			}
		} 
		return result
	} 

	
	static private DynLoader create() {
		
		URL jarLocation = null
		List<File> defaultPaths = [];
		
		// try to load the classes looking to the classloader 
		def className = DynLoaderFactory.class.getName().replace('.', '/');
		log.debug "ClassName: $className"
		def resource = DynLoaderFactory.class.getResource("/${className}.class")
		log.debug "Resource: $resource"

		/* 
		 * A JAR path 
		 */
		if( resource?.getProtocol()?.equals("jar") ) {
			String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!")); 
			log.debug "JarPath: $jarPath"
			
			defaultPaths.add( new File(jarPath) ) 
		}
		
		else if( DynLoaderFactory.class.getProtectionDomain()?.getCodeSource()?.getLocation()?.toURI()?.toString()?.startsWith("onejar:")  ) {

			jarLocation = DynLoaderFactory.class.getProtectionDomain()?.getCodeSource()?.getLocation()
			log.trace "Detected JAR location: $jarLocation "			

		}
		

		else if( extensionPath && extensionPath.exists() ) {
			defaultPaths.add(extensionPath)
		}

		// add the below path in the user $HOME if exist
		File userHomeScripts = new File( System.getProperty("user.home"), ".blow/plugins" )
		if( userHomeScripts.exists() && userHomeScripts.isDirectory() ) {
			defaultPaths.add(userHomeScripts);
		}
		
		// add the 'plugins' path in the current folder if exist
		File currentFolderScripts = new File("./blow-plugins")
		if( currentFolderScripts.exists() && currentFolderScripts.isDirectory() ) {
			defaultPaths.add(currentFolderScripts)
		}

		log.debug("Creating DynLoader using the following path(s): $defaultPaths")
	
		return new DynLoader(defaultPaths, jarLocation)

	} 
					
}
