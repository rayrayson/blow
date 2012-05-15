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
import blow.plugin.PluginFactory
import blow.plugin.Validate
import com.google.common.base.Charsets
import com.google.common.io.Files
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigList
import com.typesafe.config.ConfigValue
import groovy.util.logging.Slf4j
import org.jclouds.domain.LoginCredentials

import java.lang.reflect.Method
import java.lang.reflect.InvocationTargetException

/**
 * Contains all configuration required to handle a cluster 
 * 
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
class BlowConfig {

	def accessKey
	def secretKey
    def accountId

	def regionId 
	def zoneId 
	def imageId 
	def instanceType 
	def size = 1

	def userName
	File publicKeyFile
	File privateKeyFile

	List plugins
	
	@Lazy LoginCredentials credentials = {
		log.debug "> Creating lazy credential"

		try {
		  String privateKey = Files.toString(privateKeyFile, Charsets.UTF_8);
		  return LoginCredentials.builder().user(userName).privateKey(privateKey).build();
		}
		catch (Exception e) {
			log.error("Cannot read user private key: '${publicKeyFile}'")
			return null;
		}
			
	}()

    @Lazy
	PluginFactory pluginFactory = { new PluginFactory( DynLoaderFactory.get() ) }()


    /** Protected constructor used only for testing purpose */
    protected BlowConfig () {

    }


	public BlowConfig( String conf, String clusterName = null ) {
		this( ConfigFactory.parseString(conf), clusterName )	
	}
	
	public BlowConfig( Config conf, String clusterName = null ) {
		
		if( clusterName && conf.hasPath(clusterName) ) {
			conf = conf.getConfig(clusterName).withFallback(conf);
		}

        /*
         * The account ID
         */
        accountId = getString(conf, "account-id")

		/*
		 * credentials
		 */
		accessKey = getString(conf, "access-key") 		
		secretKey = getString(conf, "secret-key")
		
		/*
		 * region & zone
		 */
		regionId = getString(conf, "region-id", "us-east-1")
		zoneId = getString(conf, "zone-id", regionId + "a" ) 
		
		/*
		 * VM properties
		 */
		imageId = getString(conf, "image-id", null)
		instanceType = getString(conf, "instance-type", "t1.micro")
		
		/*
		 * the cluster size
		 */
		if( conf.hasPath("size") ) {
			size = conf.getInt("size")
		}
		
		/*
		 * credential 
		 */
		userName = getString(conf, "user-name", System.getProperty("user.name")) 
		privateKeyFile = getFile(conf,"private-key")
		publicKeyFile = getFile(conf,"public-key")

        // if not keys has been specified fallback to the default private key
        if ( !privateKeyFile && !publicKeyFile ) {
            privateKeyFile = getDefaultKeyFile()
        }

        // use by default the same as private + '.pub' extension
        if( privateKeyFile && !publicKeyFile ) {
            publicKeyFile = new File( privateKeyFile.toString() + ".pub" )
        }


		/*
		 * size of the cluster
		 */
		//size = conf.hasPath("size") ? conf.getIn
		
		/*
		 * Fetch all the plugin declared. Plugins can be declared 
		 * as a single item or a list of plugins configuration
		 */
		def pluginsConfs 
		if( conf.hasPath("plugin") ) {
			
			// we try first to read a list, if the user need just one plugin it can be entered 
			// omitting the list syntax
			def val = conf.getValue("plugin")
			pluginsConfs = val instanceof ConfigList ? val : [ val ]
			
		}
		else {
			pluginsConfs = []	
		}
		
		/*
		 * create an instance for each declared plugin
		 */
		def pluginList = []
		pluginsConfs.each {
			
			if( !( it instanceof ConfigValue ) ) {
				throw new BlowConfigException( "Invalid plugin declaration for: " + it )
			}
			
			/*
			 * create an instance for defined plugin
			 */
			def plugin = pluginFactory.createWithConf( it );
			if( !plugin ) {
				throw new BlowConfigException( "Invalid plugin declaration for: " + it.render() )
			}
			
			pluginList.add( plugin )
		}
		
		this.plugins = pluginList
	}
		
	
	def void checkValid() {
		log.debug("Validation configuration") 
		
		checkTrue( accessKey, "Missing Cloud access key")
		checkTrue( secretKey, "Missing Cloud secret key")
		checkTrue( imageId, "Missing VM 'image-id' in cluster configuration")
		checkTrue( size>0, "Missing 'size' attribute. You need to specifiy the number of nodes in your cluster configuration")
		
		/*
		 * validate credentials
		 */
		checkTrue( userName, "Missing cluster user name. Please provide attribute 'user-name' in your configuration")

        /*
         * Verify keys
         */
        // now verify that this keys exists otherwise raise an exception
        if( !privateKeyFile.exists() ) {
            throw new MissingKeyException(privateKeyFile)
        }

        if( !publicKeyFile.exists() ) {
            throw new MissingKeyException(publicKeyFile)
        }


        if( !privateKeyFile.text.startsWith("-----BEGIN RSA PRIVATE KEY-----") \
            && !privateKeyFile.text.startsWith("-----BEGIN DSA PRIVATE KEY-----") )
        {
             throw new BlowConfigException("Invalid private key file format: '$privateKeyFile'")
        }


		if( !publicKeyFile.text.startsWith("ssh-rsa") \
		    && !publicKeyFile.text.startsWith("ssh-dss") )
        {
            throw new BlowConfigException("Invalid public key file format: '$publicKeyFile'")
        }

        /*
         * validate plugins
         */
        checkPlugins()

	}

    def void checkPlugins() {

        if( !plugins ) return;

        plugins.each { plugin ->
            /*
             * Find all the validator method (marked with the annotation 'Validate'
             * and invoke them
             */
            def validators = plugin.getClass().getMethods() ?. findAll {  Method method -> method.getAnnotation(Validate) }
            validators.each {
                Method method -> invokeValidator(plugin,method)
            }
        }

    }

    def void invokeValidator(Object plugin, Method method) {

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
                log.warn("Plugin '${plugin.getClass().getSimpleName()}.${method.getName()}' should declare at most one parameter of type BlowConfig");
            }
        }

        /*
         * invoke the validator
         */
        try {
            method.invoke(plugin,args)
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
                    throw BlowConfig(cause);
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
	
	private String getString( Config conf, String key, String defValue = null) {
        return conf.hasPath(key) ? conf.getString(key) : defValue
	}

    private File getFile( Config conf, String key, File defValue = null ) {
        def val = conf.hasPath(key) ? conf.getString(key) : null
        return val ? new File(val) : defValue
    }
	
	def getConfMap() {

		def result = [:]

        result.put("user-name", userName)
        result.put("private-key", privateKeyFile)
        result.put("public-key", publicKeyFile)
        result.put("account-id", accountId ?: '--')
        result.put("access-key", accessKey)
		result.put("region-id", regionId)
		result.put("zone-id", zoneId)
		result.put("image-id", imageId)
		result.put("instance-type",instanceType)
		result.put("size", size)


        return result
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



}
