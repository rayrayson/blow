/*
 * Copyright (c) 2012. Paolo Di Tommaso
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

package blow.shell

import com.google.inject.*
import com.typesafe.config.ConfigFactory

import jline.ConsoleReader
import jline.Completor

import groovy.util.logging.Log4j

import blow.exception.BlowConfigException

import blow.BlowSession
import blow.DynLoader
import blow.DynLoaderFactory
import blow.BlowConfig
import blow.Project

/**
 * The Pilot shell runner 
 * 
 * @author Paolo Di Tommaso
 *
 */
@Log4j
class BlowShell {
	
	private BlowSession session;
	
	private ConsoleReader console 

	final private DynLoader loader;
	
	/**
	 * Define the standard 'run' shell command
	 */
	class RunAction extends AbstractShellCommand  {

		@Override
		public String getName() { "runplugin" }

		@Override
		public void invoke() {
			if( !params ) { 
				println "(no plugin specified)"
				return
			}

			println "@@ $params"			
			def args = params.split(" ")
			def pluginName = args[0]
			def pluginArgs = args.length>1 ? args[ 1.. args	-1 ].join(" ") : null
			
			session.runPlugin(pluginName, pluginArgs)
		}

		@Override
		public String help() { "Execute the specified plugin" }
	}
	 
	/**
	 * Defines the 'help' command used by the shell
	 * 
	 */
	class HelpAction extends AbstractShellCommand {

		@Override
		public String getName() { "help" } ;

		@Override
		public void invoke() {
			printUsage()
			listCommands()
		}
		
		def listCommands() {
			// get the longest command string
			def max = actions.keySet().max { it.length() }
			max = max.length() // <-- note: the above returns the longest item, so now we get the real max value
			
			// print the 'help' for each command			
			println ""
			actions.each {
				name, _action -> 
				println "  ${name.padRight(max)} ${_action.help()}"	
			}
		} 

		@Override
		public String help() {
			"Print this help"
		}} 
	
	/**
	 * Defines the 'use' command to switch between different confifuration
	 */
	class UseAction extends AbstractShellCommand {

		@Override
		public String getName() { "use" }

		@Override
		public void invoke() {
			println "Switching > $params"
			useCluster(params)
		}

		@Override
		public String help() {
			"Switch to a different cluster configuration"
		} } 
	
	/**
	 * Exit from the shell environment 
	 */
	class ExitAction extends AbstractShellCommand {

		public String getName() { "exit" }
		
		@Override
		public void invoke() {
			throw new ShellExit()
		} }
	
	static class ShellExit extends RuntimeException {} 

	/**
	 * This class handle the automatic shell completion feature 
	 * 
	 * @author Paolo Di Tommaso
	 *
	 */
	class ShellCompletor implements Completor {
		
		/**
		 * @param buffer 
		 * 		The string enter on the terminal that need to be completed 
		 * @param cursor 
		 * 		The cursor 'position' in the buffer
		 * @param 
		 * 		The list candidates string to present to the user
		 * 
		 */
		public int complete(String buffer, int cursor, List candidates) {
		
			/*
			 * look for the first chuck of the entered string separated by a blank char 
			 * (that is supposed to be the command entered by the user and the rest part 
			 * the arguments relative to it)
			 */
			def sCommand = ""
			def p = buffer.indexOf(' ')
			if( p >= 0 ) {
                /*
                 * Aplit it again, the first part is interpred as the command entered by the user
                 * the second part is/are the option(s) for this command
                 *
                 * So here it will delegate the command itself to propose some options to display
                 */
				sCommand = buffer.substring(0,p);
                def sOptions = buffer.substring(p).trim()
                if( sCommand && actions[sCommand] instanceof CommandCompletor) {
                    def options = (actions[sCommand] as CommandCompletor).findOptions( sOptions )
                    if( options ) {
                        candidates.addAll(options)
                        return cursor - sOptions.length()
                    }
                }
				return cursor;
			}
			else { 
				findMatchingActions(buffer, candidates)
				return cursor-buffer.length();
			}
			
		}
		
		private findMatchingActions(String prefix, List candidates) { 
		
			BlowShell.this.actions.each {
				if( it.key.startsWith(prefix)  ) { 
					candidates.add(it.key)
				}
			}
				
		}
		
		
		  
	}
	
	Map<String,ShellCommand>actions = [:];
	

	def action; 
	def params;

	/**
	 * The cluster name currently in use 
	 */
	def String cluster; 
	
	
	/**
	 * The shell constructor
	 * 
	 */
	protected BlowShell() {
		
		loader = DynLoaderFactory.get()
		
		/*
		 * add the actions
		 */
		addAction( new HelpAction() )
		addAction( new UseAction() )
		addAction( new RunAction() )
		addAction( new ExitAction() )

		loader.actionClasses.each { Class clazz ->
			addAction(clazz) 
		} 
		
		/*
		 * create the console reader 
		 */
		console = new ConsoleReader()
		console.setBellEnabled(false)
		console.addCompletor( new ShellCompletor() )
		
	}
	
	private void addAction( Class<ShellCommand> clazz ) {
		try {
			addAction(clazz.newInstance())
		}
		catch( Throwable e ) {
			BlowShell.log.error("Cannot create instance for shell command class: '$clazz?.getName()'")
		}
	}
	
