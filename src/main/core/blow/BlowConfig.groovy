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

import blow.exception.BlowConfigException
import blow.exception.MissingKeyException
import blow.operation.OperationHelper
import blow.operation.Validate
import com.google.common.base.Charsets
import com.google.common.io.Files
import groovy.util.logging.Slf4j
import org.codehaus.groovy.util.HashCodeHelper
import org.jclouds.domain.LoginCredentials

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import blow.operation.HostnameOp

/**
 * Contains all configuration required to handle a cluster 
 * 
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
class BlowConfig  {

	def accessKey
	def secretKey
    def accountId

	def regionId 
	def zoneId

	def imageId
	def instanceType 
    def instanceNum = 1

	def userName
	File publicKey
	File privateKey
    def keyPair

    def Boolean createUser
    def securityId

    /** Port used by Fast Data Transport (FDT) protocol */
    def int fdtPort = 9000

    /** Which port to open using the syntax: n,m,from-to */
    def String inboundPorts

    def List<String> roles = ['master','worker']

    /** The role used by the 'master' node, by default defined as the first entry in the {@link #roles} list */
    def String masterRole

    /** The role used by the 'worker' nodes by default defined as the second entry in the {@link #roles} list */
    def String workersRole

    /** The list of all operation defined in the configuration */
	List operations = []

    /** The {@link org.jclouds.domain.LoginCredentials} object */
	@Lazy
    protected transient LoginCredentials credentials = {
		log.debug "Creating lazy credential for user: ${userName} - key file: '${privateKey}'"

        String theKey = Files.toString(privateKey, Charsets.UTF_8);
        return LoginCredentials.builder().user(userName).privateKey(theKey).build();

    }()


    /** Protected constructor used only for testing purpose */
    protected BlowConfig () {


	}

    def void initDefaults() {

        if( !regionId )  {
            regionId = "us-east-1"
        }

        if( !zoneId ) {
            zoneId = regionId + "a"
        }


        if( !userName ) {
            userName = System.getProperty("user.name")
        }


        if( keyPair && !privateKey ) {
            File keyFile = new File("./${keyPair}.pem")
            if( !keyFile.exists() ) keyFile = new File( System.properties['user.home'], ".ssh/${keyPair}.pem")

            if( keyFile.exists() ) {
                privateKey = keyFile
                // it will be used the provider key-pair, so no user will be create
                if( createUser == null ) createUser = false
            }
        }


        // if not keys has been specified fallback to the default private key
        if ( !keyPair && !privateKey && !publicKey ) {
            privateKey = getDefaultKeyFile()
            publicKey = new File( privateKey.toString() + ".pub" )
            // if are used the default key, the user have to be created remotely
            if( createUser == null ) createUser = true
        }

        /*
         * FDT port
         */
        if( !fdtPort ) {
            fdtPort = 50000
        }

        /*
         * Inbound ports
         */
        if( !inboundPorts ) {
            inboundPorts = "22,${fdtPort}"
        }


    }


    public String imageIdFor(String role) {
        if( imageId instanceof Map ) {
            return imageId[role]
        }

        return imageId
    }

    public String instanceTypeFor(String role) {
        if( instanceType instanceof Map ) {
            return instanceType[role]
        }

        return instanceType
    }

    public int instanceNumFor( String role ) {
        if( instanceNum instanceof Map ) {
            return instanceNum[role]
        }

        if( role != roles.last() ) {
            return 1
        }

        return instanceNum - roles.size() +1
    }


    def String getMasterRole() {
        masterRole ?: ( roles.size()>0 ? roles.get(0) : null )
    }

    def String getWorkersRole() {
        workersRole ?: ( roles.size()>1 ? roles.get(1) : null )
    }

	def void checkValid() {
		log.debug("Validating configuration")
		
		checkTrue( accessKey, "Missing 'accessKey' attribute in cluster configuration")
		checkTrue( secretKey, "Missing 'secretKey' attribute in cluster configuration")
		checkTrue( imageId, "Missing 'imageId' attribute in cluster configuration")

        /*
         * Check thar roles are valid
         */
        checkTrue( roles, "Missing 'roles' attribute in cluster configuration" )
        roles.each {
            checkTrue( it ==~ /[A-Za-z_\-]+/, "The following role is not valid: '${it}'. Roles can contain only alphabetic plus '-' and '_' chars" )
        }

        if( masterRole ) {
            checkTrue( masterRole in roles, "The role defines by the attribute 'masterRole' should be defined in the 'roles' atributes as well" )
        }

        if( workersRole ) {
            checkTrue( workersRole in roles, "The role defines by the attribute 'workersRole' should be defined in the 'roles' atributes as well" )
        }

        /*
         * The property 'instanceNum' can be a single integer representing the number ot total
         * instances to launch - or - a map specifying the number of instances in each 'role'
         */
        checkTrue( instanceNum, "Missing 'instanceNum' attribute. You need to specifiy the number of nodes in your cluster configuration")

        if( instanceNum instanceof Map )  {
            instanceNum.each { key, val ->
                checkTrue( key in roles, "Unknown role definition '${key}' in 'instanceNum' declaration: '${instanceNum}' " )
                checkTrue( val > 0, "Invalid number of instance for role '${key}' in 'instanceNum' declaration: '${instanceNum}'" )
            }
        }


		/*
		 * validate credentials
		 */
		checkTrue( userName, "Missing cluster user name. Please provide attribute 'userName' in your configuration")


        if( keyPair && (!privateKey || !privateKey.exists()) ) {
            throw new BlowConfigException("Missing key file for specified keypair '${keyPair}'")
        }

        /*
         * Verify keys
         */
        // now verify that this keys exists otherwise raise an exception
        if( !privateKey.exists() ) {
            throw new MissingKeyException(privateKey)
        }

        if( createUser && !publicKey.exists() ) {
            throw new MissingKeyException(publicKey)
        }


        if( !privateKey.text.startsWith("-----BEGIN RSA PRIVATE KEY-----") \
            && !privateKey.text.startsWith("-----BEGIN DSA PRIVATE KEY-----") \
            && !privateKey.text.startsWith("-----BEGIN PRIVATE KEY-----") )
        {
             throw new BlowConfigException("Invalid private key file format: '$privateKey'")
        }

        if( publicKey )  {
            if( !publicKey.text.startsWith("ssh-rsa") \
		    && !publicKey.text.startsWith("ssh-dss") \
		    && !publicKey.text.startsWith("ssh") )
            {
                throw new BlowConfigException("Invalid public key file format: '$publicKey'")
            }
        }

        /*
         * Validate port numbers
         */
        if( fdtPort < 1 || fdtPort > 65535 ) {
            throw BlowConfigException("Invalid port number FDT service ($fdtPort)")
        }


        int[] ports = getPortsArrays( inboundPorts )
        if( !( 22 in ports ) ) {
            throw BlowConfigException("Missing SSH port (22) in declared 'inboundPorts' attribute.")
        }

        if( !( fdtPort in ports ) ) {
            throw BlowConfigException("Missing FDT port ($fdtPort) in declared 'inboundPorts' attribute.")
        }


        /*
         * validate operations
         */
        checkOperations()

	}

    def void checkOperations() {

        if( operations == null ) { operations = [] }

        if( !operations.find { it.getClass()==HostnameOp } ) {
            log.debug "Adding ${HostnameOp.getSimpleName()} operation by default"
            operations.add(0, new HostnameOp())
        }

        operations.each { op ->
            /*
             * Find all the validator method (marked with the annotation 'Validate'
             * and invoke them
             */
            def validators = op.getClass().getMethods() ?. findAll {  Method method -> method.getAnnotation(Validate) }
            validators.each {
                Method method -> invokeValidator(op,method)
            }
        }

    }

    def void invokeValidator(Object op, Method method) {

        def types=method.getParameterTypes()
        Object[] args = new Object[ types.length ];

        /*
         * check if the method defines some a parameters
         */
        if( types.length > 0 ) {

            if(  types[0]==BlowConfig.class || types[0]==Object.class) {
                args[0] = this
            }

            if( args[0]==null || types.length>1 ) {
                log.warn("Operation '${op.getClass().getSimpleName()}#${method.getName()}' should declare at most one parameter of type BlowConfig");
            }
        }

        /*
         * invoke the validator
         */
        try {
            method.invoke(op,args)
        }
        catch( InvocationTargetException e ) {
            /*
            * Normalize the exception class
            */
            def cause = e.getCause();
            switch( cause?.getClass() ) {
                case AssertionError:
                    throw new BlowConfigException("Failed validation: " + cause.getMessage(), cause)
                    break;

                case BlowConfigException:
                    throw cause;
                    break;

                default:
                    throw new BlowConfigException(cause);
            }
        }
        catch( BlowConfigException e ) {
            throw e;
        }
        catch( Throwable e ) {
            throw new BlowConfigException(e);
        }
    }

	
	private void checkTrue( def valid, String message) {
		if( !valid ) throw new BlowConfigException(message)
	} 


    private static getDefaultKeyFile() {
        def result = [:]

        def localPrivateKeyFile = new File('./id_rsa')
        def localPublicKeyFile = new File('./id_rsa.pub')
        if( localPrivateKeyFile.exists() && localPublicKeyFile.exists() ) {
            return localPrivateKeyFile
        }

        def ssh = new File(System.getProperty("user.home"),'.ssh')
        def privateKeyFile = new File(ssh,"id_rsa")
        def publicKeyFile = new File(ssh,"id_rsa.pub")

        if( privateKeyFile.exists() && publicKeyFile.exists() ) {
            return privateKeyFile
        }

        privateKeyFile = new File(ssh,"id_dsa")
        publicKeyFile = new File(ssh,"id_dsa.pub")

        if( privateKeyFile.exists() && publicKeyFile.exists() ) {
            return privateKeyFile
        }


        /*
         * when none of them exists fallback to non-existing keys in the current path
         */
        return localPrivateKeyFile


    }

    /**
     * @return The total number of instances to launch
     */
    def int getSize() {
        if( instanceNum instanceof Integer ) {
            return instanceNum
        }

        if( instanceNum instanceof Map ) {
            def result = 0
            instanceNum.each { key, value ->
                result += value
            }
            return result
        }

        return 0
    }


    /**
     * Converts a port specification string to an array of integer.
     * For example:
     * <pre>
     *     '80' --> [ 80 ]
     *     '80,8080' --> [ 80, 8080 ]
     *     '80,9001-9004' --> [ 80, 9001, 9002, 9003, 9004 ]
     * </pre>
     *
     * @param ports
     * @return
     */
    static def getPortsArrays( String ports ) {

        def result = []

        def items = ports.split(',')
        items .each {

            if( it.isInteger() ) {
                result.add(it.toInteger())
            }
            else if( it .contains('-') ) {
                def pair = it.split('-')
                if( !pair || pair.length !=2 || !pair[0].isInteger() || !pair[1].isInteger() ) {
                    log.warn("Invalid TCP ports range: '${it}'")
                }
                else {
                     result.addAll( pair[0].toInteger()..pair[1].toInteger() )
                }
            }

            else {
                log.warn("Invalid TCP port value: '${it}'")
            }

        }

        return result as int[]

    }


    @Override
    def int hashCode() {
        def hash = HashCodeHelper.initHash()

        def excludes = ['class','metaClass', 'operations', 'defaultKeyFile']
        metaPropertyValues.each { PropertyValue prop ->
            if( prop.name in excludes ) return
            hash = HashCodeHelper.updateHash(hash,prop.value)
        }

        operations?.each { Object op ->
            def opHash = OperationHelper.opHashCode( op )
            hash = HashCodeHelper.updateHash(hash,opHash)
        }


        return hash
    }


}
