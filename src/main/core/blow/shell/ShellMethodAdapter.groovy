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

import blow.BlowSession
import groovy.util.logging.Slf4j
import org.apache.commons.cli.Option
import org.apache.commons.cli.OptionBuilder

import java.lang.reflect.Method
import blow.exception.IllegalShellOptionException
import blow.util.InjectorHelper

/**
 * This class converts a generic method marked with the {@link Cmd} annotation to be sued
 * as a command in the shell provided by the framework.
 * <p>
 * Any public attribute with the types {@link BlowShell} and {@link BlowSession} will be injected
 * automatically by this adapter, in this way it is possible the method may have access to any features
 * provided by that classes.
 *
 * <p>
 * For example:
 * <pre>
 * class AnyName {
 *    def BlowSession session
 *
 *    @Cmd
 *    def void myCustomExtension {
 *         :
 *    }
 *
 * }
 *
 * </pre>
 *
 * @author Paolo Di Tommaso
 */

@Slf4j
@Mixin(InjectorHelper)
private class ShellMethodAdapter extends AbstractShellCommand implements CommandCompletor {

    /** The object instance declaring the method */
    private def targetObj

    /** The method to invoke */
    private Method method

    /** The command annotation for the method */
    private Cmd cmd

    /** The declared option annotations in the method parameters */
    private Opt[] methodOpts

    /** The parsed arguments */
    private List<String> args

    private def OptionAccessor options

    /** The closure to be used for the auto-completion feature. It get a string parameter and returns a list of strings */
    private Closure completion

    private CliBuilder cli;

    private StringWriter usageStr

    private BlowShell shell

    private static final int LEFT_PADDING = 4

    /* This flags is used to mark that the 'help' option has to be handled automatically by this method */
    private boolean showsDefaultHelp


    private Method free

    /**
     * Crete the adapter for the specified method. The method parameter cannot be null and it is assumed that
     * it is marked with the {@link Cmd} annotation.

     *
     * @param owner The reference to the {@link BlowShell} instance
     * @param method The {@link Method} instance to be used as shell command
     */
    ShellMethodAdapter( BlowShell owner, Method method ) {
        assert method
        assert method.getAnnotation(Cmd)

        this.method = method
        this.shell = owner
        this.targetObj = method.getDeclaringClass().newInstance()
        this.completion = method.getAnnotation(Completion)?.value()?.newInstance(targetObj,targetObj)

        this.cmd = method.getAnnotation(Cmd)

        /*
         * Find out all the Opt annotation declared on the method parameters
         */
        methodOpts = new Opt[ method.getParameterTypes().length ]
        for( int i=0; i<method.getParameterAnnotations().length; i++ ) {
            def it = method.getParameterAnnotations()[i]
            if( it && it[0] instanceof Opt) {
                methodOpts[i] = it[0]
            }
        }

        /*
         * Check for the free method
         */
        Method[] all = method.getDeclaringClass().getDeclaredMethods()
        for( Method mm : all ) {
            if( mm.getAnnotation(CmdFree) ) {
                free = mm
                break
            }
        }

        /*
         * create the Command Line builder to handle CLI parameters for this command
         */

        this.cli = new CliBuilder( )
        this.cli.formatter.setLeftPadding(LEFT_PADDING)

        cli.usage = cmd?.usage() ?: getName()


        for( int i=0; i<methodOpts.length; i++ ) {
            Opt annotation = methodOpts[i]
            if( !annotation ) continue

            Option option

            if( annotation.opt() ) {
                option = new Option( annotation.opt(), annotation.description() )
                if( annotation.longOpt() ) option.setLongOpt(annotation.longOpt())
            }
            else if( annotation.longOpt() ) {
                option = OptionBuilder.withLongOpt(annotation.longOpt()).create();
            }
            else {
                log.warn "Missing name parameter on shell method '${method.getName()}'"
                continue
            }

            // set the option type
            if( method.getParameterTypes()[i] != Object ) option.setType(method.getParameterTypes()[i])

            annotation.with {
                // set the description
                if( description() ) option.setDescription(description())

                // set the argument name , if not defined fallback to the 'long' option name
                if( arg() ) option.setArgName(arg())
                else if ( longOpt() && len() ) option.setArgName(longOpt())

                // the number of arguments
                if( annotation.len() ) option.setArgs(annotation.len())
                else if( arg() ) option.setArgs(1)

                // set if has optional argument value
                if( annotation.optional() ) option.setOptionalArg(true)
                else if( annotation.len() ) option.setOptionalArg(false)

                // set if required
                if ( annotation.required() ) option.setRequired(true)

                // set the separator
                if( annotation.sep() ) option.setValueSeparator(annotation.sep().charAt(0))
            }


            cli << option
        }

        /*
         * Add an help options by default if not specified
         */
        if( cli && (!cli.options.hasOption('h') && !cli.options.hasOption('help') )) {
            def opt = new Option( 'h', 'Show the help for this command' )
            opt.setLongOpt('help')
            cli << opt
            showsDefaultHelp = true
        }

    }

