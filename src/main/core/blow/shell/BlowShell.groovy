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

import blow.exception.BlowConfigException
import com.google.inject.Guice
import com.typesafe.config.ConfigFactory
import jline.Completor
import jline.ConsoleReader
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import blow.*
import blow.util.CmdLine

/**
 * The Pilot shell runner 
 * 
 * @author Paolo Di Tommaso
 *
 */
class BlowShell {

    /**
     * Note: create the logger lazily so it is possible to configure the logging
     * subsystem dynamically
     * <p>
     * See script 'logback.groovy'
     */
    @Lazy
    Logger log = { LoggerFactory.getLogger(BlowShell) }()


	private BlowSession session;
	
	private ConsoleReader console 

	private DynLoader loader;

    /**
     * The map of the {@link  ShellCommand} available in this shell instance
     */
    private Map<String,ShellCommand> availableCommands = [:];


    /**
     * The main command to be executed by the program (if any).
     * This map - if defined - holds the command {@code name} and
     * the command {@code args} as an list of strings
     */
    private Map mainCommand

    /**
     * The main entry on the program command line. Usually it is the cluster configuration name.
     * This value is define the by {@link #init} method and then read during the program @{link #run}
     */
    private String mainEntry

    /**
     * The program global options as returned by {@link CliBuilder#parse} method.
     * In other words this object holds the global argument specified on program
     * command line
     * <p>
     * See {@link #main} {@link CliBuilder}
     */
    static def options

    /**
     * The cluster name currently in use
     */
    def String currentCluster;

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
			def max = availableCommands.keySet().max { it.length() }
			max = max.length() // <-- note: the above returns the longest item, so now we get the real max value
			
