<TestScript xmlns="http://hl7.org/fhir">
    <fixture id="patient-bundle">
        <!--
           This patient comes out of the test Patient cache
        -->
        <autocreate value="false"/>
        <autodelete value="false"/>
        <resource>
            <reference value="Patient/Alex_Alder"/>
        </resource>
    </fixture>
    <variable>
        <name value="patientResourceId"/>
        <expression value="Bundle.entry.fullUrl"/>
        <sourceId value="patient-bundle"/>
    </variable>
    <variable>
        <name value="badPdbReferenceMasterIdentifier"/>
        <expression value="Bundle.entry.select(resource as DocumentReference).masterIdentifier.value"/>
        <sourceId value="bad-pdb-bundle"/>
    </variable>
    <variable>
        <name value="badPdbReferenceStatus"/>
        <expression value="Bundle.entry.select(resource as DocumentReference).status.value"/>
        <sourceId value="bad-pdb-bundle"/>
    </variable>
    <variable>
        <name value="patientIdentifier"/>
        <defaultValue value="Bundle.entry.select(resource as Patient).identifier[0]"/>
    </variable>
    <variable>
        <name value="patientIdentifierSearchParamValue"/>
        <expression value="${patientIdentifier}.system.value + '|' + ${patientIdentifier}.value.value"/>
        <sourceId value="patient-bundle"/>
    </variable>
    <variable>
        <name value="dependsOnTestId"/>
        <defaultValue value="MHDv4_DocumentRecipient_minimal/1_Prerequisite_Single_Document_with_Binary"/>
    </variable>
    <variable>
        <name value="isComprehensiveMetadataExpected"/>
        <defaultValue value="true"/>
    </variable>


    <setup>
        <modifierExtension url="urn:noErrors"/>
        <action>
            <operation>
                <description value="Get fixture string"/>
                <modifierExtension url="https://github.com/usnistgov/asbestos/wiki/TestScript-Import">
                    <extension url="component">
                        <valueString value="../../Library/GetFixtureString.xml"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="'2'"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="'?fixtureId=bad-pdb&amp;baseTestCollection=MHD_DocumentRecipient_minimal&amp;baseTestName=Missing_DocumentManifest'"/>
                    </extension>
                    <extension url="urn:fixture-out">
                        <valueString value="bad-pdb-bundle"/>
                    </extension>
                </modifierExtension>
            </operation>
        </action>
        <action>
            <operation>
                <description value="Get the document responder base address from a prerequisite PDB submission.
               (This test assumes the document responder base address in PDB response does not change between consecutive PDB submissions.)"/>
                <modifierExtension url="https://github.com/usnistgov/asbestos/wiki/TestScript-Import">
                    <extension url="component">
                        <valueString value="../../Library/DocumentResponderBaseAddress.xml"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="dependsOnTestId"/>
                    </extension>
                    <extension url="urn:variable-out">
                        <valueString value="docRespBaseAddress"/>
                    </extension>
                </modifierExtension>
            </operation>
        </action>
    </setup>

    <test>
        <modifierExtension url="urn:noErrors"/>
        <description value="Submit a PDB with a missing List (SubmissionSet)."/>
        <action>
            <operation>
                <description value="Submit."/>
                <modifierExtension url="https://github.com/usnistgov/asbestos/wiki/TestScript-Import">
                    <extension url="component">
                        <valueString value="../../Library/PDBFails.xml"/>
                    </extension>
                    <extension url="urn:fixture-in">
                        <valueString value="bad-pdb-bundle"/>
                    </extension>
                    <extension url="urn:fixture-out">
                        <valueString value="pdb-response"/>
                    </extension>
                    <extension url="urn:variable-in-no-translation">
                        <valueString value="patientResourceId"/>
                    </extension>
                </modifierExtension>
            </operation>
        </action>
    </test>
    <test>
        <description value="PDB Validations."/>
        <action>
            <modifierExtension url="urn:asbestos:test:action:expectFailure"/>
            <operation>
                <description value="Module usage description: TestScript import call to bridge an aggregate TestScript."/>
                <modifierExtension url="https://github.com/usnistgov/asbestos/wiki/TestScript-Import">
                    <extension url="component">
                        <valueString value="../../Library/AggregateModule/MHDv3xBundleEvalAggregateResult.xml"/>
                    </extension>
                    <extension url="urn:fixture-in">
                        <valueString value="pdb-bundle"/>
                    </extension>
                    <extension url="urn:fixture-in">
                        <valueString value="pdb-response"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="isComprehensiveMetadataExpected"/>
                    </extension>
                </modifierExtension>
            </operation>
        </action>
    </test>
    <test>
        <description value="Search DocumentReference."/>
        <action>
            <operation>
                <description value="Module call: Verify updates to server."/>
                <modifierExtension url="https://github.com/usnistgov/asbestos/wiki/TestScript-Import">
                    <extension url="component">
                        <valueString value="../../Library/SearchDocRef.xml"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="badPdbReferenceMasterIdentifier"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="badPdbReferenceStatus"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="patientIdentifierSearchParamValue"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="docRespBaseAddress"/>
                    </extension>
                    <extension url="urn:variable-in">
                        <valueString value="'0'"/>
                    </extension>
                </modifierExtension>
            </operation>
        </action>
    </test>

</TestScript>