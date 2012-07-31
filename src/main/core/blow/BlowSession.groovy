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

import blow.eventbus.OrderedEventBus
import blow.exception.BlowConfigException
import blow.exception.DirtySessionException
import blow.exception.OperationAbortException
import blow.ssh.ScpClient
import blow.storage.BlockStorage
import blow.util.InjectorHelper
import blow.util.PromptHelper
import blow.util.Serializable
import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.common.collect.ImmutableSet
import com.google.inject.Module
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.StaxDriver
import groovy.util.logging.Slf4j
import org.jclouds.Constants
import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions
import org.jclouds.compute.ComputeService
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.compute.ComputeServiceContextFactory
import org.jclouds.compute.options.TemplateOptions
import org.jclouds.compute.reference.ComputeServiceConstants
import org.jclouds.ec2.EC2Client
import org.jclouds.ec2.services.KeyPairClient
import org.jclouds.ec2.services.SecurityGroupClient
import org.jclouds.enterprise.config.EnterpriseConfigurationModule
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule
import org.jclouds.scriptbuilder.domain.OsFamily
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.statements.login.AdminAccess
import org.jclouds.sshj.config.SshjSshClientModule

import java.lang.reflect.InvocationTargetException

import blow.events.*
import org.jclouds.compute.domain.*

import java.util.concurrent.*

import static com.google.common.base.Predicates.not
import static org.jclouds.compute.predicates.NodePredicates.TERMINATED
import static org.jclouds.compute.predicates.NodePredicates.inGroup
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter
import blow.operation.OperationHelper

