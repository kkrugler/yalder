package org.krugler.yalder;

import org.apache.flink.api.java.ExecutionEnvironment;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

public class AnalyzeLeipzigTool {

    public static void main(String[] args) {
        AnalyzeLeipzigOptions options = new AnalyzeLeipzigOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }
        
        try {
            ExecutionEnvironment env = ExecutionEnvironment.getExecutionEnvironment();
            AnalyzeLeipzigWorkflow.createWorkflow(env, options);
            env.execute();
        } catch (Throwable t) {
            System.err.println("Exception running tool: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }


}
