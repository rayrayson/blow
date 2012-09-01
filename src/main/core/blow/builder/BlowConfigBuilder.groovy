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

package blow.builder

import blow.BlowConfig
import groovy.util.logging.Slf4j
import blow.exception.BlowConfigException
import blow.operation.OperationFactory
import blow.DynLoaderFactory

/**
 *
 *  @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */

@Slf4j
class BlowConfigBuilder extends BuilderExtended {

    @Lazy
    transient private OperationFactory operationFactory = { new OperationFactory( DynLoaderFactory.get() ) }()

    private BlowConfig config

    private List<String> clusterNames

    def String theClusterName

    def Expando root

    BlowConfigBuilder() {
    }

    BlowConfigBuilder(String clusterName) {
        this.theClusterName = clusterName
    }


    @Override
    protected Object createNode(Object name) {
        new Expando(name:name)
    }

    @Override
    protected Object createNode(Object name, Object value) {
        new Expando(name:name, value: value)
    }

    @Override
    protected Object createNode(Object name, Map attributes) {
        new Expando(name:name, value:attributes)
    }

    @Override
    protected Object createNode(Object name, Map attributes, Object value) {
        throw new MissingMethodException("Invalid values for attribute: '$name'")
    }

    @Override
    protected void setParent(Object parent, Object child) {
        if( !parent.children ) {
            parent.children = []
        }
        parent.children << child
    }

    /**
     * Intercept the process completion, when the parent is null
     * means that the root object has been found
     *
     * @param parent The parent object in the tree graph structure
     * @param node The node
     * @return
     */
    protected Object postNodeCompletion(Object parent, Object node) {
        if( parent ) {
            return node
        }

        return buildComplete(node)
    }

    /**
     * Convert the parsed tree graph structure made up of {@link Expando} objects
     * to a {@link blow.BlowConfig} object
     *
     * @param root
     * @return
     */
    protected Object buildComplete(def root) {

        /*
         * keep track of the root object
         */
        this.root = root

        /*
         * get the list of all cluster section defined
         */
        clusterNames = root.children ?.findAll { it.children } .collect { it.name }

        /*
         * parse the configuration tree and build a {@link BlowConfig} object
         */
        config = buildConfig(theClusterName)
    }

    /**
     *
     * @param aClusterName
     * @return
     */
    def BlowConfig buildConfig( String aClusterName ) {

        /*
        * create un instance of {@link BlowConfig}
        */
        def config = new BlowConfig()
        populateConfigWith(config, root)

        if( !aClusterName ) {
            config.initDefaults()
            return config
        }

        /*
         * look for the cluster section
         */
        def elem = root.children ?. find { it?.name == aClusterName && it.children }
        if( elem ) {
            populateConfigWith(config, elem)

            /*
             * find out the operations entries
             */
            def allOperationElemes = elem.children?.findAll {
                if( it.name == 'operations' && it.children ) {
                    it.children.each { op ->
                        setOperation(config, op, it.value)
                    }
                }
            }
        }

        config.initDefaults()
        return config
    }

    /**
     * Set the properties defined in the {@link Expando} obj to the matching
     * properties to {@link BlowConfig} obj
     *
     * @param config
     * @param obj
     */
    protected void populateConfigWith(BlowConfig config, Expando obj) {

        // find only children without a child .. i.e. a property value
        obj.children ?. each {
            if( !it.children && it.value ) {
                setConfigItem(config, it)
            }
        }

    }

    protected void setConfigItem(BlowConfig config, def prop) {
        if( !config.hasProperty(prop.name) ) {
            throw new BlowConfigException("Unknown configuration property: '${prop.name}'")
        }

        try {
            Class type = config.metaClass.getMetaProperty(prop.name)?.type
            if( List.isAssignableFrom(type) && !(prop.value instanceof List) ) {
                def list = new ArrayList()
                list.add(prop.value)
                prop.value = list
            }

            if( !type?.isInstance(prop.value) ) {
                throw new BlowConfigException("Cannot assign the value: ${prop.value} to property '${prop.name}' ")
            }

            config.setProperty(prop.name, prop.value)
        }
        catch( Exception e ) {
            e.printStackTrace()
            BlowConfigException("Cannot set property ${prop.name}=${prop.value}")
        }
    }


    protected void setOperation(BlowConfig config, Expando op, def applyTo ) {
        assert config
        assert op
        assert op.name


        def opName = op.name

        log.debug("Creating operation '${opName}'")
        def operation = operationFactory.create(opName)
        if( !operation ) {
            throw BlowConfigException("Unknown operation: '${opName}'")
        }

        /*
         * define to which nodes apply this operation
         */
        if( operation.hasProperty('applyTo') ) {
            operation.applyTo = applyTo
        }
        else {
            operation.metaClass.applyTo = applyTo
        }

        /*
         * now sets the operation properties
         */
        if( op.value ) {
            if( op.value instanceof Map ) {
                op.value.each { key, value ->
                    if( operation.hasProperty(key) ) {
                        operation[key] = value
                    }
                    else {
                        throw new BlowConfigException("Unknow attribute: '${key}' for operation: '${opName}' ")
                    }
                }

            }
            else {
                throw new BlowConfigException("Invalid attributes for operation: '${opName}'")
            }
        }

        config.operations.add(operation)

    }

    /**
     * @return The current {@link BlowConfig} instance
     */
    def BlowConfig getConfig() {
        config
    }

    /**
     * @return The list of 'cluster names' defined in the current configuration object
     */
    def List<String> getClusterNames() {
        clusterNames
    }

    /**
     * Merge multiple files into a single {@link BlowConfigBuilder} object
     *
     * @param files The array of files to parse
     * @return The {@link BlowConfigBuilder} instance
     */
    static BlowConfigBuilder create( File[] files ) {
        assert files
        create( files *. getText() as String[] )
    }

    /**
     * Merge multiple configuration strings and create a {@link BlowConfigBuilder} instance
     *
     * @param text An array configuration strings
     * @return A {@link BlowConfigBuilder} instance
     */
    static BlowConfigBuilder create( String[] text ) {

        StringBuilder config = new StringBuilder()
        config << "def builder = new ${BlowConfigBuilder.getName()}(); builder._ {\n"
        text .each { config << it << '\n' }
        config << "}\n"
        config << "return builder"

        GroovyShell shell = new GroovyShell()
        System.getenv().each { key, val ->
            shell.setVariable(key,val)
        }

        //println "\n\n${config.toString()}\n\n"

        return shell.evaluate( config.toString() )

    }



}