			// print the 'help' for each command			
			println ""
			availableCommands.each {
				name, _action -> 
				println " ${name.padRight(max)} ${_action.help()?.trim() ?: ''}"
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

        private String clusterName

		@Override
		public String getName() { "use" }

        def void parse( def args ) {  clusterName = args?.size()>0 ? args.head() : currentCluster }

		@Override
		public void invoke() {
			println "Switching > ${clusterName}"
			useCluster(clusterName)
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
                if( sCommand && availableCommands[sCommand] instanceof CommandCompletor) {
                    def options = (availableCommands[sCommand] as CommandCompletor).findOptions( sOptions )
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
		
			BlowShell.this.availableCommands.each {
				if( it.key.startsWith(prefix)  ) { 
					candidates.add(it.key)
				}
			}
				
		}
	}

	
	/**
	 * The shell constructor
	 * 
	 */
	protected BlowShell() {
		
		loader = DynLoaderFactory.get()
		
		/*
		 * add the availableCommands
		 */
		addCommand( new HelpAction() )
		addCommand( new UseAction() )
		addCommand( new ExitAction() )

		loader.actionClasses.each { Class clazz ->
			addCommand(clazz)
		} 
		
		/*
		 * create the console reader 
		 */
		console = new ConsoleReader()
		console.setBellEnabled(false)
		console.addCompletor( new ShellCompletor() )
		
	}

    /**
     * Create an instance of the specified {@link ShellCommand} and add it to the list
     * of the {@link #availableCommands}
     *
     * @param clazz
     */
	private void addCommand( Class<ShellCommand> clazz ) {
		try {
			addCommand(clazz.newInstance())
		}
		catch( Throwable e ) {
			log.error("Cannot create instance for shell command class: '$clazz?.getName()'")
		}
	}

    /**
     * Add the specified {@link ShellCommand} instance to the list of {@link #availableCommands}
     */
	private void addCommand( ShellCommand command ) {
		assert command != null
        log.trace "addAction: $command"
		
		if( !command.getName() ) {
			println "The following shell command extension does not define any name, skipping it: '${command.getClass().getName()}'"
			return	
		}
		
		if( availableCommands.containsKey( command.getName() ) ) {
			println "The command '${command.getName()}' is already defined, skipping class '${command.getClass().getName()}'"
			return
		}	

		if( command.hasProperty("shell") ) {
			command.shell = this;	
		}

		/*
		 * append the command to the global list
		 */
		availableCommands.put( command.getName(), command )
	}
	
	/**
	 * Initialize the context with the provided arguments
	 * 
	 * @param args
	 */
	def void init( List<String> args = [] ) {

        /*
         * parse the command arguments, they should on the following sequence
         * 1 - cluster config name
         * 2 - command to execute and its arguments (optional)
         */
        if( args.size()>0 ) {
		    mainEntry = args.head()
            args = args.tail()
        }

        if( args.size()>0 ) {
            mainCommand = [:]
            mainCommand.name = args.head()
            mainCommand.args = args.tail()
        }

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
		currentCluster = clusterName
	} 
	
	/**
	 * Execute the requested command
	 * 
	 */
	def void execute( String command, def args ) {

        def cmdObj = availableCommands[command]
        if( cmdObj ) {
            cmdObj.parse(args)
            cmdObj.invoke()
        }
        else {
            println "Unknown command: ${command}"
        }

	}
	
	/**
	 * Main execution method 	
	 */
	@Override
	public void run() {

        /*
        * if not cluster has been specified, exit with an error
        */
        if( !mainEntry ) {
            System.err.println "Please specify the cluster name on your command"
            printUsage()
            System.exit (1)
        }

        /*
         * as special case, if the 'help' string is entered, the
         * command usage string is printed
         */
        if( mainEntry == "help" ) {
            if( !mainCommand ) {
                printUsage()
            }
            else if( availableCommands.containsKey( mainCommand.name ) ) {
                print "${mainCommand.name}: "
                println availableCommands[mainCommand.name].help() ?: "(no help available)"
            }
            else {
                println "Unknown command: '${mainCommand.name}'"
            }
            System.exit(0)
        }


        /*
         * This should be the normal case
         * Initialize the cluster configuration with the entered name
         */
        useCluster( mainEntry )


		/*
		 * if an command is provided, execute and return
		 */
		if( mainCommand ) {
			execute( mainCommand.name, mainCommand.args )
			return;
		}
		
		/*
		 * otherwise enter in a 'shell' loop
		 */
		while( true ) {
		
			def line = CmdLine.splitter( prompt() )
			if( !line || line.size()==0 ) { continue }

			try {
				execute( line.head(), line.tail() );
			}
			catch( ShellExit e ) { break }
			catch( Throwable e ) {
				System.err.println "Cannot execute: '${line.join(' ')}'. Cause: ${e.getMessage()}"
                log.debug("Error executing command: '${line.join(' ')}'", e)
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
	def prompt( String prompt = null, Closure<String> accept = null ) {
		def cluster = session ? "[${session.clusterName}] " : ""

        // format the prompt nicely
        prompt = (!prompt) ? "${Project.name} ${cluster}\$ " : prompt + " "

        def line
        while( true ) {
            line = console.readLine(prompt)
            if( !accept || accept.call(line) ) {
                break
            }
        }

        return line
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

        /*
         * print the logo
         */
        println \
        """\
           ___  __
          / _ )/ /__ _    __
         / _  / / _ \\ |/|/ /
        /____/_/\\___/__,__/  ver: ${Project.number}
        """
        .stripIndent()

        /*
         * parse the command line options
         */
        CliBuilder cli = new CliBuilder()
        cli.usage = "blow [options] cluster-config"
        cli._( longOpt: "debug", "Print debug level information")
        cli._( longOpt: "trace", "Print trace level information")
        cli.h( longOpt: "help", "Show this help")

        options = cli.parse(args)

        /*
         * Initialize Guice and create a shell instance
         */
        Guice.createInjector();
		BlowShell shell = new BlowShell();
        // trace the command line
        shell.log.debug "~~~ ${Project.name} \"${args?.join(' ') ?: ''}\""

        /*
         * start the shell
         */
		shell.init(options.arguments());
		shell.run( );
		shell.close()
	}
}

