package com.pm.stack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
// AWS CDK imports
import software.amazon.awscdk.App;
import software.amazon.awscdk.AppProps;
import software.amazon.awscdk.BootstraplessSynthesizer;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Token;
// EC2 (VPC) imports
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.InstanceClass;
import software.amazon.awscdk.services.ec2.InstanceSize;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ec2.Vpc;
// ECS (Fargate) imports
import software.amazon.awscdk.services.ecs.AwsLogDriverProps;
import software.amazon.awscdk.services.ecs.CloudMapNamespaceOptions;
import software.amazon.awscdk.services.ecs.Cluster;
import software.amazon.awscdk.services.ecs.ContainerDefinitionOptions;
import software.amazon.awscdk.services.ecs.ContainerImage;
import software.amazon.awscdk.services.ecs.FargateService;
import software.amazon.awscdk.services.ecs.FargateTaskDefinition;
import software.amazon.awscdk.services.ecs.LogDriver;
import software.amazon.awscdk.services.ecs.PortMapping;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
// Logging imports
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
// MSK imports
import software.amazon.awscdk.services.msk.CfnCluster;
// RDS imports
import software.amazon.awscdk.services.rds.Credentials;
import software.amazon.awscdk.services.rds.DatabaseInstance;
import software.amazon.awscdk.services.rds.DatabaseInstanceEngine;
import software.amazon.awscdk.services.rds.PostgresEngineVersion;
import software.amazon.awscdk.services.rds.PostgresInstanceEngineProps;
// Route53 imports
import software.amazon.awscdk.services.route53.CfnHealthCheck;

/**
 * Defines an AWS CDK Stack for a microservices architecture,
 * likely intended for local development/testing with LocalStack.
 */
public class LocalStack extends Stack {
    // VPC and ECS Cluster are stored as class fields to be accessible by multiple methods
    private final Vpc vpc;
    private final Cluster ecsCluster;

    /**
     * Constructor for the LocalStack.
     * @param scope The scope in which to define this construct (typically the App).
     * @param id The logical ID of this construct.
     * @param props The stack properties.
     */
    public LocalStack(final App scope, final String id, final StackProps props){
        super(scope, id, props);

        // 1. Create the foundational Virtual Private Cloud (VPC)
        this.vpc = createVpc();

        // 2. Create two PostgreSQL RDS database instances
        DatabaseInstance authServiceDb =
                createDatabase("AuthServiceDB", "auth-service-db");

        DatabaseInstance patientServiceDb =
                createDatabase("PatientServiceDB", "patient-service-db");

        // 3. Create Route 53 Health Checks for the database endpoints
        // These checks ensure the database is up and accessible.
        CfnHealthCheck authDbHealthCheck =
                createDbHealthCheck(authServiceDb, "AuthServiceDBHealthCheck");

        CfnHealthCheck patientDbHealthCheck =
                createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");

        // 4. Create the Amazon MSK (Kafka) Cluster
        CfnCluster mskCluster = createMskCluster();

        // 5. Create the Amazon ECS Cluster
        this.ecsCluster = createEcsCluster();

        // 6. Deploy the AuthService microservice (ECS Fargate Service)
        FargateService authService =
                createFargateService("AuthService",
                        "auth-service", // Container image name
                        List.of(4005), // Exposed port
                        authServiceDb, // Associated database
                        Map.of("JWT_SECRET", "tWM+heS1tztIo0LitAhI0pAJVmutCKZATsNQGg8PyRc=")); // Custom environment variables

        // Explicitly set dependencies to ensure the DB and Health Check are ready before the service starts
        authService.getNode().addDependency(authDbHealthCheck);
        authService.getNode().addDependency(authServiceDb);

        // 7. Deploy the BillingService microservice (ECS Fargate Service)
        FargateService billingService =
                createFargateService("BillingService",
                        "billing-service",
                        List.of(4002,9002), // HTTP and gRPC ports
                        null, // No associated database
                        null);

        // 8. Deploy the AnalyticsService microservice (ECS Fargate Service)
        FargateService analyticsService =
                createFargateService("AnalyticsService",
                        "analytics-service",
                        List.of(4003),
                        null,
                        null);

        // Dependency on MSK cluster, as this service likely consumes Kafka messages
        analyticsService.getNode().addDependency(mskCluster);

        // 9. Deploy the PatientService microservice (ECS Fargate Service)
        FargateService patientService = createFargateService("PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb, // Associated database
                Map.of(
                        // Configuration for communication with the Billing Service (likely for local testing)
                        "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                        "BILLING_SERVICE_GRPC_PORT", "9002"
                ));
        // Explicitly set dependencies for PatientService
        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientDbHealthCheck);
        patientService.getNode().addDependency(billingService); // Depends on BillingService being available
        patientService.getNode().addDependency(mskCluster); // Depends on Kafka

