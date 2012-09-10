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

package blow.shell

import blow.builder.BlowConfigBuilder
import blow.util.CmdLine
import blow.util.InjectorHelper
import blow.util.KeyPairBuilder
import com.google.inject.Guice
import groovy.util.logging.Slf4j
import jline.Completor
import jline.ConsoleReader
import blow.*
import blow.exception.*

/**
 * The Pilot shell runner 
 * 
 * @author Paolo Di Tommaso
 *
 */
@Mixin(InjectorHelper)
@Slf4j
class BlowShell {

    /**
     * Declares static be make it accessible like a singleton (it will exist
     * always no more than one shell instance)
     */
	static ConsoleReader console

	private BlowSession session;

	private DynLoader loader;

    static final homePathConfigFile = new File( System.getProperty("user.home"), ".blow/blow.conf" )

    static final homePathHistoryFile = new File( System.getProperty("user.home"), ".blow/history" )

    private File userConfigFile = new File("./blow.conf")

    private BlowConfigBuilder configBuilder

    def BlowConfigBuilder getConfigBuilder() {
        if( !configBuilder )  { configBuilder=parseConfigFile() }
        return configBuilder
    }

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
    private String clusterNameRequested

    /**
     * The program global options as returned by {@link CliBuilder#parse} method.
     * In other words this object holds the global argument specified on program
     * command line
     * <p>
     * See {@link #main} {@link CliBuilder}
     */
    static def options

    /**
     * The Commad Line Interface object
     */
    static def CliBuilder cli

