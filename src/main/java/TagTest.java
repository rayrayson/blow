

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import org.jclouds.Constants;
import org.jclouds.aws.ec2.compute.AWSEC2TemplateOptions;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.Template;
import org.jclouds.compute.domain.TemplateBuilder;
import org.jclouds.compute.reference.ComputeServiceConstants;
import org.jclouds.enterprise.config.EnterpriseConfigurationModule;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.sshj.config.SshjSshClientModule;

import java.util.Properties;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class TagTest {

    public static final String accessKey = "AKIAIF3TUNLSJYG5UWQQ";
    public static final String secretKey = "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";
    public static final String regionId = "eu-west-1"; // <-- EU - IRELAND
    public static final String zoneId = "eu-west-1a";

    public static final String instanceType = "t1.micro";
    public static final String imageId = "ami-6d555119";

    public static final String keyPair = "eurokey";

    public static final String clusterName = "testcase";



    private static ComputeServiceContext context;
    private static ComputeService compute;


    public static void main(String[] args) throws RunNodesException {
        System.out.print("Starting .. ");

        Properties props = new Properties();
        props.setProperty("jclouds.ec2.ami-query", "");
        props.setProperty("jclouds.ec2.cc-ami-query", "");
        props.setProperty("jclouds.regions", regionId);

        //props.setProperty( Constants.PROPERTY_USER_THREADS, "25" )
        props.setProperty(Constants.PROPERTY_MAX_RETRIES, "7");
        props.setProperty(Constants.PROPERTY_RETRY_DELAY_START, "500");
        props.setProperty(ComputeServiceConstants.PROPERTY_TIMEOUT_NODE_RUNNING, 5 * 60 * 1000 + "") ;
        props.setProperty(ComputeServiceConstants.PROPERTY_TIMEOUT_PORT_OPEN, 5 * 60 * 1000 + "");
        props.setProperty(ComputeServiceConstants.PROPERTY_TIMEOUT_SCRIPT_COMPLETE, 5 * 60 * 1000 + "");


        context = new ComputeServiceContextFactory()
                .createContext("aws-ec2",
                        accessKey,
                        secretKey,
                        ImmutableSet.<Module>of(new SLF4JLoggingModule(), new SshjSshClientModule(), new EnterpriseConfigurationModule()),
                        props
                );

        try {


            compute = context.getComputeService();

            TemplateBuilder template = compute.templateBuilder();

            template.hardwareId( instanceType );
            template.imageId( regionId + "/" + imageId );
            template.locationId( zoneId );

            System.out.println("Launching node master .. ");
            launchNodes(template, 1, "master");

            System.out.println("Launching node worker.. ");
            launchNodes(template, 1, "worker");

        }
        finally{

            context.close();
        }


        System.out.println("DONE");

    }

    static void launchNodes(TemplateBuilder builder, int numberOfNodes, String role) throws RunNodesException {

        Template template = builder.build();
        template.getOptions().userMetadata("Role", role);

        template.getOptions().as(AWSEC2TemplateOptions.class).keyPair(keyPair);

        compute.createNodesInGroup(clusterName, numberOfNodes, template);
    }
}
