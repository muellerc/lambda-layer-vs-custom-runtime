package example;

import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.CfnOutputProps;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigatewayv2.AddRoutesOptions;
import software.amazon.awscdk.services.apigatewayv2.HttpApi;
import software.amazon.awscdk.services.apigatewayv2.HttpApiProps;
import software.amazon.awscdk.services.apigatewayv2.HttpMethod;
import software.amazon.awscdk.services.apigatewayv2.PayloadFormatVersion;
import software.amazon.awscdk.services.apigatewayv2.integrations.LambdaProxyIntegration;
import software.amazon.awscdk.services.apigatewayv2.integrations.LambdaProxyIntegrationProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.dynamodb.TableProps;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.logs.RetentionDays;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonList;

public class InfrastructureStack extends Stack {
    public InfrastructureStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfrastructureStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        Table exampleTable = new Table(this, "ExampleTable", TableProps.builder()
                .partitionKey(Attribute.builder()
                        .type(AttributeType.STRING)
                        .name("id").build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build());

        LayerVersion jre17Layer = new LayerVersion(this, "JRE17Layer", LayerVersionProps.builder()
                .layerVersionName("JRE17Layer")
                .description("JRE 17")
                .compatibleArchitectures(singletonList(Architecture.X86_64))
                .compatibleRuntimes(Arrays.asList(Runtime.PROVIDED_AL2, Runtime.JAVA_11))
                .code(Code.fromAsset("../jre-17-layer.zip"))
                .build());

        Function functionWithLayerAndProvidedRuntime = new Function(this, "functionWithLayerAndProvidedRuntime", FunctionProps.builder()
                .functionName("function-with-layer-and-provided-runtime")
                .description("function-with-layer-and-java-11-runtime")
                .architecture(Architecture.X86_64)
                .layers(singletonList(jre17Layer))
                .handler("example.ExampleDynamoDbHandler::handleRequest")
                .runtime(Runtime.PROVIDED_AL2)
                .code(Code.fromAsset("../software/example-function/target/function.jar"))
                .memorySize(512)
                .environment(mapOf(
                        "TABLE_NAME", exampleTable.getTableName()))
                .timeout(Duration.seconds(20))
                .logRetention(RetentionDays.ONE_WEEK)
                .build());

        // kind of hack to leverage the init boost
        Function functionWithLayerAndJava11Runtime = new Function(this, "functionWithLayerAndJava11Runtime", FunctionProps.builder()
                .functionName("function-with-layer-and-java-11-runtime")
                .description("function-with-layer-and-java-11-runtime")
                .architecture(Architecture.X86_64)
                .layers(singletonList(jre17Layer))
                .handler("example.ExampleDynamoDbHandler::handleRequest")
                .runtime(Runtime.JAVA_11)
                .code(Code.fromAsset("../software/example-function/target/function.jar"))
                .memorySize(512)
                .environment(mapOf(
                        "TABLE_NAME", exampleTable.getTableName(),
                        "AWS_LAMBDA_EXEC_WRAPPER", "/opt/bootstrap"))
                .timeout(Duration.seconds(20))
                .logRetention(RetentionDays.ONE_WEEK)
                .build());

        Function functionWithCustomRuntime = new Function(this, "functionWithCustomRuntime", FunctionProps.builder()
                .functionName("function-with-custom-runtime")
                .description("function-with-custom-runtime")
                .handler("example.ExampleDynamoDbHandler::handleRequest")
                .runtime(Runtime.PROVIDED_AL2)
                .architecture(Architecture.X86_64)
                .code(Code.fromAsset("../jre-17-custom-runtime.zip"))
                .memorySize(512)
                .environment(mapOf(
                        "TABLE_NAME", exampleTable.getTableName()))
                .timeout(Duration.seconds(20))
                .logRetention(RetentionDays.ONE_WEEK)
                .build());

        exampleTable.grantWriteData(functionWithLayerAndProvidedRuntime);
        exampleTable.grantWriteData(functionWithLayerAndJava11Runtime);
        exampleTable.grantWriteData(functionWithCustomRuntime);

        HttpApi httpApi = new HttpApi(this, "LambdaLayerVsCustomRuntimeApi", HttpApiProps.builder()
                .apiName("LambdaLayerVsCustomRuntimeApi")
                .build());

        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/layer-custom-runtime")
                .methods(singletonList(HttpMethod.GET))
                .integration(new LambdaProxyIntegration(LambdaProxyIntegrationProps.builder()
                        .handler(functionWithLayerAndProvidedRuntime)
                        .payloadFormatVersion(PayloadFormatVersion.VERSION_2_0)
                        .build()))
                .build());

        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/layer-provided-runtime")
                .methods(singletonList(HttpMethod.GET))
                .integration(new LambdaProxyIntegration(LambdaProxyIntegrationProps.builder()
                        .handler(functionWithLayerAndJava11Runtime)
                        .payloadFormatVersion(PayloadFormatVersion.VERSION_2_0)
                        .build()))
                .build());

        httpApi.addRoutes(AddRoutesOptions.builder()
                .path("/custom-runtime")
                .methods(singletonList(HttpMethod.GET))
                .integration(new LambdaProxyIntegration(LambdaProxyIntegrationProps.builder()
                        .handler(functionWithCustomRuntime)
                        .payloadFormatVersion(PayloadFormatVersion.VERSION_2_0)
                        .build()))
                .build());

        new CfnOutput(this, "api-endpoint", CfnOutputProps.builder()
                .value(httpApi.getApiEndpoint())
                .build());
    }

    private Map<String, String> mapOf(String... keyValues) {
        Map<String, String> map = new HashMap<>(keyValues.length/2);
        for (int i = 0; i < keyValues.length; i++) {
            if(i % 2 == 0) {
                map.put(keyValues[i], keyValues[i + 1]);
            }
        }
        return map;
    }
}
