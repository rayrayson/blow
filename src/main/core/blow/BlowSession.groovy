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
import blow.operation.OperationHelper
import blow.ssh.ScpClient
import blow.storage.BlockStorage
import blow.util.ArrayListMultimapConverter
import blow.util.HashBiMapConverter
import blow.util.InjectorHelper
import blow.util.PromptHelper
import com.google.common.base.Predicate
import com.google.common.base.Predicates
import com.google.inject.Module
import com.thoughtworks.xstream.XStream
import com.thoughtworks.xstream.io.xml.PrettyPrintWriter
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
import com.google.common.collect.*
import org.jclouds.compute.domain.*

import java.util.concurrent.*

import static com.google.common.base.Predicates.not
import static org.jclouds.compute.predicates.NodePredicates.TERMINATED
import static org.jclouds.compute.predicates.NodePredicates.inGroup
import blow.exception.BlowException

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
                .adminPublicKey( conf.publicKey )
                .adminPrivateKey( conf.privateKey )
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
    @Deprecated
	transient private NodeMetadata masterMetadata

    transient private Timer refreshTimer

    private long refreshLastRun

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

    /**
     * A map containing the pair <'Node name', 'Instance ID' > e.g. < 'worker1', 'us-east-1/i-0b075a70'>
     * <p>
     * The 'Instance ID' is defined by {@link NodeMetadata#getId()}
     */
    private BiMap<String, String> nodeNamesMap = HashBiMap.create()


    /**
     * A Multimap containing for each 'role' the associated collection of instance IDs e.g.
     *
     * <'master', ['us-east-1/i-0b075a70']>
     * <'worker', ['us-east-1/i-74837843', 'us-east-1/i-02909243', 'us-east-1/i-8093289'] >
     * <p>
     * The 'Instance ID' is defined by {@link NodeMetadata#getId()}
     */
    private Multimap<String,String> nodeRolesMap = ArrayListMultimap.create()

    /**
     * The map containing pair < Node name, Node metadata >
     */
    private Map<String,BlowNodeMetadata> allNodes = [:]

    private long nodesCacheDuration = 4 * 60 * 1000 // 4 min

    /** Only for test */
    protected BlowSession( BlowConfig config = new BlowConfig()) {
        this.conf = config
    }


    /**
	 * The Blow core object creator
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
     * Initialize the object when the session is de-serialized by XStream
     * <p>
     * It takes care to create transient objects
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

        /*
         * If there was a refresh timer, re-set it
         */
        resumeRefreshMetadata()

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
	   //props.setProperty( Constants.PROPERTY_RETRY_DELAY_START, "500" );
	   props.setProperty( Constants.PROPERTY_MAX_RETRIES, "3" );
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
            log.error( e.getMessage() ?: e.toString() , e )

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
         * Initialize the metadata structures
         */
        metadataInitialize()


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
        deleteDefaultSecurityGroup()


        /*
         * Creates the instances for each defined role
         */
        conf.roles.each { role ->

            /*
             * Get the compute service
             */
            def templateBuilder = compute.templateBuilder()

            templateBuilder.hardwareId( conf.instanceTypeFor(role) )
            templateBuilder.imageId( "${conf.regionId}/${conf.imageIdFor(role)}" )
            templateBuilder.locationId( conf.zoneId )


            /*
             * create the master node
             */
            def count = conf.instanceNumFor(role)
            if( count ) {
                def lbl = count==1 ? 'node' : 'nodes'
                log.info("Creating $count $lbl for role '${role}'")
                def nodes = startNodes(templateBuilder, count, role)

                if( !masterMetadata ) {
                    masterMetadata = nodes?.find()
                }
            }
            else {
                log.debug("(no '${role}' nodes required)")
            }            
        }


		/*
		 * notify the cluster creation 	
		 */
		postEvent( new OnAfterClusterStartedEvent(session: this, clusterName:clusterName, nodes: listNodes() ) )

        /*
         * refresh the nodes metadata to be sure that are updated after the previous event
         */
        scheduleRefreshMetadataNow()


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

		/*
		 * Start the requested nodes
		 */
        log.debug "Creating nodes cluster: $clusterName; numberOfNodes: $numberOfNodes; role: $role; template: $template"
		def setOfNodes = compute.createNodesInGroup(clusterName, numberOfNodes, template)

        /*
         * Update session metadata
         */
        metadataAddInstances(setOfNodes, role)
		
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
         * Cancel any pending refresh timer to avoid any potential side effects
         * on nodes metadata update during the shutdown process
         */
        cancelScheduledRefresh()

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

        // reschedule a metadata refresh to reflect the changes applied
        // and keep it running because this process can takes some seconds (or minutes) to complete
        scheduleRefreshMetadataNow()

        /*
         * When the session si terminated, it is not more require to store it
         */
        saveOnExit = false

        // delete the session file
        deleteSessionFile()

		return result
	}

    synchronized protected metadataInitialize() {

        int tot = 0;
        conf.roles.each { tot += conf.instanceNumFor(it) }

        nodeNamesMap = HashBiMap.create(tot)
        nodeRolesMap = ArrayListMultimap.create()
        allNodes = new LinkedHashMap<String, BlowNodeMetadata>(tot)  // <-- use a linked hash map to maintain the insertion order

        conf.roles.each {
            def count = conf.instanceNumFor(it)
            if( !count ) return

            for( int i=0; i<count; i++ ) {
                def name = getNextNodeName(it,i)
                allNodes.put( name, null )
            }

        }

    }

    synchronized protected void metadataAddInstances( Set<? extends NodeMetadata> setOfNodes, String role ) {
        assert role

        setOfNodes.each { NodeMetadata node ->
            def name = getNextNodeName(role)
            log.debug "Adding metadata for node ${name} - instance: ${node.getProviderId()} - IP: ${node.getPublicAddresses().find()} "
            nodeNamesMap.put( name, node.getId() )
            nodeRolesMap.put( role, node.getId() )
            allNodes.put(name, wrapNode(node))
        }
    }


    protected void metadataUpdate( def setOfNodes = null ) {
        log.debug "Metadata update"

        def nodeIdToName = nodeNamesMap.inverse()
        List<NodeMetadata> listOfNewNodes = []
        def listOfUpdatedNames = []

        /*
         * Find the 'fresh' metadata for the current nodes
         */
        if( !setOfNodes ) {
            setOfNodes = compute.listNodesDetailsMatching( new Predicate<NodeMetadata>() {
                boolean apply( NodeMetadata it ) {
                    return it.getGroup() == clusterName
                }
            })
        }


        /*
         * For each of the node in the new list
         * update the metadata for the existing entries in the 'allNodes' map
         */
        synchronized (this) {

            setOfNodes.each { NodeMetadata node ->

                if( nodeIdToName.containsKey(node.id) ) {
                    def name = nodeIdToName[ node.id ]
                    allNodes.put( name, wrapNode(node) )
                    listOfUpdatedNames << name
                }
                else {
                    listOfNewNodes << node
                }
            }

            if( listOfNewNodes ) {
                log.debug "Oops these nodes should not exist: ${listOfNewNodes *. getProviderId()}"
            }

            /*
            * Check if the 'setOfNodes' contain less nodes than in
            * 'allNodes' map
            */
            def missingNames = allNodes.keySet() - listOfUpdatedNames
            log.debug "Removing from cached nodes the following entries: $missingNames"

            missingNames .each { String name ->

                def node = allNodes.get(name)
                if( node ) {
                    nodeNamesMap.remove(name)
                    nodeRolesMap.remove( node.getNodeRole(), node.getId() )
                    allNodes.put(name, null) // note: this remove must be the last otherwise the above 'getNodeId()' and 'getNodeRole()' will fail
                }
                else {
                    log.debug "Unknown node: '$name'"
                }
            }
            // end of missingNames
        }

    }

    private BlowNodeMetadata wrapNode( NodeMetadata node ) {
        new BlowNodeMetadata(node)
    }


    /*
     * Resume the metadata refresh timer after a session continuation
     */
    protected void resumeRefreshMetadata() {
        log.debug "Resume refresh metadata timer - refreshLastRun: ${refreshLastRun} "

        long delay = 0
        if( refreshLastRun ) {
            long elapsed = System.currentTimeMillis() - refreshLastRun
            if( elapsed < nodesCacheDuration ) {
                delay = nodesCacheDuration - elapsed
            }

            log.debug("Refresh timer delay: ${delay / 1000} secs")
            scheduleRefreshMetadata(delay)
        }

    }

    /**
     * Refresh the nodes metadata scheduling a timer to keep them updated
     */
    protected void scheduleRefreshMetadataNow() {
        scheduleRefreshMetadata(0)
    }

    /**
     * Schedule a metadata update
     */
    protected void scheduleRefreshMetadata(long delay = nodesCacheDuration) {
        log.debug "Schedule refresh metadata with delay: ${delay/1000} secs"

        if( !allNodes ) {
            log.debug "Not install the refresh timer since the 'allNodes' map is empty "
            return
        }

        if( refreshTimer ) { refreshTimer.cancel() }

        refreshTimer = new Timer('BlowSyncTimer',true)
        refreshTimer.runAfter(delay.toInteger()) {
            log.debug("Refreshing nodes metadata")
            try {
                metadataUpdate()
                refreshLastRun = System.currentTimeMillis()
            }
            catch( Throwable e ) {
                log.warn("Oops .. unable to refresh nodes metadata. See log file for details.", e)
            }
            finally {
                log.debug("Refreshing nodes metadata - end")
                // schedule another iteration
                scheduleRefreshMetadata(nodesCacheDuration)
                log.debug("Re-scheduled metadata refresh")
            }
        }

    }


    /*
     * A background timer tries to keep the nodes metadata updates,
     * but the user may call this method to force a metadata update
     */
    public void refreshMetadata() {
        log.debug "Refreshing metadata"

        /*
         * cancel the current refresh timer
         */
        def thereWasTimer = cancelScheduledRefresh()

        try {
            /*
             * Update the metadata
             */
            metadataUpdate()

            // keep track of the last time it has been executed
            refreshLastRun = System.currentTimeMillis()
        }
        finally {
            /*
             * schedule a new refresh if there's a timer
             */
            if( thereWasTimer ) {
                resumeRefreshMetadata()
            }

        }
    }

    protected boolean cancelScheduledRefresh() {
        if( refreshTimer ) {
            log.debug "Cancel refresh timer"

            refreshTimer.cancel();
            refreshTimer=null

            return true
        }

        return false
    }


    @Deprecated
	public NodeMetadata getMasterMetadata() {
        if( !masterMetadata ) {
            def filter = filterByCriteria(conf.masterRole)
            masterMetadata = context.getComputeService().listNodesDetailsMatching(filter) ?.find()
        }

        masterMetadata
    }

    /**
     * Find out a list of node IDs given a 'node name' or a 'node role' or a combination of them
     *
     * @param criteria
     * @return
     */
    def List<String> findNodeIDs( def criteria ) {
        assert criteria

        def result = []
        if( criteria instanceof Collection || criteria.getClass().isArray() ) {
            criteria.each {
                if(it) {
                result.addAll( findNodeIDs(it) )
                }
            }
            return result
        }

        if( nodeNamesMap.containsKey(criteria) ) {
            result.add(nodeNamesMap.get(criteria))
        }
        else if( nodeRolesMap.containsKey(criteria) ) {
            result.addAll( nodeRolesMap.get(criteria) )
        }

        return result

    }

    /**
     * Get a predicate matching the specified criteria
     * @param criteria A specification of nodes. It could be a node name (as defined by blow), a node role or a combination of them (as list)
     * @return The matching criteria
     */

    def Predicate<NodeMetadata> filterByCriteria( final def criteria ) {
        assert criteria

        def listOfNodeIDs = findNodeIDs(criteria)

        def nodeFilter = new Predicate<NodeMetadata>() {
            boolean apply( NodeMetadata it) {
                return it.getGroup() == clusterName \
				    && it.getState() == NodeState.RUNNING \
				    && it.getId() in listOfNodeIDs
            }
        }
    }

	def Predicate<NodeMetadata> filterAll() {
		
		new Predicate<NodeMetadata>() {
			boolean apply( NodeMetadata it ) {
				return it.getGroup() == clusterName \
				    && it.getState() == NodeState.RUNNING
			}
		}

	}

    def Predicate<NodeMetadata> filterByPublicAddress( String publicAddress ) {
        assert publicAddress

        new Predicate<NodeMetadata>() {
            boolean apply( NodeMetadata it ) {
                return it.getGroup() == clusterName \
                    && it.getState() == NodeState.RUNNING \
                    && it.getPublicAddresses().find( {  it =~ publicAddress  } )
            }
        }

    }


	def boolean runScriptOnNodes( String script, def criteria = null, boolean runAsRoot = false) {

        def filter
        if( criteria == null ) {
            filter = filterAll()
        }
        else if( criteria instanceof Predicate<NodeMetadata> ) {
            filter = criteria
        }
        else {
            filter = filterByCriteria(criteria)
        }

		/*
		 * defines the credentials option
		 */
		def opt = TemplateOptions.Builder.overrideLoginCredentials(conf.credentials);
		
		/*
		 * run as 'root' if required
		 */
		opt.runAsRoot(runAsRoot)
		
		def responses = context.getComputeService().runScriptOnNodesMatching(filter, script, opt)

        logExecResponse(script, responses)

		return checkForValidResponse(responses)
	}

    def boolean runStatementOnNodes( Statement statement, def criteria = null, boolean runAsRoot = false ) {

        def filter
        if( criteria == null ) {
            filter = filterAll()
        }
        else if( criteria instanceof Predicate<NodeMetadata> ) {
            filter = criteria
        }
        else {
            filter = filterByCriteria(criteria)
        }

        /*
           * defines the credentials option
           */
        def opt = TemplateOptions.Builder.overrideLoginCredentials(conf.credentials);

        /*
         * run as 'root' if required
         */
        opt.runAsRoot(runAsRoot)

        def responses = context.getComputeService().runScriptOnNodesMatching(filter, statement, opt)

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
     * Synchronize the access to the 'allNodes' map
     */
    synchronized def getAllNodes() { allNodes }

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
	synchronized def Set<? extends BlowNodeMetadata> listNodes() {

        def result =  new LinkedHashSet<BlowNodeMetadata>(allNodes.size())
        allNodes.each {
            if( it.value ) { result.add(it.value)  }
        }

        result
	}

    synchronized def Set<? extends BlowNodeMetadata> listNodes( def criteria )  {
        assert criteria

        def result = new LinkedHashSet<BlowNodeMetadata>()
        def nodeList = findNodeIDs(criteria)

        allNodes?.values(). each { BlowNodeMetadata node ->
            if( node?.getId() in nodeList ) {
                result.add(node)
            }
        }

        return result
    }


    def Collection<String> listNodesNames( ) {
        listNodes().collect { BlowNodeMetadata node ->
            node.getNodeName()
        }
    }
	
	def close() {
        log.trace('Closing session')
		if( contextCreated ) context?.close()
		if( scpExecutor ) scpExecutor.shutdown()
        if( refreshTimer ) refreshTimer.cancel()
	}
	
	
	public String getConfString() {

        def defConfigObj = new BlowConfig();
        def nesting = 0
		def result = new StringBuilder()
        result <<= "cluster: $clusterName { \n"
        nesting += 2

        def props = new ArrayList(conf.metaPropertyValues)
        def excludes = ['class','metaClass','operations', 'credentials', 'size', 'defaultKeyFile']
        def map = [:]
        def maxlen = 0
        props.each { PropertyValue it ->

            // skip unwanted properties
            if( it.name in excludes ) return

            // skip that values which does not change respect teh default obj
            if( defConfigObj[it.name] == it.value ) return

            def val = it.value
            if( val == null ) {
                val = '--'
            } else if( val instanceof CharSequence || val instanceof File )  {
                val = "'$val'"
            }

            map[ it.name ] = val
            if( it.name.length()>maxlen ) maxlen = it.name.length();
        }

        // print the props
        map.keySet().sort().each { String name ->

            result <<= "".padLeft(nesting)
            result <<= "${name.padRight(maxlen)} ${map[name]}\n"
        }


        if( conf.operations?.size() >= 1 ) {
            result <<= "".padLeft(nesting)
            result <<= "operations {"
            nesting += 2
            conf.operations.each {
                result <<= "\n  "
                result <<= "".padLeft(nesting)
                result <<= OperationHelper.opToString(it)
            }
            nesting -= 2
            result <<= "\n"
            result <<= "".padLeft(nesting) << "}"
        }

        result <<= "\n}"
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
	protected void copyToNode( def payload, String targetPath, BlowNodeMetadata targetNode ) {
		assert targetNode
		assert targetPath 
		
		log.debug("[scp] uploading to path: ${targetNode.getNodeName()}:${targetPath}")

		def ip = targetNode.getNodeIp()
		log.debug("[scp] connecting host: '${ip}'")
		def scp = new ScpClient()
		
		scp.connect( ip, conf.userName, conf.privateKey )
		try {
			def result
			if( payload instanceof File ) {
				scp.uploadFile( payload, targetPath )
			}
			else if( payload instanceof String ) {
				scp.uploadString( payload, targetPath )
			}
			else {
				throw new RuntimeException("[scp] unsupported payload type [${payload.getClass().getName()}]")
			}
			
		} finally {
			scp.close()
		}
	}
	
	
	public boolean copyToNodes( def payload, String targetPath, def criteria = null ) {

		def nodes = criteria ? listNodes(criteria) : this.listNodes()
		
		/* 
		 * create a list with an upload job for each node 		
		 */
		def tasks = new ArrayList( nodes.size() )
		
		nodes.each { BlowNodeMetadata node ->
			
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
    def List<String> findMatchingAttributes( def criteria = null, def defAttribute = 'nodeName' ) {

        /*
        * Find all available IP addresses
        */
        def result
        if( !criteria ) {
            result = listNodes() .collect { BlowNodeMetadata node  ->
                node[defAttribute]
            }
        }

        else {
            result = listNodes() .collect { BlowNodeMetadata node ->
                def entries = []

                if( node.getNodeName()?.startsWith(criteria) ) {
                    entries.add(node.getNodeName())
                }

                if( node.getNodeIp()?.startsWith(criteria) ) {
                    entries.add(node.getNodeIp())
                }

                if( node.getProviderId()?.startsWith(criteria) ) {
                    entries?.add(node.getProviderId())
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

        def list = listNodes().findAll() { BlowNodeMetadata node ->

            return  node.getNodeName() == value \
                    || node.getNodeIp() == value \
                    || node.getHostname() == value \
                    || node.getProviderId() == value

        }

        if( list?.size() > 1 ) {
            log.warn "The specified attribute '$value' cannot identify uniquely a node"
            return null
        }

        return list?.size() > 0 ? list.find() : null

    }


    protected String getNextNodeName(String role, Integer count=null) {
        assert role

        if( count == null ) {
            count = nodeRolesMap.get(role).size()
        }

        def num = conf.instanceNumFor(role)
        if( num <= 1 ) {
            return role
        }

        def len = num.toString().length()

        return role + (count+1).toString().padLeft(len,'0')
    }


    /**
     * @return The first available deviceName
     */
    protected String getNextDevice( ) {

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
    protected boolean markDevice( String device ) {

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

    private void deleteDefaultSecurityGroup() {
        def securityToClear = "jclouds#${clusterName}#${conf.regionId}"
        log.debug "Clearing security group: '${securityToClear}'"
        try {
            securityGroupClient.deleteSecurityGroupInRegion( conf.regionId, securityToClear )
        }
        catch( Exception e ) {
            log.warn("Error delerting security group: '$securityToClear'", e)
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

    /**
     * Define the file where read/store the session object
     */
    def private static File sessionFile( String clusterName ) {
        def home = new File(System.properties['user.home'], '.blow')
        new File( home, "blow_session.${clusterName}")
    }

    @Lazy
    transient private static XStream xStream = {
        def result = new XStream(new StaxDriver())
        result.registerConverter( new HashBiMapConverter(result.getMapper()) )
        result.registerConverter( new ArrayListMultimapConverter(result.getMapper()) )
        result.alias( "session", BlowSession.class )
        result.alias( "hash-bimap", HashBiMap.class )
        result.alias( "arraylist-multimap", ArrayListMultimap.class )
        result
    } ()

    /**
     * Save the current session to a file
     *
     * @return The file where the session has been persisted, of {@code null} in it cannot stored
     */
    def File persist(def target) {
        assert clusterName, "Missing 'clusterName', cannot save session"
        def file;

        /*
         * detect where save the session
         */
        if( target == null ) {
            file = sessionFile(clusterName)
        }
        else if( target instanceof File ) {
            file = target
        }
        else if( target instanceof CharSequence ) {
            file = new File( target.toString() )
        }
        else {
            throw new BlowException("Invalid save session target class: ${target.getClass()}")
        }


        /*
         * serialize and save the session
         */
        try {
            FileWriter writer = new FileWriter(file)
            xStream.marshal(this, new PrettyPrintWriter(writer))
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
            return (BlowSession)xStream.fromXML(file)
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
    def static boolean deleteSessionFile( String clusterName ) {
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
    def boolean deleteSessionFile() {
        clusterName ? deleteSessionFile(clusterName) : false
    }

    def with( NodeMetadata node, Closure closure ) {
        closure.setDelegate(new BlowNodeMetadata(node))
        closure.call()
    }



    public class BlowNodeMetadata implements NodeMetadata {

        BlowNodeMetadata(NodeMetadata node) {
            this.node = node
        }

        @Delegate(interfaces=false)
        NodeMetadata node

        def String getNodeName( ) {
            nodeNamesMap.inverse().get( this.getId() )
        }

        def String getNodeIp( ) {
            getPublicAddresses() ?. find()
        }

        def String getNodeRole() {
            def entry = nodeRolesMap.entries().find { entry -> node.getId() == entry.value }
            return entry?.key
        }

        @Override
        public ComputeType getType() {
            node.getType()
        }

        @Override
        String getAdminPassword() {
            node.getAdminPassword()
        }
    }
}