	private void addAction( ShellCommand theActionToAdd ) {
		assert theActionToAdd != null
        BlowShell.log.trace "addAction: $theActionToAdd"
		
		if( !theActionToAdd.getName() ) {
			println "The following shell command extension does not define any name, skipping it: '${theActionToAdd.getClass().getName()}'"
			return	
		}
		
		if( actions.containsKey( theActionToAdd.getName() ) ) {
			println "The command '${theActionToAdd.getName()}' is already defined, skipping class '${theActionToAdd.getClass().getName()}'"
			return
		}	

		if( theActionToAdd.hasProperty("shell") ) {
			theActionToAdd.shell = this;	
		}

		/*
		 * append the command to the global list
		 */
		actions.put( theActionToAdd.getName(), theActionToAdd )
	}
	
	/**
	 * Initialize the context with the provided arguments
	 * 
	 * @param args
	 */
	def void init( String[] args ) {
		BlowShell.log.debug("Initializating shell argments: ${args}")
		
		cluster = args.length>0 ? args[0] : null;
		action = args.length>1 ? args[1] : null
		params = args.length>2 ? args[ 2 .. args.length-1 ].join(" ") : ""
		
		if( !cluster ) {
			System.err.println "Please specify the cluster name on your command"
			printUsage()
			System.exit (1)	
		}
		
		// set the cluster as the current used one
		useCluster( cluster )
	}
	
	/**
	 * define the cluster to be used 
	 */
	
	def void useCluster( String clusterName ) {
		
		/*
		 * The configuration files use the following strategy
		 * - the configuration file is named 'blow.conf'
		 * - it can be in the current directory as well as in the $HOME/.blow/ path
		 * - when both of them exist, the one in current path is consierated the main configurtion file, 
		 *   and the one under the use home is used for fallback values
		 * - if one of them exists, it is used as the main configuration file
		 * - when none of them exist an error is reported
		 */
		
		def currentPathConf = new File("./blow.conf")
		def homePathConf = new File( System.getProperty("user.home"), ".blow/blow.conf" )
		
		
		def confObj 
		if( currentPathConf.exists() && homePathConf.exists() ) {
			confObj = ConfigFactory.parseFile(currentPathConf).withFallback( ConfigFactory.parseFile(homePathConf)  )
		}  
		else if( currentPathConf.exists() ) {
			confObj = ConfigFactory.parseFile(currentPathConf)
		} 
		else if( homePathConf.exists() ) {
			confObj = ConfigFactory.parseFile(homePathConf)
		}
		else {
			System.err.println """\
								Missing configuration file. Configuration have to be specified using one (or both) the following paths:
								 - '$currentPathConf' 
								 - '$homePathConf'
								"""
								.stripIndent()
								
			System.exit(1)
		}
		
		/* 
		 * read and validate the configuration 
		 */
			
		BlowConfig config
		try {
			config = new BlowConfig(confObj, clusterName)
			config.checkValid()
		}
		catch( BlowConfigException e ) {
			System.err.println "Configuration error: ${e.message}"
			return
		}
		
		
		
		/*
		 * if OK, close the previous instance 
		 * and create a new one for this cluster instance
		 */
		if( session ) session.close()
		session = new BlowSession(config, clusterName)
		
		// set the current cluster name 
		cluster = clusterName 
	} 
	
	/**
	 * Execute the requested command
	 * 
	 */
	def void execute() {

        def command = actions[action]
        if( command ) {
            command.parse(params)
            command.invoke()
        }
        else {
            println "Uknown command"
        }

	}
	

	
	
	/**
	 * Main execution method 	
	 */
	@Override
	public void run() {

		/*
		 * if an command is provided, execute and return
		 */
		if( action ) {
			execute()	
			return;
		}
		
		/*
		 * otherwise enter in a 'shell' loop
		 */
		while( true ) {
		
			def line = prompt()
			if( !line ) continue

			String[] cmd = line.split(" ")
			action = cmd[0]
			params = cmd.length>1 ? cmd[ 1 .. cmd.length-1 ].join(" ") : ""
			try {
				execute();
			}
			catch( ShellExit e ) { break }
			catch( Throwable e ) {
				System.err.println "Cannot execute: $line. Cause: ${e.getMessage()}"
                BlowShell.log.error(e)
			}
						
		}

		println "Bye"
	}

	def void close() {
		// close the 'blow' session
		if( session ) session.close()
	} 
	
	/**
	 * Just wait for an user console entry 
	 * 
	 * @return the entered text
	 */
	def prompt() { 
		def cluster = session ? "[${session.clusterName}] " : ""
		console.readLine("blow $cluster\$ ")
	} 
	
	def void printUsage() {
		println "usage: ${Project.name} <clustername> [command [..]]"
	} 	
	
	/**
	 * Shell entry point 
	 * 
	 * @param args
	 */
	public static void main( String[] args ) {

        def logo =
        """\
           ___  __
          / _ )/ /__ _    __
         / _  / / _ \\ |/|/ /
        /____/_/\\___/__,__/  ver: ${Project.number}
        """
        .stripIndent()

        println logo
		Injector injector = Guice.createInjector();
		BlowShell shell = injector.getInstance(BlowShell.class)

		shell.init(args);
		shell.run( );
		shell.close()
	}
}