        // 10. Deploy the API Gateway Service (with Load Balancer)
        createApiGatewayService();
    }

    /**
     * Creates the core VPC for the application.
     * @return The created Vpc construct.
     */
    private Vpc createVpc(){
        return Vpc.Builder
                .create(this, "PatientManagementVPC")
                .vpcName("PatientManagementVPC")
                .maxAzs(2) // Deploy resources across 2 Availability Zones
                .build();
    }

    /**
     * Creates a PostgreSQL Database Instance (RDS).
     * @param id The logical ID for the database construct.
     * @param dbName The database name within the instance.
     * @return The created DatabaseInstance construct.
     */
    private DatabaseInstance createDatabase(String id, String dbName){
        return DatabaseInstance.Builder
                .create(this, id)
                .engine(DatabaseInstanceEngine.postgres(
                        PostgresInstanceEngineProps.builder()
                                .version(PostgresEngineVersion.VER_17_2) // Specify PostgreSQL version
                                .build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO)) // Cost-effective instance type
                .allocatedStorage(20) // 20 GiB storage
                .credentials(Credentials.fromGeneratedSecret("admin_user")) // Credentials stored in a generated Secrets Manager secret
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY) // Crucial for dev stacks: DB will be deleted when stack is destroyed
                .build();
    }

    /**
     * Creates a Route 53 Health Check for a database endpoint.
     * @param db The DatabaseInstance to check.
     * @param id The logical ID for the health check construct.
     * @return The created CfnHealthCheck construct.
     */
    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id){
        return CfnHealthCheck.Builder.create(this, id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP") // Check TCP connection
                        .port(Token.asNumber(db.getDbInstanceEndpointPort())) // Use the DB's port
                        .ipAddress(db.getDbInstanceEndpointAddress()) // Use the DB's endpoint address
                        .requestInterval(30) // Check every 30 seconds
                        .failureThreshold(3) // 3 consecutive failures to mark unhealthy
                        .build())
                .build();
    }

    /**
     * Creates an Amazon Managed Streaming for Kafka (MSK) Cluster.
     * @return The created CfnCluster construct.
     */
    private CfnCluster createMskCluster(){
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("kafa-cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(1) // Single-broker node (suitable for dev/test)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                        .instanceType("kafka.m5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList())) // Place broker in private subnets
                        .brokerAzDistribution("DEFAULT")
                        .build())
                .build();
    }

    /**
     * Creates the Amazon ECS Cluster where Fargate Services will run.
     * @return The created Cluster construct.
     */
    private Cluster createEcsCluster(){
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder()
                        .name("patient-management.local") // Configure Cloud Map for service discovery
                        .build())
                .build();
    }

    /**
     * Creates an ECS Fargate Service for a microservice application.
     * @param id The logical ID for the service construct.
     * @param imageName The name of the container image (used as service name/prefix).
     * @param ports List of container ports to map.
     * @param db Optional DatabaseInstance to configure connection details.
     * @param additionalEnvVars Optional map of extra environment variables.
     * @return The created FargateService construct.
     */
    private FargateService createFargateService(String id,
                                                String imageName,
                                                List<Integer> ports,
                                                DatabaseInstance db,
                                                Map<String, String> additionalEnvVars) {

        // Define the Task (CPU/Memory resource allocation)
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, id + "Task")
                        .cpu(256) // 0.25 vCPU
                        .memoryLimitMiB(512) // 512 MiB memory
                        .build();

        // Builder for the Container Definition
        ContainerDefinitionOptions.Builder containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry(imageName)) // Container image from registry
                        .portMappings(ports.stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList()) // Define port mappings
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id + "LogGroup")
                                        .logGroupName("/ecs/" + imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY) // Log retention set to 1 day
                                        .build())
                                .streamPrefix(imageName)
                                .build()));

        // --- Environment Variables Configuration ---
        Map<String, String> envVars = new HashMap<>();
        // Set Kafka connection (using LocalStack's specific local DNS for development/testing)
        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512");

        // Add custom environment variables if provided
        if(additionalEnvVars != null){
            envVars.putAll(additionalEnvVars);
        }

        // Configure database connection variables if a DB is associated with this service
        if(db != null){
            envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(
                    db.getDbInstanceEndpointAddress(), // DB endpoint
                    db.getDbInstanceEndpointPort(),    // DB port
                    imageName                          // DB name suffix
            ));
            envVars.put("SPRING_DATASOURCE_USERNAME", "admin_user");
            // Retrieve password securely from the generated secret
            envVars.put("SPRING_DATASOURCE_PASSWORD",
                    db.getSecret().secretValueFromJson("password").toString());
            // JPA/Hibernate settings for schema management
            envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
            envVars.put("SPRING_SQL_INIT_MODE", "always");
            // Set a long timeout for DB initialization/failure to allow DB to start up
            envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
        }

        // Finalize container definition with environment variables and add it to the task
        containerOptions.environment(envVars);
        taskDefinition.addContainer(imageName + "Container", containerOptions.build());

        // Create the Fargate Service
        return FargateService.Builder.create(this, id)
                .cluster(ecsCluster)
                .taskDefinition(taskDefinition)
                .assignPublicIp(false) // Services run in private subnets/within the VPC
                .serviceName(imageName)
                .build();
    }

    /**
     * Creates the API Gateway service using an Application Load Balanced Fargate Service pattern.
     */
    private void createApiGatewayService() {
        // Define the Task Definition (same CPU/Memory as other services)
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, "APIGatewayTaskDefinition")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        // Define the Container
        ContainerDefinitionOptions containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("api-gateway"))
                        .environment(Map.of(
                                "SPRING_PROFILES_ACTIVE", "prod",
                                // URL for the Auth Service (using host.docker.internal for local resolution)
                                "AUTH_SERVICE_URL", "http://host.docker.internal:4005"
                        ))
                        .portMappings(List.of(4004).stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                        .logGroupName("/ecs/api-gateway")
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix("api-gateway")
                                .build()))
                        .build();

        taskDefinition.addContainer("APIGatewayContainer", containerOptions);

        // Create the Load Balanced Fargate Service
        ApplicationLoadBalancedFargateService apiGateway =
                ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                        .cluster(ecsCluster)
                        .serviceName("api-gateway")
                        .taskDefinition(taskDefinition)
                        .desiredCount(1)
                        .healthCheckGracePeriod(Duration.seconds(60)) // Give the container 60s to start up and pass the load balancer health check
                        .build();
    }

    /**
     * Main method to synthesize the CDK application.
     * @param args Command line arguments.
     */
    public static void main(final String[] args) {
        // Create the CDK App
        App app = new App(AppProps.builder().outdir("./cdk.out").build());

        // Define Stack properties
        StackProps props = StackProps.builder()
                // Use BootstraplessSynthesizer: bypasses standard CDK bootstrap, suitable for local/non-AWS environments like LocalStack
                .synthesizer(new BootstraplessSynthesizer())
                .build();

        // Instantiate the stack
        new LocalStack(app, "localstack", props);

        // Synthesize the stack (generates CloudFormation template)
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}