    // the name is defined by the annotation (if not empty) or fallback on the method name
    @Override
    public String getName() {
        cmd.name() ?: method.getName()
    }

    @Override String getSummary() {
        method.getAnnotation(Cmd) ?. summary()
    }
    
    
    @Override
    public String getHelp() { 
        def cmd = method.getAnnotation(Cmd.class)
        def result = new StringBuilder()

        /*
         * name + summary
         */
        result.append("NAME") .append("\n")
        result.append("".padLeft(LEFT_PADDING)).append(getName())

        def summary = cmd?.summary()
        if( summary ) {
            result.append(" -- ") .append(summary)
            result.append("\n")
        }

        /*
         * usage
         */
        cli.writer = new PrintWriter( usageStr = new StringWriter() )
        if( shell.getWidth() > cli.formatter.defaultWidth ) {
            cli.width = shell.getWidth()-4
        }
        cli.usage()

        if( usageStr ) {
            result.append("\n")
            result.append("SYNTAX") .append("\n")
            result.append("".padLeft(LEFT_PADDING)).append(usageStr)
            result.append("\n")
        }

        if( cmd.description() ) {
            result.append("\n")
                .append("DESCRIPTION").append("\n")
                .append(cmd.description()) .append('\n\n')
        }

        return result.toString()
    }

    List<String> findOptions( String cmdline ) {
        if( completion ) {
            completion.call(cmdline)
        }
    }

    /*
     * the command line arguments provided by the user
     */
    public void parse( def args ) {

        if( cli ) {
            this.options = cli.parse(args)
            if( !options) {
                throw new IllegalShellOptionException(usageStr.toString())
            }
            this.args = options.arguments()

        }
        else {
            this.args = args
            this.options = null
        }

    }

    /**
     * Execute the command invoking the target method
     */
    @Override
    void invoke() {

        /*
         * Check if the help has been invoked
         */
        if ( options?.hasOption('help') && showsDefaultHelp ) {
            print getHelp()
            return
        }

        /*
         * When the method does not declare any parameter, just invoke it
         */
        if( method.getParameterTypes().length == 0 ) {
            method.invoke(targetObj)
            return
        }

        /*
         * Otherwise we need to populate an array of object according the
         * CLI options defined for the method parameters
         */
        Opt opt
        def methodArgs = new Object[ method.getParameterTypes().length ]

        for( int i=0; i<methodArgs.length; i++ ) {
            def val = null
            def Class type = method.getParameterTypes()[i]

            // if the parameter is annotated with the option annotation
            // we get the value form the CLI's option object
            if( (opt=methodOpts[i]) ) {

                if( !options ) continue
                if( opt.opt() ) val = options[ opt.opt() ]
                else if( opt.longOpt() ) val = options[ opt.longOpt() ]
                else val = null

                if( val!=null && !type.isInstance(val) ) {
                    val = null
                }
            }

            // non-annotated param
            else {
                if( List.isAssignableFrom(type) ) {
                    def p=0
                    val = new ArrayList<String>(args.size())
                    for( def it : args) {
                        val[p++] = it?.toString()
                    }
                    args = []
                }
                else if( type.isArray() ) {
                    def p=0
                    val = new String[args.size()]
                    for( def it : args) {
                        val[p++] = it?.toString()
                    }
                    args = []

                }

                // when reach the end, but there are more than one elem in the array
                // pass the argument as an array
                if ( type == Object && i+1==methodArgs.length && args.size()>1 ) {
                    val = args
                    args = []
                }

                // otherwise as a single element
                else if( args ) {
                    def item = args.head()?.toString();

                    if( item && type.isAssignableFrom(item.getClass())) {
                        val = item
                        args = args.tail()
                    }
                }
            }

            // assign the value
            methodArgs[i] = val
        }

        log.debug("${getName()} (${methodArgs})")
        method.invoke( targetObj, methodArgs as Object[] )
    }

    @Override
    public void free() {
        if( free ) free.invoke(targetObj)
    }

}
