package org.jenkinsci.plugins.serviceFabric.ServiceFabricCommands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class SFCommandBuilder {
	private static final Logger logger = Logger.getLogger(SFCommandBuilder.class.getName());

    // declare the command templates
    private String sfConnect = "sfctl cluster select --endpoint http://{clusterIP}:19080";
    private String sfSecureConnect = "sfctl cluster select --endpoint http://{clusterIP}:19080 --key {clientKey} --cert {clientCert} --ca-chain {caChain} --no-verify";
    private String sfCopy = "sfctl application upload --path {appName} --show-progress";
    private String sfRegisterType = "sfctl application provision --application-type-build-path {appName}";
    private String sfApplicationCreate = "sfctl application create --app-name {appName} --app-type {appType} --app-version {appVersion}";
    private String sfApplicationUpgrade = "sfctl application upgrade --app-id {appId} --app-version {appVersion} --mode Monitored";
    private String sfApplicationRemove = "sfctl application delete --application-id {appId}";
    private String sfApplicationUnregister = "sfctl application unprovision --application-type-name {appType} --application-type-version {appVersion}";


    private String appName; 
    private String appType; 
    private String clusterIP;
    private String manifestPath;
    private String projectName;
    private String clientKey;
    private String clientCert;
    private String caChain;

    // constructor for the class
    public SFCommandBuilder(String name, String type, String cIP, String mPath, String cKey, String cCert, String cChain, String pName){

        // set the values
        this.appName = name;
        this.appType = type;
        this.clusterIP = cIP;
        this.manifestPath = mPath;
        this.projectName = pName;
        this.clientKey = cKey;
        this.clientCert = cCert;
        this.caChain = cChain;
    }

    // makes an API request to check if the application type and version are already registered
    // input: targetApplicationType is the application type of the project being built, version is the desired version
    // output: true if the type exists, false otherwise
    private String checkApplicationTypeExists(String targetApplicationType, String version){

        // build the URL and make the request
        String requestURL = "http://" + clusterIP + ":19080/ApplicationTypes/" + targetApplicationType + "?api-version=1.0";

        String searchString = "\"Name\":\"" + targetApplicationType + "\",\"Version\":\"" + version + "\""; 
        String response;

        try{

			BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(requestURL).openStream()));
            while((response = reader.readLine()) != null)
            {
                // if the target application type/version combination already exists, then we should do a clean deploy
                if(response.contains(searchString)){
                    return "cleanDeploy";
                }

                // if the desired version doesn't exist but the app type does, then we are doing an upgrade
                else if(response.contains(targetApplicationType)){
                    return "upgrade";
                }
            }
        }catch(Exception e){
        	logger.info("Exception occurred while checking applicationType. Better to do cleanDeploy.");
            return "cleanDeploy";
        }

        // if the type and version aren't found, then just do a regular deploy
        return "deploy";
    }

    // parses the ApplicationManifest file to see what version the application should be
    private String checkApplicationManifestVersion(String pName, String filePath) /*throws SAXException, IOException, ParserConfigurationException*/{

        String version = null, newFilePath = System.getenv("JENKINS_HOME") + "/workspace/" + pName + "/" + filePath;        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = null;
        Document applicationManifest = null;
        try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			logger.log(Level.SEVERE, "ParserConfigurationException:" + e.getStackTrace());
			throw new RuntimeException(e.getMessage());
		}
        try {
			applicationManifest = builder.parse(new File(newFilePath));
		} catch (SAXException e) {
			logger.log(Level.SEVERE, "SAXException:" + e.getStackTrace());
			throw new RuntimeException(e.getMessage());
		} catch (IOException e) {
			logger.log(Level.SEVERE, "IOException:" + e.getStackTrace());
			throw new RuntimeException(e.getMessage());
		}
        version = applicationManifest.getDocumentElement().getAttribute("ApplicationTypeVersion");
        
        return version;

    }

    // build and return the output command
    public String buildCommands(){

        String appId = getAppIdFromName(appName);
        // check to see the version of the application being deployed and whether or not it exists
        String version = checkApplicationManifestVersion(projectName, manifestPath);
        String deploymentMode = checkApplicationTypeExists(appType, version);

        // start building the command. note that since Jenkins resets its location after each command, 
        // we put everything into one big command in order to avoid having to move locations over and over
        String outputCommand = "";

        // start by connecting to the cluster -- see if this is a secure cluster or not
        if(clientKey.isEmpty()){

            outputCommand = sfConnect.replace("{clusterIP}", clusterIP);
        }

        // if this is a secure cluster, then we use the secure command.
        else{

            outputCommand += sfSecureConnect.replace("{clusterIP}", clusterIP)
                                            .replace("{clientKey}", clientKey)
                                            .replace("{clientCert}", clientCert)
                                            .replace("{caChain}", caChain);
        }
        

        // if doing a clean deploy, then we have to first remove and unregister the app/app type
        if (deploymentMode.equals("cleanDeploy")){
            outputCommand += " && " + sfApplicationRemove.replace("{appId}", appId);
            outputCommand += " ; " + sfApplicationUnregister.replace("{appType}", appType).replace("{appVersion}", version);
        }
        logger.info("Till this point outputcommand:"+ outputCommand + "\nappname:"+appName);
        
        // make the command: move into the application package folder
        // Getting application path from the appilcation-manifest path input in Jenkins portal
        String tmpString = manifestPath.substring(0, manifestPath.lastIndexOf('/',manifestPath.length()-1));
        String applicationPath = tmpString.substring(0, tmpString.lastIndexOf('/',tmpString.length()-1));
        outputCommand += "&& cd " + applicationPath;

        // // add on the different commands: copy -> register -> create
        outputCommand += " && " + (sfCopy.replace("{appName}", appName.replace("fabric:/", "")));
        outputCommand += " && " + (sfRegisterType.replace("{appName}", appName.replace("fabric:/", "")));

        // if it's not an upgrade, we'll do an application create
        if(!deploymentMode.equals("upgrade")){
            outputCommand += " && " + (sfApplicationCreate.replace("{appName}", appName).replace("{appType}", appType).replace("{appVersion}", version));           
        }

        // if it is an upgrade, then we'll start the upgrade
        else{
            outputCommand += " && " + (sfApplicationUpgrade.replace("{appId}", appId).replace("{appVersion}", version));
        }
        logger.info("Command to be run:"+ outputCommand);
        return outputCommand;
    }

    private String getAppIdFromName(String appName) {
        return appName.substring(appName.indexOf(":/")+2);
    }



}
