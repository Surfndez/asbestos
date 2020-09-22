package gov.nist.asbestos.mhd.translation.test;

import gov.nist.asbestos.client.Base.ParserBase;
import gov.nist.asbestos.client.client.FhirClient;
import gov.nist.asbestos.client.events.Event;
import gov.nist.asbestos.client.events.ITask;
import gov.nist.asbestos.client.log.SimStore;
import gov.nist.asbestos.client.resolver.ResourceCacheMgr;
import gov.nist.asbestos.client.resolver.ResourceMgr;
import gov.nist.asbestos.mhd.transactionSupport.AssigningAuthorities;
import gov.nist.asbestos.mhd.transactionSupport.CodeTranslator;
import gov.nist.asbestos.mhd.transactionSupport.CodeTranslatorBuilder;
import gov.nist.asbestos.mhd.transforms.BundleToRegistryObjectList;
import gov.nist.asbestos.client.channel.ChannelConfig;
import gov.nist.asbestos.simapi.simCommon.SimId;
import gov.nist.asbestos.simapi.validation.Val;
import gov.nist.asbestos.simapi.validation.ValErrors;
import gov.nist.asbestos.simapi.validation.ValFactory;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectListType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.xml.bind.JAXBElement;
import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("The Test suffix from the class name can be removed so that it would be ignored until the errors are fixed.")
class BuildRegistryObjectListTest {
    private static Bundle bundle;
    private static Val val;
    private ResourceMgr rMgr;
    private static ResourceMgr bundleMgr;
    private static File externalCache;
    private FhirClient fhirClient;
    private String testSession = "default";
    private String channelId = "test";

    @BeforeAll
    static void beforeAll() throws URISyntaxException {
        externalCache = Paths.get(BuildRegistryObjectListTest.class.getResource("/external_cache/findme.txt").toURI()).getParent().toFile();
        InputStream is = ResourceMgrContainedTest.class.getResourceAsStream("/gov/nist/asbestos/mhd/translation/shared/bundle.xml");
        IBaseResource resource = ParserBase.getFhirContext().newXmlParser().parseResource(is);
        assertTrue(resource instanceof Bundle);
        bundle = (Bundle) resource;
    }

    @BeforeEach
    void beforeEach() {
        ChannelConfig channelConfig = new ChannelConfig()
                .setChannelId("test")
                .setChannelType("mhd")
                .setActorType("fhir")
                .setEnvironment("default")
                .setFhirBase("http://localhost:8877/fhir/fhir")
                .setTestSession("default");

        rMgr = new ResourceMgr();
        val = new Val();
        rMgr.setVal(val);
        fhirClient = new FhirClient();
        fhirClient.setResourceCacheMgr(new ResourceCacheMgr(externalCache));
        rMgr.setFhirClient(fhirClient);
        SimId simId = SimId.buildFromRawId(testSession + "__" + channelId).withActorType("fhir").withEnvironment("default");
        SimStore simStore = new SimStore(externalCache, simId);
        simStore.create(channelConfig);
        simStore.setResource("Bundle");
        Event event = simStore.newEvent();
        ITask task = event.newTask();
        rMgr.setTask(task);
        bundleMgr = new ResourceMgr();
        bundleMgr.setVal(val);
        bundleMgr.setBundle(bundle);
    }

    @Test
    void build() {
        InputStream is2 = this.getClass().getResourceAsStream("/gov/nist/asbestos/mhd/translation/shared/theCodes.xml");
        CodeTranslator codeTranslator = CodeTranslatorBuilder.read(is2);

        rMgr.setBundle(bundle);
        rMgr.getResourceMgrConfig().internalOnly();

        BundleToRegistryObjectList xlate = new BundleToRegistryObjectList(null);
        xlate
                .setCodeTranslator(codeTranslator)
                .setResourceMgr(rMgr)
                .setAssigningAuthorities(AssigningAuthorities.allowAny())
                .setVal(val);

        RegistryObjectListType rol = xlate.buildRegistryObjectList();
        if (val.hasErrors())
            fail( ValFactory.toJson(new ValErrors(val)));
        else
            System.out.println(ValFactory.toJson(val));

        assertNotNull(rol);
        List<String> objectTypes = new ArrayList<>();
        for (JAXBElement ele : rol.getIdentifiable()) {
            objectTypes.add(ele.getName().getLocalPart());
        }
        assertEquals(3, objectTypes.size());
        assertTrue(objectTypes.contains("RegistryPackage"));
        assertTrue(objectTypes.contains("ExtrinsicObject"));
        assertTrue(objectTypes.contains("Association"));
    }


}