    /**
     * The cluster name currently in use
     */
    def String currentCluster;


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
            def result = cursor
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
                def cmdObj = sCommand ? availableCommands[sCommand] : null
                if( cmdObj instanceof CommandCompletor) {
                    // inject the required fields to this command
                    injectProperties(cmdObj)
                    // look for the 'completion' options
                    def options = (cmdObj as CommandCompletor).findOptions( sOptions )
                    if( options ) {
                        candidates.addAll(options)
                        result =  cursor - sOptions.length()
                    }
                }
			}
			else { 
				findMatchingActions(buffer, candidates)
				result = cursor-buffer.length() ;
			}

            // trick to add a blank space when there is just one candidate, so the right one
            if( candidates.size() == 1 ) {
                candidates[0] = candidates[0]+" "
            }
            return result

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
		
		loader.shellCommands.each { clazz -> addCommand(clazz) }
        
        loader.shellMethods.each {  method -> addCommand( new ShellMethodAdapter(this, method) ) }
		
		/*
		 * create the console reader 
		 */
		console = new ConsoleReader()
		console.setBellEnabled(false)
        console.useHistory=false
        console.history.setHistoryFile(homePathHistoryFile)
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
			log.error("Cannot add shell command defined by class: '${clazz?.getName()}'. Make sure that it defines a default constructor.", e)
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
	def void init( List<String> args = [], String confFileName = null, String clusterName = null ) {

        /*
         * Check if a custom configuration file name has been provided
         */
        if( confFileName ) {
            userConfigFile = new File(confFileName)
            if( !userConfigFile.exists() ) {
                println "The specified configuration file does not exists: '${userConfigFile}'"
                System.exit(1)
            }
        }

        // define the cluster name as the 'main' entry
        clusterNameRequested = clusterName

        // the first argument as the command to execute
        if( args.size()>0 ) {
            mainCommand = [:]
            mainCommand.name = args.head()
            mainCommand.args = args.tail()
        }

	}

    /**
     * @return The list of cluster names defined in the current used configuration file
     */
    def List<String> listClusters() {
        getConfigBuilder().getClusterNames()
    }


    /**
     * Parse the configuration file(s) and return the {@link BlowConfigBuilder} object.
     * This is meant for low-level operation.
     *
     * The configuration files use the following strategy
     * - the configuration file is named 'blow.conf'
     * - it can be in the current directory as well as in the $HOME/.blow/ path
     * - when both of them exist, the one in current path is considered the main configuration file,
     *   and the one under the use home is used for fallback values
     * - if one of them exists, it is used as the main configuration file
     * - when none of them exist an error is reported
     *
     *
     * See {@link BlowConfigBuilder}
     */
    protected BlowConfigBuilder parseConfigFile( ) {

        def currentPathConfig = userConfigFile


        log.debug( "User conf file [${currentPathConfig.exists()}]: " + currentPathConfig )
        log.debug( "Home conf file [${homePathConfigFile.exists()}]: " + homePathConfigFile )

        def configFiles = []

        if( homePathConfigFile.exists() ) {
            configFiles << homePathConfigFile
        }

        if( currentPathConfig.exists() ) {
            configFiles << currentPathConfig
        }

        if( configFiles.size() == 0 ) {
            System.err.println """\
								Missing configuration file. Configuration have to be specified using one (or both) the following paths:
								 - '$currentPathConfig'
								 - '$homePathConfigFile'
								""" .stripIndent()

            System.exit(1)
        }

        return BlowConfigBuilder.create(configFiles as File[])
    }

	/**
	 * define the cluster to be used 
	 */
	
	def BlowSession useCluster( String clusterName ) {
		log.debug("Using cluster: ${clusterName}")

        /*
         * If no cluster has been specified try to use the default one
         */
        if( !clusterName ) {
            def names = getConfigBuilder().getClusterNames()
            if( names.size() == 1 ) {
                clusterName = names[0]
                log.debug("Choosing by default cluster: ${clusterName} ")
            }
            else {
                if( names.size() == 0 ) {
                    log.warn("The provided configuration file(s) does not contain any cluster definition")
                }
                else if( !mainCommand?.name ) {  // <-- since there's any command to be execute, give a hint to the user
                    log.info("Use the command 'listclusters' to view the list of available cluster definitions")
                }
                return null
            }
        }


        /*
         * try to reload from disk
         */
        BlowConfig config
        def serialized = clusterName ? BlowSession.read(clusterName) : null
        // clear the serialized session if the 'saveOnExit' is false,
        // it means that we don't need it any node
        if( serialized && !serialized.saveOnExit ) {
            try { serialized.deleteSessionFile() }
            catch( Exception e) { log.debug("Error deleting session file", e) }
            serialized = null
        }

        if( serialized ) {
            def newConfig = getConfigBuilder().buildConfig( clusterName )
            if( serialized.confHashCode != newConfig.hashCode() ) {
                def answer = prompt("The configuration has changed. Do you want to C)ontinue previous session, load the N)ew configuration file or E)exit?", ['c','n','e'])
                if( 'e' == answer ) {
                    System.exit(0)
                }
                if( 'n' == answer ) {
                    try { serialized.deleteSessionFile() }
                    catch( Exception e) { log.debug("Error deleting session file", e) }
                    serialized = null
                }
            }
        }

        if( serialized ) {
            log.info "Restoring session for cluster: '${clusterName}' .. "
        }

        else {
            /*
            * read and validate the configuration
            */

            while( true ) {
                try {
                    config = getConfigBuilder().buildConfig( clusterName )
                    config.checkValid()
                    // configuration validated -> exit from the loop
                    break
                }
                catch( MissingAccessCredentials e ) {
                    println "Missing AWS credentials -- Please provide the information as requested below"
                    promptForAccessCredentials(config)
                    // note: invalidate the 'configBuilder' to force to re-parse it in the next iteration
                    // by the 'getConfigBuilder()' method
                    configBuilder = null
                }
                catch( MissingKeyException e ) {
                    def answer = prompt("The required key file: '${e.keyFile}' does not exist. Do you Blow to create it?", ['y','n'])
                    if( answer == 'y' ) {
                        KeyPairBuilder.create()
                                .privateKey( config.privateKey )
                                .publicKey( config.publicKey )
                                .store()
                    }
                    else {
                        println "Blow requires a valid key-pair to continue. Configure them in the '${userConfigFile}' file."
                        System.exit 2
                    }
                }
                catch( BlowConfigException e ) {
                    log.error("Configuration error: ${e.message}", e)
                    System.exit 2
                }
                catch( Exception e ) {
                    log.error ("Unknown error: ${e.message}", e )
                    System.exit 2
                }
            }
        }


		/*
		 * if OK, close the previous instance 
		 * and create a new one for this cluster instance
		 */
		if( session ) {
            try { session.close() } catch( Exception e ) { log.warn("Opps .. something wrong closing session: '${session.clusterName}'", e) }
            if( session.saveOnExit ) {
                session.persist()
            }
        }
		session = serialized ?: new BlowSession(config, clusterName)
		
		// set the current cluster name 
		currentCluster = clusterName

        // return the session
        return session
	} 




	/**
	 * Execute the requested command
	 */
	def void execute( String command, def args ) {
        log.debug("** ${command} ${args}")

        def message

        def cmdObj = availableCommands[command]
        if( cmdObj ) {
            try {

                /* Inject the shell instance to the command */
                injectProperties(cmdObj)

                cmdObj.parse(args)
                cmdObj.invoke()
            }

            /*
             * handles the various exceptions
             */
            catch( ShellExit e ) {
                throw e
            }
            catch( CommandSyntaxException e ) {
                def show = []
                if( e.getMessage() ) { show << e.getMessage()?.trim() }
                def hh = cmdObj.getHelp()?.trim()
                if( hh ) show << hh
                println show.join('\n\n')
            }
            catch( OperationAbortException e ) {
                message = "Operation aborted: '${cmdObj.getName()}'"
                log.warn(message)
            }
            catch( IllegalShellOptionException e ) {
                message = e.getMessage()
                log.warn(message,e)
            }

            catch( Exception e ) {
                message = e.getMessage() ?: (e.getCause()?.getMessage() ?: e.toString())
                log.error(message, e)
            }
        }
        /*
         * User entered an unknown command
         */
        else {
            println "Unknown command: '${command}'"
        }

	}

	/**
	 * Main execution method 	
	 */
	@Override
	public void run() {


        /*
         * This should be the normal case
         * Initialize the cluster configuration with the entered name
         */
        if( mainCommand?.name != "help" ) {
            useCluster( clusterNameRequested )
        }


		/*
		 * if an command is provided, execute and return
		 */
		if( mainCommand ) {
            mainCommand.with {
                execute( name, args )
                addToHistory( name, args)
            }
            return;
		}

		/*
		 * otherwise enter in a 'shell' loop
		 */
		while( true ) {
		    
            def line = prompt()
			def cmdLine = CmdLine.splitter(line)
			if( !cmdLine || cmdLine.size()==0 ) { continue }


			try {
				execute( cmdLine.head(), cmdLine.tail() );
                addToHistory(line)
			}
			catch( ShellExit e ) { break }
			catch( Throwable e ) {
				System.err.println "Cannot execute: '${line}'. Cause: ${e.getMessage()}"
                log.debug("Error executing command: '${line}'", e)
			}
						
		}

		println "Bye"
	}

    private void injectProperties( ShellCommand cmd ) {

        def props = [ this, this.session ]
        def target = ( cmd instanceof ShellMethodAdapter ) ? (cmd as ShellMethodAdapter).targetObj : cmd
        injectFields(target, props)
    }


    /**
     *  Invoke the free method on each command
     */
    private void freeCommands () {

        availableCommands.each { String name, ShellCommand cmd ->
            try {
                cmd ?. free()
            }
            catch( Exception e ) {
                log.warn "Oops .. error freeing command: ${name}"
            }
        }

    }

	def void close() {
		// close the 'blow' session
        freeCommands()
		if( session ) session.close()
        log.trace 'After close session'
	} 

    /**
     * Add the command to the command line history
     */

    def void addToHistory( String name, def args = null ) {

        def cmd = name
        cmd += (args instanceof Collection) ? ' ' + args.join(' ') : ( args ? args.toString() : '' )
        console.history.addToHistory(cmd)

    }

	/**
	 * Just wait for an user console entry 
	 * 
	 * @return the entered text
	 */
	def String prompt( String prompt = null, Closure<String> accept = null ) {
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

    /**
     * Wait for an the user console input. Only the entries specified as the second
     * parameters will be accepted as valid.
     *
     * @param text The string value to show on the input prompt
     * @param options A list of valid entries that will accepted, otherwise
     * it will continue to prompt for an answer
     */
    def String prompt ( String text, List<String> options  ) {
        assert options, "You should provide at least one entry in the 'options' list parameter"

        def show = (text ?: "") + " [${options.join('/')}]"
        prompt( show ) { it in options }

    }

    def String promptYesOrNo( String query ) {
        prompt(query,['y','n'])
    }

    /**
     * Prompt the users for the AWS access credentials and store them
     * in to the default configuration file
     *
     * @param config The current {@link BlowConfig} object
     */
    def void promptForAccessCredentials(BlowConfig config) {

        def defAccessKey = config.accessKey
        def defSecretKey = config.secretKey
        def defAccountId = config.accountId

        def strAccessKey = defAccessKey ? " [${defAccessKey}]" : ''
        def strSecretKey = defSecretKey ? " [${defSecretKey}]" : ''
        def strAccountId = defAccountId ? " [${defAccountId}]" : ' (optional)'

        def value
        while( true ) {
            print "Enter the AWS Access key${strAccessKey}: "
            value = console.readLine() ?: defAccessKey
            if ( value ) break
        }
        defAccessKey = value


        while( true ) {
            print "Enter the AWS Secret key${strSecretKey}: "
            value = console.readLine() ?: defSecretKey
            if (value) break
        }
        defSecretKey = value


        print "Enter the AWS Account id${strAccountId}: "
        defAccountId = console.readLine() ?: defAccountId


        def text = homePathConfigFile.getText()
        text = addOrReplaceAccessProperties(text, defAccessKey,defSecretKey,defAccountId)
        homePathConfigFile.setText(text)

        log.info "Note: credential store in the following file: ${homePathConfigFile}"
    }

    static String addOrReplaceAccessProperties(String text, String accessKey, String secretKey, String accountId) {

        def pattern = /accessKey '.+'/
        if ( text =~ pattern ) {
            text = text.replaceAll(pattern, "accessKey '${accessKey}'")
        }
        else {
            text += "accessKey '${accessKey}'\n"
        }

        pattern = /secretKey '.+'/
        if ( text =~ pattern ) {
            text = text.replaceAll(pattern, "secretKey '${secretKey}'")
        }
        else {
            text += "secretKey '${secretKey}'\n"
        }

        pattern = /accountId '.+'/
        if ( text =~ pattern ) {
            text = text.replaceAll(pattern, accountId ? "accountId '${accountId}'" : '' )
        }
        else if( accountId ) {
            text += "accountId '${accountId}'\n"
        }

        return text

    }

    /**
     * Print the shell usage help
     */
	def void printUsage() {
        // set the formatter size
        if( getWidth() > cli.formatter.defaultWidth ) {
            cli.width = getWidth()-4
        }

        // print out the usage info
        cli.usage()

        // the list of command
        println "\ncommands:"
        printCommandsList()
	}

    def printCommandsList() {
        // get the longest command string
        def max = availableCommands.keySet().max { it.length() }
        max = max.length() // <-- note: the above returns the longest item, so now we get the real max value

        // print the 'help' for each command
        availableCommands.sort().each {
            name, _action ->
            println " ${name.padRight(max)} ${_action.getSummary()?.trim() ?: ''}"
        }
    }



    /**
     * @return The current shell terminal width (characters)
     */
    def getWidth() {
        console ? console.getTermwidth() : 0
    }

    /**
     * @return The current shell terminal height (characters)
     */
    def getHeight() {
        console ? console.getTermheight() : 0
    }
	
	/**
	 * Shell entry point 
	 * 
	 * @param args
	 */

    static final transient def LOGO =
        """\
           ___  __
          / _ )/ /__ _    __
         / _  / / _ \\ |/|/ /
        /____/_/\\___/__,__/  ver: ${Project.number}
        """
        .stripIndent()

	public static void main( String[] args ) {

        println LOGO

        /*
         * parse the command line options
         */
        BlowShell.cli = new CliBuilder()
        cli.usage = "usage: ${Project.name} [options] [command [arguments..]]"
        cli._( longOpt: "debug", "Print debug level information", args:1, optionalArg:true, argName: 'package(s)')
        cli._( longOpt: "trace", "Print trace level information", args:1, optionalArg:true, argName: 'package(s)')
        cli._( longOpt: "conf", "Specify a configuration file other than 'blow.conf'", args: 1, optionalArg: false, argName:  'file name')
        cli.c( longOpt: "cluster", "Specify the cluster name to be used", args:1, optionalArg: false, argName: 'clusterName')
        cli.h( longOpt: "help", "Show this help")

        options = cli.parse(args)
        if( !options ) {
            System.exit()
        }

        /*
         * Initialize Guice and create a shell instance
         */
        Guice.createInjector();
		BlowShell shell = new BlowShell();
        // trace the command line
        log.debug ">>>>> Launching ${Project.name} - ver ${Project.version} <<<<<"
        log.debug "cmdline: \"${args?.join(' ') ?: ''}\""

        /*
         * Shutdown runtime
         */
        Runtime.getRuntime().addShutdownHook {
            if( shell?.session?.saveOnExit ) {
                def file = shell.session.persist()
                if( file ) {
                    log.info "Session saved to file: $file"
                }
            }
            log.debug "===== Terminated ${Project.name} ====="
        }

        def confFileName = options?.conf ?: null
        def clusterName = options?.cluster ?: null

        if( options?.help ) {
            shell.printUsage()
            System.exit(0)
        }

        /*
         * start the shell
         */
        try {
            shell.init(options.arguments(), confFileName, clusterName);
            shell.run();
            shell.close()
        }
        catch( Exception e ) {
            log.error(e.getMessage() ?: e.toString(), e)
            System.exit(1)
        }

	}
}

