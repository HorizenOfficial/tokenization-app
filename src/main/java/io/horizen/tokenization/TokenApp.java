package io.horizen.tokenization;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.horizen.SidechainApp;

import java.io.File;

// Tokenization application starting point.
// Application expect to be executed with a single argument - path to configuration file
// This app has some custom settngs other than the default ones. See the readme file for further info.
public class TokenApp {
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please provide settings file name as first parameter!");
            return;
        }

        if (!new File(args[0]).exists()) {
            System.out.println("File on path " + args[0] + " doesn't exist");
            return;
        }

        String settingsFileName = args[0];

        // To Initialize the core starting point - SidechainApp, Guice DI is used.
        // Note: it's possible to initialize SidechainApp both using Guice DI or directly by emitting the constructor.
        Injector injector = Guice.createInjector(new TokenAppModule(settingsFileName));
        SidechainApp sidechainApp = injector.getInstance(SidechainApp.class);

        // Start the sidechain node.
        sidechainApp.run();
        System.out.println("Tokenization sidechain application successfully started...");
    }
}
