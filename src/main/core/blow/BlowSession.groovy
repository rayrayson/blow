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

package blow

import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.common.collect.ImmutableSet
import com.google.common.eventbus.EventBus
import com.google.inject.Module
import groovy.util.logging.Slf4j
import org.jclouds.Constants
import org.jclouds.compute.ComputeService
import org.jclouds.compute.ComputeServiceContext
import org.jclouds.compute.ComputeServiceContextFactory
import org.jclouds.compute.domain.ExecResponse
import org.jclouds.compute.domain.NodeMetadata
import org.jclouds.compute.domain.NodeState
import org.jclouds.compute.domain.TemplateBuilder
import org.jclouds.compute.options.TemplateOptions
import org.jclouds.compute.reference.ComputeServiceConstants
import org.jclouds.enterprise.config.EnterpriseConfigurationModule
import org.jclouds.scriptbuilder.domain.Statement
import org.jclouds.scriptbuilder.statements.login.AdminAccess
import org.jclouds.sshj.config.SshjSshClientModule
import blow.ssh.ScpClient
import blow.events.*

import java.util.concurrent.*

import static com.google.common.base.Predicates.not
import static org.jclouds.compute.predicates.NodePredicates.TERMINATED
import static org.jclouds.compute.predicates.NodePredicates.inGroup
import blow.storage.BlockStorage
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule

/**
 * Base session initializer
 * 
 * @author Paolo Di Tommaso
 *
 */

@Slf4j
class BlowSession {

	final ComputeServiceContext context;

	final BlowConfig conf;
	
	final String clusterName
	
	final EventBus eventBus
	
	Map<String,ExecResponse> response = [:]
    Map<String,ExecResponse> errors = [:]
	
	@Lazy 
	ComputeService compute = getLazyComputeService() 
	
	@Lazy
	private BlockStorage blockstore = new BlockStorage(context,conf)
	
	/*
	 * The thread pool to handle ssh upload / download 
	 */
	@Lazy 
	private ExecutorService scpExecutor = new ThreadPoolExecutor(0, 20, 60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>())
	
	/**
	 * The metadata for the master node
	 */
	private NodeMetadata masterMetadata
	
	/**
	 * The Pilot core object creator
	 * 
	 * @param conf
	 * @param clusterName
	 */
	BlowSession(BlowConfig conf, String clusterName) {
		assert conf, "Argument 'conf' cannot be null on PilotBase constructor"
		assert clusterName, "Argument 'clusterName' cannot be empty"
		
		this.conf = conf;
		this.clusterName = clusterName
		this.context = createContext(conf)
		
		/*
		 * create the event bus for the plugin system
		 * and register all the plugins
		 */
		eventBus = new EventBus()
		conf.plugins.each { eventBus.register(it) } 
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
	
	private ComputeService getLazyComputeService() {
		log.debug("> Getting lazy ComputeService")
		context.getComputeService()
	}

	public BlockStorage getBlockStore() { blockstore }

	/**
	 * Create an instance of the specific cluster 
	 * 
	 */
	public void createCluster() {
		log.info("Creating cluster: $clusterName")
		
		/*
		 * send the cluster create event
		 */
		eventBus.post( new OnBeforeClusterCreationEvent(session: this, clusterName: clusterName) )
	
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
		eventBus.post( new OnAfterClusterCreateEvent(session: this, clusterName:clusterName, nodes: allNodes) )
	}

	private startNodes( TemplateBuilder template, int numberOfNodes, String role ) {
		
		// note this will create a user with the same name as you on the
		// node. ex. you can connect via ssh public ip
        def builder = new AdminAccess.Builder()
		AdminAccess admin = builder
                            .adminUsername( conf.userName ) 
                            .adminPublicKey( conf.publicKeyFile )   
                            .adminPrivateKey( conf.privateKeyFile )
                            .build()

		TemplateOptions opt = new TemplateOptions()
		opt.runScript(admin)
		opt.userMetadata("Role", role)
		
		/*
		 * send the before creation event
		 */
		eventBus.post( new ONBeforeNodeStartEvent(
			session: this,
			clusterName: clusterName, 
			numberOfNodes: numberOfNodes, 
			role: role, 
			options: opt
			) )
		
		/*
		 * Start the requested nodes
		 */
		def setOfNodes = compute.createNodesInGroup(clusterName, numberOfNodes, template.options(opt).build())
		
		/*
		 * send the after creation event
		 */
		eventBus.post( new OnAfterNodeStartEvent(
			session: this,
			clusterName: clusterName, 
			numberOfNodes: numberOfNodes, 
			role: role, 
			nodes: (setOfNodes.size()==1 ? setOfNodes.find() : setOfNodes)
			) )
	
		/*
		 * returns the set of metadate for the started nodes
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
		eventBus.post( new OnBeforeClusterTerminationEvent(session: this, clusterName: groupName) );
		
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
		eventBus.post( new OnAfterClusterTerminationEvent(session: this, clusterName: groupName, nodes: result) );
		
		return result
	}
	 
	public NodeMetadata getMasterMetadata() {
		if( masterMetadata ) {
			masterMetadata
		}
		
		/* try to discover it */
		masterMetadata = context.getComputeService().listNodesDetailsMatching(filterMasterNode()).find()
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
		def response = context.getComputeService().runScriptOnNodesMatching(whichNodes, script, opt)
		
		return checkForValidResponse(response)
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
        def response = context.getComputeService().runScriptOnNodesMatching(nodesFilter, statement, opt)

        return checkForValidResponse(response)

    }
	
	/**
	 * Givan a map of response check if ALL are OK (exitcode == 0)
	 * <p>
	 * It will also update the 'response' map. The user can access it to know more abot the errors raised
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
	
	def void printNodeInfo( String istanceId ) {
	
		def node = compute.getNodeMetadata(istanceId);
		println "+ ID: " + node.getId()
		println "+ Name: " + node.getName()
		println "+ Provider ID: " + node.getProviderId()
		println "+ Type: " + node.getType()
		println "+ Credential: " + node.getCredentials()
		println "+ Group: " + node.getGroup()
		println "+ Hardware: " + node.getHardware();
		println "+ Hostname: " + node.getHostname();
		println "+ Imageid: " + node.getImageId()
		println "+ LoginPort: " + node.getLoginPort()
		println "+ OS: " + node.getOperatingSystem()
		println "+ Private addr: " + node.getPrivateAddresses()
		println "+ Public addr: " + node.getPublicAddresses()
		println "+ State: " + node.getState()
		println "+ Tags: " + node.getTags().join("; ")
		println "+ Metadata: " + node.getUserMetadata()
		println "+ Uri: " + node.getUri()
		println "+ Location: " + node.getLocation()
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
		if( context ) context.close()	
		if( scpExecutor ) scpExecutor.shutdown()
	} 
	
	
	public String getConfString() {
		
		String result = "cluster: $clusterName\n"
		
		conf.getConfMap().each {
			result <<= "${it.key}: ${it.value}\n"	
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
	
}
