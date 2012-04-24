/*
 *   Copyright (c) 2012. Paolo Di Tommaso
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

import groovy.xml.NamespaceBuilder

class Build {
	
	def pathBuild = "./build/stage"
	def pathSourceAll = "./src/main/core:./src/main/extensions:./src/main/java"
	def pathSourceCore = "./src/main/core"
	def pathClasses = "$pathBuild/classes"
	def pathCompileLibs = "$pathBuild/lib"
	def pathEmbedLibs = "$pathBuild/embed"
    def pathBigJar = "$pathBuild/onejar.jar"
    def pathShellStub = "./build/launcher.stub"
    def pathMavenAntTask = "./build/maven-ant-tasks-2.1.3.jar"
    def pathOneJarTask = "./build/one-jar-ant-task-0.97.jar"
	def mainClass = "blow.shell.BlowShell"
	def jcloudsVersion = "1.3.2"
	def versionNumber   // defined by the property 'version' in the pom.xml
    def projectName     // defined by the property 'groupId' in the pom.xml
	@Lazy def pathDistFile = { "./dist/${projectName}.jar" } ()
	def ant = new AntBuilder()
    def artifact


    Build() {

        ant.path( id:"maven-ant-tasks.classpath",  path:pathMavenAntTask)
        ant.typedef( uri:"antlib", resource:"org/apache/maven/artifact/ant/antlib.xml", classpathref:"maven-ant-tasks.classpath" )

        artifact = NamespaceBuilder.newInstance(ant,"antlib","artifact");

        ant.sequential {

            artifact.pom id:"mypom", file: "pom.xml"
            property (name:'pomVersionNumber', value: '${mypom.version}')
            property (name:'pomGroupId', value: '${mypom.groupId}' )

        }

        this.projectName =  ant.project.properties.'pomGroupId'
        this.versionNumber = ant.project.properties.'pomVersionNumber'
    }

    /*
    * Create a Groovy class to stamp the version number defined in the file 'version.txt' in the compiled code
    */
	def createVersionNumberClass () {
	   
	   new File("${pathSourceCore}/blow/Project.groovy").text = """\
		   package blow

		   /*
		    * DO NOT MODIFY!
		    * THIS FILE IS GENERATED AUTOMATICALLY BY THE BUILD SCRIPT
		    */
		   final class Project {
		       final static def name = "${projectName}"
			   final static def number = "${versionNumber}"
		   }
		   """
		   .stripIndent()      
	} 
	
	
	/*
	 * Clean the build directory and create the structure directory
	 * 
	 */
	def clean () {
		ant.delete dir: pathBuild
        ant.delete file: pathDistFile
		ant.mkdir  dir: pathBuild
		ant.mkdir  dir: pathClasses
		ant.mkdir  dir: pathCompileLibs
	}
	
	/*
	 * Download all the dependencies defined in the maven 'pom.xml' file
	 */
	
	def copyCompileDeps() {

        artifact.dependencies ( filesetId:"libs.fileset", versionsId:"dependency.versions", scopes:"compile" ) {
            pom file:"pom.xml"
        }

        ant.copy( todir: pathCompileLibs , verbose: "true" ) {
			fileset( refid:"libs.fileset" )
			mapper type:"flatten"
			}
	}
	
	def copyGroovyDeps() {
		
		
		def GROOVY_HOME = new File( System.getenv('GROOVY_HOME') ?: "(none)" )
		if (!GROOVY_HOME.canRead()) {
			ant.echo( "Missing environment variable GROOVY_HOME: '${GROOVY_HOME}'" )
			System.exit(1)
		}
		
		// copy groovy dependencies
		ant.copy( todir: pathEmbedLibs , verbose: "true" ) {
			  fileset( dir: GROOVY_HOME, includes: 'embeddable/groovy-all-*.jar'  )
			  fileset( dir: GROOVY_HOME, includes: 'lib/commons-cli-*.jar'  )
			  fileset( dir: GROOVY_HOME, includes: 'lib/asm-*.jar'  )
			  fileset( dir: GROOVY_HOME, includes: 'lib/jline-*.jar'  )
			  mapper type:"flatten"
			}
		
		
	} 
	
	
	
	/*
	 * compile all sources 
	 */
	
	def compile() {
		if( emptyPath(pathCompileLibs) ) copyCompileDeps()
        if( emptyPath(pathEmbedLibs) ) copyGroovyDeps()
		
		ant.taskdef ( name: "groovyc", classname: "org.codehaus.groovy.ant.Groovyc" )
		ant.sequential {

            /*
             * copy extra resources file (properties, etc)
             */
			copy( todir: pathClasses ) {
				fileset( dir: pathSourceCore, includes: '*.properties'  )
				}

			/*
			 * compile all sources
			 */
			groovyc (srcdir: pathSourceAll, destdir: pathClasses  ) {
				classpath {
					fileset dir: pathCompileLibs, { include name: "*.jar" }
					pathelement path: pathClasses
				}

				javac source: "1.5", target: "1.5", debug: "on"
			}
			

		}
		
	
	} 
	
	
	/*
	 * Create the BIG jar containing compiled code and libraries
	 */
	
	def pack() {
	
		if( emptyPath(pathClasses) ) compile()
		if( emptyPath(pathEmbedLibs) ) copyGroovyDeps()
		
	   
	   // One-Jar task
	   // http://one-jar.sourceforge.net
	   ant.taskdef (
		   name:"one-jar",
		   classname:"com.simontuffs.onejar.ant.OneJarTask",
		   classpath:"${pathOneJarTask}",
		   onerror: "report"
		   )
	   
	   ant.delete( dir: pathBigJar )
	   ant."one-jar"( destfile: pathBigJar ) {
			 manifest {
			   attribute( name: 'One-Jar-Main-Class', value: mainClass )
			   attribute( name: 'One-Jar-URL-Factory', value: "com.simontuffs.onejar.JarClassLoader\$OneJarURLFactory" )
			 }
			 
			 delegate.main {
				 fileset( dir: pathClasses )
			   }
			 
			   lib {
				 fileset dir: pathCompileLibs
				 fileset dir: pathEmbedLibs
			   }
	   }
		   
	   
	   /*
		* Create the bash startup file
		*/
	   
	   def distFile = new File(pathDistFile)
       if( !distFile.getParentFile().exists() ) { distFile.getParentFile().mkdirs()  }
	   if( distFile.exists() ) distFile.delete()

       def out = new FileOutputStream(pathDistFile);
       out << new FileInputStream(pathShellStub)
       out << new FileInputStream(pathBigJar)

       "chmod +x ${distFile}".execute()
	   
	   ant.echo( "Blow executable created. Launch with: '$pathDistFile'" )
       ant.echo( "Done" );
		
	} 
	
	def all () { 
		clean()
		createVersionNumberClass()
		compile()
		pack()
	} 
	
	private boolean emptyPath( def path ) {
		assert path
		if( !(path instanceof File) ) path = new File(path)
		return !path.exists() || path.list() ?. size() == 0
	}

	private int hashPath( def path, int hash = 7 ) {
		assert path
		if( !(path instanceof File) ) path = new File(path)

		path.eachFileRecurse { file ->
			hash = 31 * hash + file.getAbsolutePath().toString().hashCode()
			hash = 31 * hash + file.lastModified().hashCode()
		}
		
		return hash
	}

	private int hashSources() {
		def hash = 7
		pathSourceAll.split("\\:").each {
			hash = hashPath(it,hash)
		}
		return hash
	}

	def info() {
		properties.each { println "~ $it" }
	}

	
	/**
	 * Compose the java command line with classpath info, etc to invoke the main class.
	 * The caller can use it to launch the app. 
	 */
	def run(def cmdline="") {

		if( emptyPath(pathCompileLibs) ) copyCompileDeps()
		if( emptyPath(pathEmbedLibs) ) copyGroovyDeps()
		compile();

		def classPath = "$pathClasses"
		new File(pathCompileLibs).eachFile { classPath += ":${it}" }
		new File(pathEmbedLibs).eachFile { classPath += ":${it}" }
		
		print "java -cp $classPath $mainClass $cmdline"
	}

    def upload() {

        def file = new File(pathDistFile);
        if( !file.exists() ) pack()

        println "Uploading $file .. "
        ant.copy ( file : file , tofile : '/Users/ptommaso/Dropbox/Public/blow/blow.jar', overwrite: true )

    }
	
}

def build = new Build();

if( !args ) build.all();
else if( args[0] == "run" ) {
	args = args.toList();
	args.remove(0)
	build.run( args.join(' ') )
}
else {
	args.each { action -> 
		println "~~ '$action'"
		build."$action"()  
	}
}


System.exit(0)