/**
 * Base session initializer
 * 
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
@Mixin(PromptHelper)
@Mixin(InjectorHelper)
class BlowSession {

	final BlowConfig conf;

	final String clusterName

	transient OrderedEventBus eventBus

	transient Map<String,ExecResponse> response = [:]

    transient Map<String,ExecResponse> errors = [:]

    transient private boolean contextCreated

    @Lazy
	transient ComputeServiceContext context = { def ctx=createContext(conf); contextCreated=true; ctx }()

	@Lazy
    transient ComputeService compute = { context.getComputeService() }()
	
	@Lazy
	transient private BlockStorage blockstore = { new BlockStorage(context,conf) } ()

    @Lazy
    transient private SecurityGroupClient securityGroupClient = {
        (context.getProviderSpecificContext().getApi() as EC2Client) .getSecurityGroupServices()
    }()

    @Lazy
    transient private KeyPairClient keyPairClient = {
        (context.getProviderSpecificContext().getApi() as EC2Client) .getKeyPairServices()
    } ()


    // note this will create a user with the same name as you on the
    // node. ex. you can connect via ssh public ip
    @Lazy transient private AdminAccess adminAccessScript = {

        new AdminAccess.Builder()
                .adminUsername( conf.userName )
                .adminPublicKey( conf.publicKeyFile )
                .adminPrivateKey( conf.privateKeyFile )
                .build()

    } ()

	/*
	 * The thread pool to handle ssh upload / download 
	 */
	@Lazy 
	transient private ExecutorService scpExecutor = new ThreadPoolExecutor(0, 20, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>())
	
	/**
	 * The metadata for the master node
     * <p>
     * See {@link #getMasterMetadata()}
	 */
	transient private NodeMetadata masterMetadata

    /** Mark the session dirty as soon as the cluster is created */
    private boolean dirty

    private boolean saveOnExit

    private int confHashCode

    /** Keep the status of assigned devices */
    private def devicesMap = [
            "/dev/sdf":0,
            "/dev/sdg":0,
            "/dev/sdh":0,
            "/dev/sdi":0,
            "/dev/sdj":0,
            "/dev/sdk":0,
            "/dev/sdl":0,
            "/dev/sdm":0,
            "/dev/sdn":0,
            "/dev/sdo":0,
            "/dev/sdp":0
    ]


    /** Only for test */
    protected BlowSession( BlowConfig config = new BlowConfig()) {
        this.conf = config
    }


    /**
	 * The Pilot core object creator
	 * 
	 * @param conf
	 * @param clusterName
	 */
	BlowSession(BlowConfig conf, String clusterName) {
		assert conf, "Argument 'conf' cannot be null on PilotBase constructor"
		assert clusterName, "Argument 'clusterName' cannot be empty"

        log.debug("Creating BlowSession for cluster: '${clusterName}'")
		this.conf = conf;
		this.clusterName = clusterName

        eventBus = new OrderedEventBus()
        conf.operations.each { it ->
            eventBus.register(it)
            injectFields(it, [this])
        }

        // store the config object
        this.confHashCode = conf.hashCode()
	}

    /**
     * Managed by XStream during the de-serializaton phase
     */
    private Object readResolve() {
        log.debug('XStream desialization readResolve invoke')
        eventBus = new OrderedEventBus()
        conf.operations.each { it ->
            eventBus.register(it)
            /* note: here we don't need to inject session to the operation instances because the de-serialization
              process take care of that
             */
        }

        return this;
    }

	protected ComputeServiceContext createContext(BlowConfig conf) {
		
		/*
		* JClouds configuration
		*/
	   def props = new Properties();
	   props.setProperty("jclouds.ec2.ami-query", "")
	   props.setProperty("jclouds.ec2.cc-ami-query", "")
	   props.setProperty("jclouds.regions", conf.regionId)
	   
	   //props.setProperty( Constants.PROPERTY_USER_THREADS, "25" )
	   props.setProperty( Constants.PROPERTY_MAX_RETRIES, "7" );
	   props.setProperty( Constants.PROPERTY_RETRY_DELAY_START, "500" );
	   props.setProperty( ComputeServiceConstants.PROPERTY_TIMEOUT_NODE_RUNNING, 5 * 60 * 1000 + "" )
	   props.setProperty( ComputeServiceConstants.PROPERTY_TIMEOUT_PORT_OPEN, 5 * 60 * 1000 + ""  )
	   props.setProperty( ComputeServiceConstants.PROPERTY_TIMEOUT_SCRIPT_COMPLETE, 5 * 60 * 1000 + ""  )
	   

	   ComputeServiceContext result = new ComputeServiceContextFactory()
				  .createContext("aws-ec2",
								  conf.accessKey,
								  conf.secretKey,
								  ImmutableSet.<Module> of(new SLF4JLoggingModule(), new SshjSshClientModule(), new EnterpriseConfigurationModule()),
								  props
								  );
							  
		return result;
	} 
	

	public BlockStorage getBlockStore() { blockstore }

    /**
     * Submit the specified event to the registered plugins
     *
     * @param event
     */
    protected void postEvent( def event ) {
        assert event

        try {
            eventBus.post(event)
        }
        catch( OperationAbortException e ) {
            // just pass through
            throw e
        }
        catch( Exception e ) {
            if( e instanceof InvocationTargetException ) {
                e = e.getCause()
            }
            log.error("${event.getClass().getSimpleName()} raised an exception. Cause: " + (e.getMessage()?:e.toString()), e )

            // ask to the user if want to continue
            println "\nIt is strongly suggested to stop the process and review the cluster configuration!"
            if( promptYesOrNo("Do you want to continue?") == 'n' ) {
                throw new OperationAbortException()
            }
        }
    }


	/**
	 * Create an instance of the specific cluster 
	 * 
	 */
	public void createCluster() {
		log.info("Starting cluster: $clusterName")

        /*
         * make sure that the session can be started one and only one time
         */
        if( dirty ) {
            throw new DirtySessionException()
        }
        dirty = true

        /*
         * Verify the specified key-pair exists (if any)
         */
        if( conf.keyPair ) {
            log.debug("Check key-pair '${conf.keyPair}' existance")
            def result = keyPairClient.describeKeyPairsInRegion(conf.regionId,conf.keyPair)?.find()
            if( !result ) {
                throw new BlowConfigException("The specified key-pair '${conf.keyPair}' does not exist in region '${conf.regionId}'")
            }
        }


		/*
		 * send the cluster create event
		 */
		postEvent( new OnBeforeClusterStartEvent(session: this, clusterName: clusterName) )

        /*
         * Delete previously created security-group with the same name
         *
         * Since if a security group, with the specified props is NOT created, if already
        *  exists another group with the same name, delete it already exists
         */
        def securityToClear = "jclouds#${clusterName}#${conf.regionId}"
        log.debug "Cleating security group: '${securityToClear}'"
        securityGroupClient.deleteSecurityGroupInRegion( conf.regionId, securityToClear )

		/*
		 * Get the compute service
		 */
		def template = compute.templateBuilder()

		template.hardwareId( conf.instanceType )
		template.imageId( "${conf.regionId}/${conf.imageId}" )
		template.locationId( conf.zoneId )


		/*
		 * create the master node
		 */
		def allNodes = new TreeSet()
		log.info("Creating the 'master' node")
		masterMetadata = startNodes(template, 1, "master").find()
		allNodes.add(masterMetadata)

		/*
		 * create the 'worker' nodes
		 */
		def workerCount = conf.size-1
		if( workerCount ) {
			log.info("Creating ${workerCount} worker node(s)")
			def nodes = startNodes( template, workerCount, "worker" )

			allNodes.addAll( nodes )
		}
		else {
			log.debug("(no worker nodes required)")
		}

	
		/*
		 * notify the cluster creation 	
		 */
		postEvent( new OnAfterClusterStartedEvent(session: this, clusterName:clusterName, nodes: allNodes) )

        /* Require to save this session on exit */
        saveOnExit = true
	}


    /**
     * Start a set of nodes
     *
     * @param builder
     * @param numberOfNodes
     * @param role
     * @return
     */
    private startNodes( TemplateBuilder builder, int numberOfNodes, String role ) {

        Template template = builder.build()
		template.getOptions().userMetadata("Role", role)

        /*
         * set the credential to use
         */
        if( conf.createUser ) {
            log.debug("Creating admin access for user: '${conf.userName}'")
            template.getOptions().runScript(adminAccessScript)
        }
        else if( conf.keyPair ) {
            log.debug("Setting key-pair: " + conf.keyPair)
            template.getOptions().as(AWSEC2TemplateOptions).keyPair(conf.keyPair)
        }
        else {
            log.debug("Override login credentials: " + conf.credentials)
            template.getOptions().overrideLoginCredentials( conf.credentials )
        }

        // set the security group id
        if( conf.securityId ) {
            template.getOptions().as(AWSEC2TemplateOptions).securityGroupIds(conf.securityId)
        }
        else {
            int[] ports = BlowConfig.getPortsArrays ( conf.inboundPorts )
            template.getOptions().inboundPorts(ports)
        }

		/*
		 * send the before creation event
		 */
		postEvent( new OnBeforeNodeLaunchEvent(
			session: this,
			clusterName: clusterName, 
			numberOfNodes: numberOfNodes, 
			role: role, 
			options: template.getOptions()
			) )

        log.debug "Template metadata for '${role}': " + template.getOptions()?.getUserMetadata()
        //log.debug "Template options for '${role} == '" + template.getOptions()?.toString()

		/*
		 * Start the requested nodes
		 */
		def setOfNodes = compute.createNodesInGroup(clusterName, numberOfNodes, template)
		
		/*
		 * send the after creation event
		 */
		postEvent( new OnAfterNodeLaunchEvent(
			session: this,
			clusterName: clusterName, 
			numberOfNodes: numberOfNodes, 
			role: role, 
			nodes: (setOfNodes.size()==1 ? setOfNodes.find() : setOfNodes)
			) )
	
		/*
		 * returns the set of metadata for the started nodes
		 */
		return setOfNodes
	} 
		
	
	
	/**
	 * Terminates all the nodes in the specified group name 
	 * 
	 * @param groupName
	 */
	def terminateCluster( String groupName = clusterName ) {
		log.info "Terminating nodes in cluster '$groupName'"
		
		
		/*
		 * notify the event
		 */
		postEvent( new OnBeforeClusterTerminationEvent(session: this, clusterName: groupName) );
		
		/*
		 * apply the command
		 */
		def result = context.getComputeService().destroyNodesMatching( Predicates.<NodeMetadata> and(not(TERMINATED), inGroup(groupName)) )
		result.each {
				log.debug "- Terminated instance: ${it.providerId} "
		} 

		/*
		 * sent the complete notification
		 */
		postEvent( new OnAfterClusterTerminationEvent(session: this, clusterName: groupName, nodes: result) );

        /*
         * When the session si terminated, it is not more require to store it
         */
        saveOnExit = false

		return result
	}

	public NodeMetadata getMasterMetadata() {
        if( !masterMetadata ) {
            masterMetadata = context.getComputeService().listNodesDetailsMatching(filterMasterNode())?.find()
        }

        masterMetadata
    }
	 
	/**
	 * Define a predicate to filter all nodes marked by 'Role' tag a cluster. 
	 * 
	 * @param role The required role string. Currently are used <code>master</code> and <code>worker</code>
	 * @return A {@link Predicate<NodeMetadata>} instance matching for the required role for the running nodes in the current cluster 
	 */
	def Predicate<NodeMetadata> filterByRole( final String role ) {

		def nodeFilter = new Predicate<NodeMetadata>() {
			boolean apply( NodeMetadata it) {
				def map = it.getUserMetadata()
				return it.getGroup() == clusterName && it.getState() == NodeState.RUNNING && map.containsKey("Role") && map["Role"] == role
			}
		}

	}
	
	def Predicate<NodeMetadata> filterAll( final groupName = clusterName ) {
		
		new Predicate<NodeMetadata>() {
			boolean apply( NodeMetadata it ) {
				def map = it.getUserMetadata()
				return it.getGroup() == groupName && it.getState() == NodeState.RUNNING 
			}
		}

	}

    def Predicate<NodeMetadata> filterByPublicAddress( String publicAddress ) {
        assert publicAddress

        new Predicate<NodeMetadata>() {
            boolean apply( NodeMetadata it ) {
                def map = it.getUserMetadata()
                return it.getGroup() == clusterName \
                    && it.getState() == NodeState.RUNNING \
                    && it.getPublicAddresses().find( {  it =~ publicAddress  } )
            }
        }

    }
	
	/**
	 * Define a predicate filter matching all nodes marked by the 'master' role
	 * 
	 * @return The {@link Predicate<NodeMetadata>} instance matching the above rule
	 */
	def Predicate<NodeMetadata> filterMasterNode( ) {  filterByRole("master")	} 

	/**
	* Define a predicate filter matching all nodes marked by the 'worker' role
	*
	* @return The {@link Predicate<NodeMetadata>} instance matching the above rule
	*/
	def Predicate<NodeMetadata> filterWorkerNodes( ) { filterByRole("worker")  }
	
	/**
	 * List all the node metadata matching for the specified 'role' string
	 * 
	 */
	def Set<? extends NodeMetadata> listByRole( final String role ) {
        compute.listNodesDetailsMatching( filterByRole(role) )
    }
	
	/**
	 * Run a shell script (sh/bash) on the remote 'master' 
	 * 	
	 * @param script
	 */
	def boolean runScriptOnMaster( String script, boolean runAsRoot = false ) {

		runScriptOnNodes( script, filterMasterNode(), runAsRoot )		

	}
	
	def boolean runScriptOnNodes( String script, Predicate<NodeMetadata> filter = null, boolean runAsRoot = false) {

		/*
		 * defines the credentials option
		 */
		def opt = TemplateOptions.Builder.overrideLoginCredentials(conf.credentials);
		
		/*
		 * run as 'root' if required
		 */
		opt.runAsRoot(runAsRoot)
		
		
		def whichNodes = filter ?: filterAll()
		def responses = context.getComputeService().runScriptOnNodesMatching(whichNodes, script, opt)

        logExecResponse(script, responses)

		return checkForValidResponse(responses)
	}
	
	/**
	 * Run a shell script (sh/bash) on the remote 'worker(s)' node as 'root' user
	 * 
	 * @param script
	 */
	def boolean runScriptOnWorkers( String script, boolean runAsRoot = false ) {

		runScriptOnNodes( script, filterWorkerNodes(), runAsRoot )		

	}

    def boolean runStatementOnNodes( Statement statement, Predicate<NodeMetadata> filter = null, boolean runAsRoot = false ) {

        /*
           * defines the credentials option
           */
        def opt = TemplateOptions.Builder.overrideLoginCredentials(conf.credentials);

        /*
           * run as 'root' if required
           */
        opt.runAsRoot(runAsRoot)


        def nodesFilter = filter ?: filterAll()
        def responses = context.getComputeService().runScriptOnNodesMatching(nodesFilter, statement, opt)

        logExecResponse(statement, responses)

        return checkForValidResponse(responses)
    }
	
	/**
	 * Givan a map of response check if ALL are OK (exitcode == 0)
	 * <p>
	 * It will also update the 'response' map. The user can access it to know more about the errors raised
	 * 
	 * @param mapOfResponses
	 * @return <code>true</code> if all responses contain a 0 as exit code, <code>false</code> otherwise
	 */
	private boolean checkForValidResponse( Map<? extends NodeMetadata, ExecResponse> mapOfResponses ) {
	
		def result = [:]
        def errors = [:]
		int errorCount = 0 
		mapOfResponses .each { node, response  -> 
			
			result.put( node.getId(), response )
            if( response.getExitCode() ) {
                errorCount++
                errors.put( node.getId(), response )
            }

            if( log.isDebugEnabled() && response.getExitCode() ) {
				String msg = 
				"""\
				Failed execution on node: ${node.getProviderId()}
				+ Exitcode: ${response.getExitCode()}
				+ Error: ${response.getError()}
				+ Output:\n${response.getOutput()}
				//end"
				""" .stripIndent()
				log.debug(msg)
			}
				
		}  	
	
		this.response = result;
        this.errors = errors;
		return errorCount == 0
		
	}  
	
	def getNodeInfoString( String instanceId ) {
        assert instanceId
        log.debug("getNodeInfoString for: ${instanceId}")

        def node = compute.getNodeMetadata(instanceId);

        def result = new StringBuilder()
        result << "+ ID: " << node.getId()
		result << "\n+ Name: " << node.getName()
		result << "\n+ Provider ID: " <<  node.getProviderId()
		result << "\n+ Type: " <<  node.getType()
		result << "\n+ Credential: " <<  node.getCredentials()
		result << "\n+ Group: " << node.getGroup()
		result << "\n+ Hardware: " << node.getHardware();
		result << "\n+ Hostname: " << node.getHostname();
		result << "\n+ Imageid: " << node.getImageId()
		result << "\n+ LoginPort: " << node.getLoginPort()
		result << "\n+ OS: " + node.getOperatingSystem()
		result << "\n+ Private addr: " << node.getPrivateAddresses()
		result << "\n+ Public addr: " << node.getPublicAddresses()
		result << "\n+ State: " << node.getState()
		result << "\n+ Tags: " << node.getTags().join("; ")
		result << "\n+ Metadata: " << node.getUserMetadata()
		result << "\n+ Uri: " << node.getUri()
		result << "\n+ Location: " << node.getLocation()

        return result.toString()
	}
	

	/**
	 * @return the list of current available cluster names
	 */
	def listClusters() {
		compute.listNodes()
                .findAll { NodeMetadata node -> node.group != null }
                .collect { NodeMetadata node -> node.group }
                .unique()
	} 
	
	/**
	 * @return the list of availables nodes in the cluster specified 
	 */
	def Set<? extends NodeMetadata> listNodes( String groupName = clusterName ) {
		compute.listNodesDetailsMatching( filterAll(groupName) )
	} 
	
	def Collection<String> listHostNames( String groupName = clusterName ) {
		listNodes(groupName).collect { it.getHostname() } 
	} 
	
	def close() {
        log.trace('Closing session')
		if( contextCreated ) context?.close()
        log.trace 'After close context'
		if( scpExecutor ) scpExecutor.shutdown()
        log.trace 'After shutdown executor'
	} 
	
	
	public String getConfString() {
		
		def result = new StringBuilder()
        result <<= "cluster: $clusterName\n"

		conf.getConfMap().each {
			result <<= "${it.key}: ${it.value}\n"	
		}

        if( conf.operations?.size() == 1 ) {
            result <<= "operations [ ${OperationHelper.opToString(conf.operations[0])} ] "
        }
        else if( conf.operations?.size() > 1 ) {
            result <<= "operations ["
            conf.operations.each { result <<= "\n  " + OperationHelper.opToString(it) }
            result <<= "\n]"
        }

		return result
	} 
	
	public String getUserInfo() {
		println context.credentialStore
	} 
	
	
	/**
	 * Upload a payload to a remoet host using ssh link 
	 * 
	 * @param payload a generic content, it could be an instance of {@link InputStream}, {@link byte[]}, {@link File}, {@link String} or {@link org.jclouds.io.Payload}
	 * @param targetPath the path on the remote system where to save the payload content 
	 * @param targetNode 
	 * @return
	 */
	protected void copyToNode( def payload, String targetPath, NodeMetadata targetNode ) {
		assert targetNode
		assert targetPath 
		
		log.debug("[scp] uploading to path: ${targetNode.getHostname()}:${targetPath}")

		def ip = targetNode.getPublicAddresses()?.find()
		log.debug("[scp] connecting host: ${ip}")
		def scp = new ScpClient()
		
		scp.connect( ip, conf.userName, conf.privateKeyFile )
		try {
			def result
			if( payload instanceof File ) {
				result = scp.uploadFile( payload, targetPath )
			}
			else if( payload instanceof String ) {
				result = scp.uploadString( payload, targetPath )
			}
			else {
				throw new RuntimeException("[scp] unsupported payload type [${payload.getClass().getName()}]")
			}
			
		} finally {
			scp.close()
		}
	}
	
	
	public boolean copyToNodes( def payload, String targetPath, Predicate<NodeMetadata> filter = null) {
		log.debug("copyToNodes: filter ${filter}")
		
		def nodes = filter ? compute.listNodesDetailsMatching(filter) : this.listNodes()
		
		/* 
		 * create a list with an upload job for each node 		
		 */
		def tasks = new ArrayList( nodes.size() )
		
		nodes.each { NodeMetadata node -> 
			
			tasks.add( new Callable<Object[]>() {
					public Object[] call( ) {
						def resp
						try {
							copyToNode( payload, targetPath, node )
							resp = new ExecResponse( "OK", null, 0 )
						}
						catch( Exception e ) {
							resp = new ExecResponse( "ERR", e.getMessage(), 1)
						}
						
						return [ node, resp ]
					}
				})
		} 
		
		/*
		 * Start all tasks and wait for scp upload finishes
		 */
		def results = scpExecutor.invokeAll(tasks)
		
		/*
		 * remap the list of futures to response map 
		 */
		def map = [:]
		results.each { future ->
				def ret = future.get()
				map.put( ret[0], ret[1] )
		}
		
		checkForValidResponse(map)
	}


    /**
     * Find all matching attributes in all run instances
     *
     * @param criteria
     * @return
     */
    def List<String> findMatchingAttributes( def criteria = null, def defAttribute = 'publicAddresses' ) {

        /*
        * Find all available IP addresses
        */
        def result
        if( !criteria ) {
            result = listNodes() .collect { NodeMetadata node  ->
                node[defAttribute]
            }
        }

        else {
            result = listNodes() .collect { NodeMetadata node ->
                def entries = []
                def ip = node.getPublicAddresses()?.find();
                if( ip?.startsWith(criteria) ) entries.add(ip)

                if( node.getProviderId()?.startsWith(criteria) ) {
                    entries?.add(node.getProviderId())
                }

                if( node.getHostname()?.startsWith(criteria) ) {
                    entries.add(node.getHostname())
                }

                return entries
            }
        }

        result = result.flatten()
        return result?.sort();

    }

    /**
     * Find the first node matching the specified attribute.
     * The node can be specified either with the:
     * - IP address
     * - the Hostname
     * - the InstanceID (providerID)
     *
     * @param value
     * @return
     */
    def NodeMetadata findMatchingNode( String value ) {

        def list = listNodes().findAll() { NodeMetadata node ->

            return node.getPublicAddresses()?.contains(value) \
                        || node.getHostname() == value \
                        || node.getProviderId() == value

        }

        if( list?.size() > 1 ) {
            log.warn "The specified attribute '$value' cannot identify uniquely a node"
            return null
        }

        return list?.size() > 0 ? list.find() : null

    }

    /**
     * @return The first available deviceName
     */
    def String getNextDevice( ) {

        synchronized (devicesMap) {
            // find the first device that has been never assigned (count == 0)
            def entry = devicesMap.find { device, count -> count == 0 }
            if( entry ) {
                entry.value++
                return entry.key
            }
            return null
        }

    }

    /**
     * Mark a device name as used.
     *
     * @param device The Linux device name e.g {@code /dev/sdf}
     * @return {@code true} if the device was not used before and it is marked as used successfully
     */
    def boolean markDevice( String device ) {

        synchronized (devicesMap) {
            // normalize to the device '/dev/xvd?' to '/dev/sd?'
            if( device =~ /\/dev\/xvd([f-p])/) {
                device = '/dev/sd' + device[-1]
            }

            if( devicesMap.containsKey(device) ) {
                devicesMap[device] ++

            }
            else {
                devicesMap[device] = 1
            }

            return devicesMap[device] == 1
        }
    }

    private def instancesLogFiles = [:]


    protected void logExecResponse( def script, Map<? extends NodeMetadata, ExecResponse> mapOfResponses ) {
         mapOfResponses ?. each { node, response -> logExecResponse(script,node,response) }
    }


    /**
     * Create a separate log file for each node.
     *
     * @param node
     * @param command
     * @param response
     */
    protected void logExecResponse( def command, NodeMetadata node, ExecResponse response ) {

        String script = command.toString()
        if( command instanceof Statement ) {
            script = (command as Statement) .render(OsFamily.UNIX)
        }

        def name = "node-${node.getProviderId()}.log";
        File file = instancesLogFiles[name]
        if( file == null ) {
            file = new File("logs", name)
            instancesLogFiles[name] = file
            if( !file.getParentFile().exists() ) {
                file.getParentFile().mkdirs()
            }

            /*
             * The very fist time, print out some node metadata information
             */
            file << '============================================================\n'
            file << getNodeInfoString( node.getId() ) << '\n'
            file << '============================================================\n'
            file << '\n'
        }


        /*
         * Save the executed command and the result in the log file
         */

        file << '\n==(run)=='
        file << '\n' << script

        // print the exit code
        file << "\n\n-- exit: ${response.exitCode}"

        // the returned standard output
        if( response.output ) {
            file << "\n-- out :\n${response.output}"
        }

        // the returned standard error
        if( response.error) {
            file << "\n-- err :\n${response.error}"
        }
        file << '\n//end\n'

    }

    /** Define the file where read/store the sessin object */
    def private static File sessionFile( String clusterName ) {
        def home = new File(System.properties['user.home'], '.blow')
        new File( home, ".blow_session.${clusterName}")
    }

    @Lazy
    transient private static XStream xstream = {
        def result = new XStream(new StaxDriver())
        result.alias("session", BlowSession.class)
        result
    } ()

    /**
     * Save the current session to a file
     *
     * @return The file where the session has been persisted, of {@code null} in it cannot stored
     */
    def File persist() {
        def file = sessionFile(clusterName)

        try {
            FileWriter writer = new FileWriter(file)
            xstream.marshal(this, new PrettyPrintWriter(writer))
            writer.close()

            return file
        }
        catch( Exception e ) {
            log.warn("Error saving session to file: '${file}'", e)
        }

    }

    /**
     * Read a session object from a serialized object
     *
     * @param clusterName The session name to restore
     * @return A {@code BlowSession} instance
     */
    def static BlowSession read( String clusterName ) {
        def file = sessionFile(clusterName)
        if( !file.exists() ) {
            return null
        }

        try {
            return (BlowSession)xstream.fromXML(file)
        }
        catch( Exception e ) {
            log.warn("Cannot read saved session: '${file}'", e)
            return null
        }

    }

    /**
     * Delete a serialized session file
     * @param clusterName The cluster name of the serialized session
     * @return {@code true} if the serialized session file has beend delete
     */
    def static boolean delete( String clusterName ) {
        def file = sessionFile(clusterName)
        if( file.exists() ) {
            return file.delete()
        }
        return false
    }

    /**
     * Delete a serialized session file, if exist
     * @return @{code true} if the serialized file has been deleted,
     * or {@false } if it does not exist or cannot be deleted
     */
    def boolean delete() {
        clusterName ? delete(clusterName) : false
    }

}

