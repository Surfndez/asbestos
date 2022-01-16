package gov.nist.asbestos.debug.testScript.requests;

import gov.nist.asbestos.client.Base.Request;
import gov.nist.asbestos.client.client.FhirClient;
import gov.nist.asbestos.client.client.Format;
import gov.nist.asbestos.client.log.SimStore;
import gov.nist.asbestos.serviceproperties.ServiceProperties;
import gov.nist.asbestos.serviceproperties.ServicePropertiesEnum;
import gov.nist.asbestos.client.channel.ChannelConfig;
import gov.nist.asbestos.client.debug.StopDebugTestScriptException;
import gov.nist.asbestos.client.debug.TestScriptDebugState;
import gov.nist.asbestos.simapi.simCommon.SimId;
import gov.nist.asbestos.simapi.validation.Val;
import gov.nist.asbestos.testEngine.engine.ModularEngine;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.hl7.fhir.r4.model.TestReport;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
// 0 - "debug-testscript"
// 1 - channelName (testSession__channelId)
// 2 - testCollectionId
// 3 - testId
// Returns modular test reports
//   JSON object : test/moduleId => TestReport

public class DebugTestScriptRequest implements Runnable {
    private static Logger log = Logger.getLogger(DebugTestScriptRequest.class.getName());

    private Request request;
    private TestScriptDebugState state;

    public static boolean isRequest(Request request) {
        return request.uriParts.size() == 4 && request.uriParts.get(0).equals("debug-testscript");
    }

    public DebugTestScriptRequest(Request request, TestScriptDebugState state) {
        this.request = request;
        this.state = state;
    }

    public TestScriptDebugState getState() {
        return state;
    }

    @Override
    public void run() {
        log.info("Run DebugTestScriptRequest");
        String channelId = request.uriParts.get(1);
        String testCollection = request.uriParts.get(2);
        String testName = request.uriParts.get(3);

        ChannelConfig channelConfig = null;
        try {

            SimId simId = SimId.buildFromRawId(channelId);
            SimStore simStore = new SimStore(request.externalCache, simId);
            if (simStore.exists()) {
                simStore.open();
                channelConfig = simStore.getChannelConfig();
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        if (channelConfig == null) {
            throw new RuntimeException("channelConfig is null for " + channelId);
        }

        String testSession = channelConfig.getTestSession();
        String proxyStr;
        ServicePropertiesEnum key = ServicePropertiesEnum.FHIR_TOOLKIT_BASE;
        proxyStr = ServiceProperties.getInstance().getPropertyOrStop(key);
        proxyStr += "/proxy/" + channelId;
        URI proxy;
        try {
            proxy = new URI(proxyStr);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        File testDir = request.ec.getTest(testCollection, testName);

        File patientCacheDir = request.ec.getTestLogCacheDir(channelId);
        File alternatePatientCacheDir = request.ec.getTestLogCacheDir("default__default");
        patientCacheDir.mkdirs();
        alternatePatientCacheDir.mkdirs();

        FhirClient fhirClient = new FhirClient()
                .setFormat(request.isJson ? Format.JSON : Format.XML)
                .sendGzip(request.isGzip)
                .requestGzip(request.isGzip);
        TestReport report;
        ModularEngine modularEngine = null;
        try {
            modularEngine = new ModularEngine(testDir, proxy, state);
            modularEngine
                    //.getLastTestEngine()
                    .setSaveLogs(true)
                    .setTestSession(testSession)
                    .setChannelId(channelId)
                    .setExternalCache(request.externalCache)
                    .setVal(new Val())
                    .setFhirClient(fhirClient)
                    .setTestCollection(testCollection)
                    .addCache(patientCacheDir)
                    .addCache(alternatePatientCacheDir)
                    .setModularScripts()
                    .runTest();
        }
        catch (StopDebugTestScriptException sdex) {
            log.info("caught StopDebug...");
        }
        catch (Throwable t) {
            log.log(Level.SEVERE, "DebugTestScriptRequest#run exception", t);
            throw t;
        } finally {
            if (modularEngine != null && state != null) {
                String json = modularEngine.reportsAsJson();
                String testReport = ((json != null) ? json : "{}");
                state.sendFinalReport(testReport);
            }
        }

    }
}
