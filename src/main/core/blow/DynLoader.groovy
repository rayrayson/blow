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

package blow;

import blow.plugin.Plugin;

import groovy.util.logging.Slf4j

import java.lang.reflect.Modifier;


import java.util.zip.ZipInputStream
import blow.shell.ShellCommand
import java.lang.reflect.Method
import blow.shell.Cmd

/**
 * Load all the class defined dynamically 
 * 
 * @author Paolo Di Tommaso
 *
 */
@Slf4j
public class DynLoader {

	/**
	 * The list of paths defining the root folder which contains extensions scripts
	 */
	protected List<File> roots

	protected URL jarLocation


    DynLoader( List<File> path, URL jarLocation = null ) {
		this.roots = path ? new ArrayList(path) : []
		this.jarLocation = jarLocation
	}	
	
	/**
	 * Create a loader with the provided list of folder path to be used as root
	 */
	DynLoader( File... path ) {
		this( (List<File>) (path != null ? path.collect { it }  : []) );	
	}

	/**
	 * Create a loader with the specified list of paths to be used as root folder 
	 * to look for extension script
	 *  	
	 */
	DynLoader( String... path ) {
		this( (List<File>) (path != null ? path.collect { new File(it) }  : []) );	
	}

	/**
	 * All and only one classes the implements the interface {@link ShellCommand} meant
	 * to be used as Pilot shell extension	
	 */
	@Lazy
	def List<Class<ShellCommand>> shellCommands = {
		allClasses.findAll { Class clazz -> 
			ShellCommand.class.isAssignableFrom(clazz) \
			&& !clazz.isAnnotation() \
			&& !Modifier.isAbstract(clazz.getModifiers()) \
			&& !Modifier.isInterface(clazz.getModifiers()) \
			&& Modifier.isPublic(clazz.getModifiers())
		} 
	}()

    /**
     * The list of discovered method marked with the {@link Cmd} annotation.
     * These methods will added to the command available using the shell-methods
     * extension mechanism
     */
    @Lazy
    def List<Method> shellMethods = {

        def result = []

        allClasses.collect { Class clazz ->

            if( !ShellCommand.class.isAssignableFrom(clazz) \
                && !clazz.isAnnotation() \
                && !Modifier.isAbstract(clazz.getModifiers()) \
                && !Modifier.isInterface(clazz.getModifiers()) \
                && Modifier.isPublic(clazz.getModifiers()) )
            {

                result.addAll(  clazz.getMethods().findAll { Method m -> m.getAnnotation(Cmd) } )

            }

        }

        return result


    }()

    
    
	/**
	 * All and only one classes the are annotated with {@link Plugin}
	 */
	@Lazy 
	def List<Class> pluginClasses = {
		log.trace "Before pluginClasses"
		def result = allClasses.findAll { Class clazz -> clazz.isAnnotationPresent(Plugin.class) }
		log.trace "// After pluginClasses: $result"
		return result
	}()
	
	/**
	 * All the dynamically discovered classes in the specified folder
	 */
	@Lazy
	private List<Class> allClasses = {
	
		ArrayList<Class> result = []

		/*
		 * Load all classes found in the jar 
		 */
		if( foundClassNamesInJar ) {
			foundClassNamesInJar.each { String clazzName -> 
				try {
					log.trace "Loading class: '$clazzName'"
					result.add(Class.forName(clazzName))
				}
				catch( Exception e ) {
					log.error("Cannot instantiate class: '$clazzName'")
				}
			}
			log.trace "//Loaded class in jar"
		}
		
		/*
		 * try to laod more classes for plugins in the extension classpath
		 */
		if( foundGroovySources ) {
			log.trace "Loading groovy sources" 
			GroovyClassLoader loader = new GroovyClassLoader()	
			roots.each { loader.addClasspath(it.getAbsolutePath()) }
			foundGroovySources.each { 
				log.trace "Loading source: '$it' "
				result.add(loader.parseClass(it)) 
				
			} 
			
			log. trace "//Loaded groovy sources"
		}
		
		return result;
		
	}()
	 
	/**
	 * All groovy source files discovered in the path provided by the loader constructor
	 */
	@Lazy
	private List<File> foundGroovySources = {
		
		def result = []

		// find only the path that are directory (that are containing the groovy sources)		
		def folders = roots.findAll { File path -> path.isDirectory() }  
		log.trace "Groovy sources folders: $folders"
		
		/*
		 * traverse the script folder and parse all groovy source files
		 */
		folders.each { File path ->
		   log.trace "Groovy load folder: '$path' "
		   
		   path.eachFileRecurse { file ->
			   log.trace "Groovy load source file: '$file' "
			   
			   if( file.name.endsWith(".groovy") ) {
				   result.add(file)
			   }
		   }}
	
		log.trace "// Groovy load folders $result"
	   return result;
	}()
	
	@Lazy
	private List<String> foundClassNamesInJar = {
		
		def result = new LinkedList();
		def jars = roots.findAll { File path -> path.getName().endsWith(".jar") }
		log.trace "Jars: $jars"
		
		/*
		 * the .jar files in the path array 
		 */
		jars.each {  File path -> 
			result.addAll( fetchClassesInJar(path) )  
		} 
		
		/*
		 * the jar Stream 
		 */
		if( jarLocation ) {
			log.trace ">> Loading classes from JAR stream"
			ZipInputStream zip = new ZipInputStream(jarLocation.openStream())
			result.addAll( fetchClassesInJar(zip) )	
			log.trace "<< Loading classes from JAR stream DONE"
		}
		
		
		log.trace "Found classes: $result"
		return result
		
	}()
	
	
	/**
	 * Given a jar file, find all the classes names in the given package root 
	 *  
	 * @param jarPath The JAR file 
	 * @param packageRoot The base package on which look for the classes
	 * @return A list of the classes names found in the jar file
	 */
	private List<String> fetchClassesInJar(File jarPath, String packageRoot = "blow") {
		log.trace "Fetch in Jar: $jarPath; package: $packageRoot"

		ZipInputStream zip = new ZipInputStream( new FileInputStream(URLDecoder.decode(jarPath?.toString(), "UTF-8")))
		fetchClassesInJar(zip, packageRoot);
		
	}
	
	private List<String> fetchClassesInJar(ZipInputStream zip, String packageRoot = "blow") {
		assert zip != null
		
		packageRoot = packageRoot.replaceAll('\\.','/')
		def result = new LinkedList<String>()
		
		def entry 
		while( entry = zip.getNextEntry() ) {
		  String name = entry.getName();
		  if( !name.startsWith(packageRoot)) { continue }
		  if( name.endsWith("/") ) continue
		  if( !name.endsWith(".class") ) continue
		  if( name.contains('$') ) continue
		  
		  name = name.substring( 0, name.length()-6 ); // remove the .class extension
		  name = name.replaceAll('/','.');
		  
		  log.trace "Found class: $name"
		  result.add(name)
		}
		
		return result

	}
	
}
