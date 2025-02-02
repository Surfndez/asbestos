package gov.nist.asbestos.mhd.translation.attribute;

import gov.nist.asbestos.client.Base.IVal;
import gov.nist.asbestos.client.client.FhirClient;
import gov.nist.asbestos.client.resolver.Ref;
import gov.nist.asbestos.client.resolver.ResourceCacheMgr;
import gov.nist.asbestos.client.resolver.ResourceWrapper;
import gov.nist.asbestos.serviceproperties.ServiceProperties;
import gov.nist.asbestos.serviceproperties.ServicePropertiesEnum;
import gov.nist.asbestos.simapi.validation.Val;
import gov.nist.asbestos.simapi.validation.ValE;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PatientId implements IVal {
    private String patientid = "";
    private String aa = "";
    private String id = "";
    private ResourceCacheMgr resourceCacheMgr = null;
    private Val val;
    private FhirClient fhirClient = null;

    public PatientId setPatientid(String patientid) {
        this.patientid = patientid;
        this.aa = "";
        this.id = "";
        String[] parts = patientid.split("\\^\\^\\^");
        if (parts.length == 2) {
            id = parts[0].trim();
            String theAa = parts[1].trim();
            String[] aaParts = theAa.split("&");
            if (aaParts.length >= 2) {
                aa = aaParts[1].trim();
            }
        }
        return this;
    }

    public String getAa() {
        return aa;
    }

    public String getId() {
        return id;
    }

//    Ref patientServer = new Ref("http://localhost:8080/fhir/fhir");
    Ref patientServer = new Ref(ServiceProperties.getInstance().getPropertyOrThrow(ServicePropertiesEnum.HAPI_FHIR_BASE));


    public Optional<Reference> getFhirReference() {
        Objects.requireNonNull(resourceCacheMgr);
        Objects.requireNonNull(val);
        Objects.requireNonNull(fhirClient);
        String system = "urn:oid:" + getAa();
        String id = getId();
        List<String> searchParams = new ArrayList<>();
        searchParams.add("identifier=" + system + "|" + id);
        List<ResourceWrapper> results = fhirClient.search(patientServer, Patient.class, searchParams, true, false);

        //List<ResourceWrapper> results = resourceCacheMgr.search(null, Patient.class, searchParams, true);
        if (results.isEmpty()) {
            val.add(new ValE("DocumentEntryToDocumentReference: cannot find Patient resource for " + system + "|" + id).asError());
            return Optional.empty();
        }
        return Optional.of(new Reference(results.get(0).getRef().toString()));
    }

    public PatientId setResourceCacheMgr(ResourceCacheMgr resourceCacheMgr) {
        this.resourceCacheMgr = resourceCacheMgr;
        return this;
    }

    public void setVal(Val val) {
        this.val = val;
    }

    public PatientId setFhirClient(FhirClient fhirClient) {
        this.fhirClient = fhirClient;
        return this;
    }
}
