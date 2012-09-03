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
import blow.exception.BlowException
import blow.util.InjectorHelper
import com.beust.jcommander.JCommander
import groovy.util.logging.Slf4j

import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException
import blow.exception.CommandSyntaxException

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


    /** The closure to be used for the auto-completion feature. It get a string parameter and returns a list of strings */
    private Closure completion

    private BlowShell shell

    private Method free

    private Class paramsClass = Object;

    private JCommander paramsParser

    private Object paramsObj

    private boolean showHelp

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

        log.trace "Creating adapter for method: '${method.getName()}'"
        this.method = method
        this.shell = owner
        this.targetObj = method.getDeclaringClass().newInstance()
        this.completion = method.getAnnotation(Completion)?.value()?.newInstance(targetObj,targetObj)

        this.cmd = method.getAnnotation(Cmd)

        /*
         * Infer argument command parameters type class
         */
        log.trace "Params for method '${method.getName()}' :: ${method.getParameterTypes()}"
        if( method.getParameterTypes().length > 0 ) {
            paramsClass = method.getParameterTypes()[0]

            /* only sub-classes of CmdParams will be managed by JCommander */
            if( CmdParams.isAssignableFrom(paramsClass)) {
                try {
                    paramsObj = paramsClass.newInstance()
                    paramsParser = new JCommander(paramsObj)
                }
                catch( InstantiationException e ) {
                    throw new BlowException("Unable to create command '${getName()}' parameters class: '${paramsClass.simpleName}' -- make sure it has a defaut constructor")
                }
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


    }


    // the name is defined by the annotation (if not empty) or fallback on the method name
    @Override
    String getName() {
        cmd.name() ?: method.getName()
    }

    @Override
    String getSummary() {
        method.getAnnotation(Cmd) ?. summary()
    }

    @Override
    String getHelp() {

        def result = new StringBuilder()
        result.append( super.getHelp() )


        /*
         * Usage
         */

        def usageText = cmd.usage()
        def optionsText = null

        // the options as produced by JCommand, if exist
        if( paramsParser ) {
//            paramsParser.setProgramName( getName() )
//            if( shell.getWidth() < paramsParser.getColumnSize() ) {
//                paramsParser.setColumnSize(shell.getWidth())
//            }
            optionsText = new StringBuilder()
            paramsParser.usage(optionsText)
        }

        // replace the first line one the options with the provided 'usage' string
        if( optionsText?.size() && usageText ) {
            def lines = optionsText.readLines()
            lines[0] = ''.padLeft(LEFT_PADDING) + usageText + '\n'
            usageText = lines.join('\n')
        }
        // remove the prefix 'Usage: ' in the string produced by JCommander
        else if ( optionsText?.size() ) {
            def lines = optionsText.readLines()
            lines[0] = lines[0].toString().replace('Usage: ', ''.padLeft(LEFT_PADDING)) + '\n'
            usageText = lines.join('\n')
        }

        if ( usageText ) {
            result.append("\n")
            result.append("USAGE") .append("\n")
            result.append(usageText)
            result.append("\n")
        }

        /*
         * Description
         */
        def descriptionText = cmd.description()
        if ( descriptionText ) {
            result.append("\n")
            result.append("DESCRIPTION") .append("\n")
            result.append(descriptionText)
            result.append("\n")
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
    @Override
    public void parse( List<String> args ) {

        if ( paramsParser ) {
            paramsObj = paramsClass.newInstance()
            try {
                paramsParser = new JCommander(paramsObj, args as String[])
                paramsParser.setProgramName(getName())
                paramsParser.setColumnSize( shell.getWidth() )
                showHelp = (paramsObj as CmdParams).help
            }
            catch( Exception e ) {
                throw new CommandSyntaxException("Invalid command syntax: ${e.getMessage()}",e)
            }
        }
        else {
            paramsObj = args

            // is it a 'help' parameter ?
            if( paramsObj instanceof List ) {
                showHelp = paramsObj.contains('-h') || paramsObj.contains('--help')
            }
            else if ( paramsObj instanceof CharSequence ) {
                showHelp = paramsObj.toString() == '-h' || paramsObj.toString() == '-h'
            }
            else {
                showHelp = false
            }

        }

    }



    /**
     * Execute the command invoking the target method
     */
    @Override
    void invoke() {

        log.debug("Invoke ${getName()} ( ${paramsObj} )")

        if( showHelp ) {
            print getHelp()
            return
        }

        try {
            doInvoke()
        }
        catch( InvocationTargetException e ) {
            // unwrap the source exception
            if( e.getCause() ) {
                throw e.getCause()
            }
            throw new BlowException("Oops .. command '${name}' fail", e)
        }

    }

    private void doInvoke() {

        /*
        * When the method does not declare any parameter, just invoke it
        */
        def len = method.getParameterTypes().length
        if( len == 0 ) {
            method.invoke(targetObj)
            return
        }

        if( len > 1 ) {
            log.warn("Multiple arguments are not supported by the ${ShellMethodAdapter} system")
        }

        def methodArgs = new Object[len]
        if( paramsClass.isAssignableFrom(paramsObj.getClass()) ) {
            methodArgs[0] = paramsObj
        }
        else if ( paramsClass.isAssignableFrom(String) ) {
            methodArgs[0] = paramsObj instanceof Collection ? paramsObj.join(' ') : ( paramsObj ? paramsObj.toString() : null )
        }
        else {
            throw new BlowException("Cannot use arguments of type: ${paramsObj?.class?.simpleName} to shell method: ${getName()}(${paramsClass?.simpleName})")
        }

        method.invoke( targetObj, methodArgs  )
    }

    @Override
    public void free() {
        if( free ) free.invoke(targetObj)
    }

}
