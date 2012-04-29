/*
 * Copyright (c) 2012. Paolo Di Tommaso.
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

import java.lang.reflect.Method
import org.apache.commons.cli.Option
import org.apache.commons.cli.OptionBuilder
import groovy.util.logging.Slf4j
import java.lang.reflect.Field
import blow.BlowSession
import java.lang.reflect.Modifier

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
private class ShellMethodAdapter extends AbstractShellCommand implements CommandCompletor {

    /** The object instance declaring the method */
    private def declaringObj

    /** The method to invoke */
    private Method method

    /** The command annotation for the method */
    private Cmd cmd

    /** The declared option annotations in the method parameters */
    private Opt[] methodOpts

    /** The parsed arguments */
    private def args

    private def OptionAccessor options

    /** The closure to be used for the auto-completion feature. It get a string parameter and returns a list of strings */
    private Closure completion

    private CliBuilder cli;

    private StringWriter usageStr

    private BlowShell shell

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
        this.declaringObj = method.getDeclaringClass().newInstance()
        this.completion = method.getAnnotation(Completion)?.value()?.newInstance(declaringObj,declaringObj)

        this.cmd = method.getAnnotation(Cmd)


        /*
         * Find out all the Opt annotation declared on the method parameters
         */
        methodOpts = new Opt[ method.getParameterTypes().length ]
        for( int i=0; i<method.getParameterAnnotations().length; i++ ) {
            def it = method.getParameterAnnotations()[i]
            if( it && it[0] instanceof Opt) {
                methodOpts[i++] = it[0]
            }
        }


        
        /*
         * create the Command Line builder to handle CLI parameters for this command
         */
        this.usageStr = new StringWriter()
        this.cli = new CliBuilder( )
        cli.usage = cmd.value()
        cli.writer = new PrintWriter(usageStr)

        for( int i=0; i<methodOpts.length; i++ ) {
            Opt annotation = methodOpts[i]
            if( !annotation ) continue

            Option option
            if( annotation.name() ) {
                option = new Option( annotation.name(), annotation.description() )
                if( annotation.longOpt() ) option.setLongOpt(annotation.longOpt())
            }
            else if( annotation.longOpt() ) {
                option = OptionBuilder.withLongOpt(annotation).create();
                if( annotation.description() ) option.setDescription(annotation.description())
            }
            else {
                log.warn "Missing name parameter on shell method '${method.getName()}'"
                continue
            }

            if( method.getParameterTypes()[i] != Object ) option.setType(method.getParameterTypes()[i])
            if( annotation.required() ) option.setRequired(true)
            if( annotation.argName() ) option.setArgName(annotation.argName())
            if( annotation.args() ) option.setArgs(annotation.args())
            if( annotation.optionalArg() ) option.setOptionalArg(true)
            if( annotation.valueSeparator() ) option.setValueSeparator(annotation.valueSeparator().charAt(0))

            cli << option

        }

        if( cli.options.options.size() == 0 ) {
            cli = null // we don't need it
        }

    }

    /**
     * Inject the {@link BlowShell} and {@link BlowSession} instances in the method's declaring object
     */
    private void injectFields(def obj) {

        obj.getMetaClass().getProperties().each { MetaProperty field ->

            if( field.type == BlowShell ) {
                field.setProperty(obj, this.shell)
            }
            else if( field.type == BlowSession ) {
                field.setProperty(obj, this.shell.session)
            }
           
        }

        
    }



    // the name is defined by the annotation (if not empty) or fallback on the method name
    @Override
    public String getName() {
        cmd.value() ?: method.getName()
    }

    @Override String getSynopsis() {
        method.getAnnotation(Synopsis) ?. value()
    }
    
    
    @Override
    public String getHelp() { 
        def result = []
        if( getSynopsis() ) {
            result.add(getSynopsis())
        }

        if( cli ) {
            cli.usage()
            result.add( usageStr )
        }

        if( result ) result.join("\n")
    }

    List<String> findOptions( String cmdline ) {
        if( completion ) {
            injectFields(declaringObj)
            completion.call(cmdline)
        }
    }

    /*
     * the command line arguments provided by the user
     */
    public void parse( def args ) {
        if( cli ) {
            this.options = cli.parse(args)
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
         * Inject the shell object
         */
        injectFields(declaringObj)

        /*
         * When the method does not declare any parameter, just invoke it
         */
        if( method.getParameterTypes().length == 0 ) {
            method.invoke(declaringObj)
            return
        }

        /*
         * Otherwise we need to populate an array of object according the
         * CLI options defined for the method parameters
         */
        Opt opt
        def val
        def methodArgs = new Object[ method.getParameterTypes().length ]


        for( int i=0; i<methodArgs.length; i++ ) {
            // if the parameter is annotated with the option annotation
            // we get the value form the CLI's option object
            if( (opt=methodOpts[i]) ) {
                if( !options ) continue
                if( opt.name() ) val = options[ opt.name() ]
                else if( opt.longOpt() ) val = options[ opt.longOpt() ]
                else val = null

            }

            // non-annotated param
            else {
                def type = method.getParameterTypes()[i]
                if( type == Object || List.isAssignableFrom(type) ) {
                    val = args
                    args = []
                }
                else if( type.isArray() ) {
                    val = args.toArray()
                    args = []
                }
                else if( args ) {
                    def item = args.head();

                    if( item && type.isAssignableFrom(item.getClass())) {
                        val = item
                        args = args.tail()
                    }

                }
            }

            // assign the value
            methodArgs[i] = val

        }


        method.invoke( declaringObj, methodArgs )

    }
   

}
