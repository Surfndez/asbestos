package gov.nist.asbestos.testEngine.engine;

import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import com.google.gson.Gson;
import gov.nist.asbestos.client.client.Format;
import gov.nist.asbestos.client.debug.AssertionFieldDescription;
import gov.nist.asbestos.client.debug.AssertionFieldSupport;
import gov.nist.asbestos.client.debug.AssertionFieldValueDescription;
import gov.nist.asbestos.client.debug.DebugCopyAssertException;
import gov.nist.asbestos.client.debug.StopDebugTestScriptException;
import gov.nist.asbestos.client.debug.TestScriptDebugInterface;
import gov.nist.asbestos.client.debug.TestScriptDebugState;
import gov.nist.asbestos.client.debug.TsEnumerationCodeExtractor;
import gov.nist.asbestos.client.events.UIEvent;
import gov.nist.asbestos.client.resolver.Ref;
import gov.nist.asbestos.serviceproperties.ServiceProperties;
import gov.nist.asbestos.serviceproperties.ServicePropertiesEnum;
import gov.nist.asbestos.testEngine.engine.fixture.FixtureComponent;
import java.util.logging.Logger;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.BaseResource;
import org.hl7.fhir.r4.model.Enumeration;
import org.hl7.fhir.r4.model.TestReport;
import org.hl7.fhir.r4.model.TestScript;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class TestScriptDebugger implements TestScriptDebugInterface {
    private static Logger log = Logger.getLogger(TestScriptDebugger.class.getName());
    /**
     * An implementation of the TestScript Test Engine
     */
    TestEngine te;
    /**
     * If testScriptDebugState is null, then TestScript is being run normally. ie., TestScript is not being debugged.
     */
    private TestScriptDebugState state;

    public TestScriptDebugger(TestEngine testEngine, TestScriptDebugState state) {
        this.te = testEngine;
        this.state = state;
    }

    public ModularEngine getMyModularEngine() {
        ModularEngine me = te.getModularEngine();
        if (me == null) {
            if (te.parent != null && te.parent.getModularEngine() !=null) {
                me = te.parent.getModularEngine();
            }
        }
        return me;
    }
    @Override
    public void onBreakpoint() {
        ModularEngine me = this.getMyModularEngine();
        if (me != null) {
            me.saveLogs(); // Without this getTestReportsAsJson is empty
        } else {
            log.severe("getModularEngine is null: log cannot be saved!");
        }
    }

    @Override
    public String getLogAtBreakpoint() {
        ModularEngine me = this.getMyModularEngine();
        if (me != null) {
            return me.reportsAsJson();
        } else {
            log.severe("getModularEngine is null!");
            getState().sendUnexpectedError();
            return "";
        }
    }

    @Override
    public TestScriptDebugState getState() {
        return state;
    }

    @Override
    public void pauseIfBreakpoint() {
        boolean isBreakpoint = state.isBreakpoint();
        if (isBreakpoint) {
            onBreakpoint();
            sendBreakpointHit(false);
            waitOnBreakpoint();
        }
    }


    @Override
    public void pauseIfBreakpoint(final String parentType, final Integer parentIndex, final TestScript.SetupActionAssertComponent assertComponent, final Integer childPartIndex) {
        TestScriptDebugState state = getState();
        state.setCurrentExecutionIndex(parentType, parentIndex, childPartIndex);

        boolean isBreakpoint = state.isBreakpoint();
        if (isBreakpoint) {
            onBreakpoint();
            sendBreakpointHit(true);
            final List<String> evalElementList = Arrays.asList("label","description","direction","compareToSourceId","compareToSourceExpression","compareToSourcePath","contentType","expression","headerField","minimumId",
                    "navigationLinks","operator","path","requestMethod","requestURL","resource","response","responseCode","sourceId","validateProfileId","value","warningOnly");
            do {
                // Must Pause first before Eval can be requested
                waitOnBreakpoint(); // if eval, exit pause
                if (state.getDebugEvaluateModeWasRequested().get()) { // Only assertion-eval is supported for now. Need to address TestScript Operations later
                    state.resetEvalModeWasRequested();
                    String evalJsonString = state.getEvalJsonString();
                    if (evalJsonString == null) {
                        // Original assertion was requested
                        // Prepare user-selectable type information
                        // "valueTypes" : {"direction" : [{"codeValue":"req","displayName":"","definition":""},...],
                        // Field descriptions are the standard FHIR fields based on assertion class annotation
                        sendOriginalAssert(assertComponent, state, evalElementList);
                    } else {
                        // Eval was requested
                        doEval(assertComponent, state, evalElementList, evalJsonString);
                    }
                } else if (state.getDebugEvaluateForResourceMode().get()) {
                   state.resetEvalForResourceMode();
                   String evalJsonString = state.getEvalJsonString();
                    doEvalForResource(assertComponent, state, evalElementList, evalJsonString);
                }
            } while (! state.getResume().get() && ! state.getStopDebug().get());
        }
    }

    private void sendOriginalAssert(TestScript.SetupActionAssertComponent assertComponent, TestScriptDebugState state, List<String> evalElementList) {
        List<AssertionFieldDescription> fieldDescriptions = new ArrayList<>();
        // Override fields are used to inject dropdowns into a text-only field
        List<AssertionFieldDescription> overrideFields = new ArrayList<>();
        AssertionFieldSupport fieldSupport = new AssertionFieldSupport();
        if (state.getRequestAnnotations().get()) {
            // gather static FHIR enumeration values and other field support values as requested only the first time around
            setAssertionEnumerationTypes(evalElementList, fieldDescriptions);
            fieldSupport.setFhirEnumerationTypes(fieldDescriptions);
            List<String> resourceNames = new ArrayList<String>(Ref.getResourceNames());
            overrideFields.add(formatField("resource", new ArrayList<String>(resourceNames)));
            List<String> contentTypeList = new ArrayList<String>(Format.getFormats());
            Collections.sort(contentTypeList);
            overrideFields.add(formatField("contentType", contentTypeList));
            overrideFields.add(formatField("warningOnly", Arrays.asList("false","true")));
            fieldSupport.setOverrideFieldTypes(overrideFields);
        }
        Set<String> fixtureIds = te.getFixtureMgr().keySet();
        AssertionFieldDescription fixtureIdsDesc = formatField("sourceId", new ArrayList<String>(fixtureIds));

        // When the evalJsonString is empty, Send original assertion as a template for the user to edit an assertion
        String assertionJsonStr = new Gson().toJson(assertComponent);
        String fieldSupportStr = new Gson().toJson(fieldSupport);
        String fixtureIdsStr = new Gson().toJson(fixtureIdsDesc);

        state.sendAssertionStr(assertionJsonStr, fieldSupportStr, fixtureIdsStr);
    }


    private void doEvalForResource(TestScript.SetupActionAssertComponent assertComponent, TestScriptDebugState state, List<String> evalElementList, String evalJsonString) {
        TestScript.SetupActionAssertComponent copy = null;
        TestReport.SetupActionAssertComponent actionReport = new TestReport.SetupActionAssertComponent();
        try {
            copy = makeAssert(assertComponent, state, evalElementList, evalJsonString);
            if (copy != null) {
                String typePrefix = "contained.action";
                te.doAssert(typePrefix, copy, actionReport);
            }
        } catch (DebugCopyAssertException ex) {
            state.sendDebugAssertionEvalResultStr("error", ex.toString(), ex.getPropKey());
            return;
        }

        String code = actionReport.getResult().toCode();
        String fixtureId = copy.getSourceId();
        String propKey = "";
        String fixtureResourceName = null;
        String fixtureProfileUrl = null;
        String analysisUrl = null;
        String resourcesString = TestScriptDebugState.quoteString("");
        String direction = null;
        String scalarValueString = null;

        try {
            propKey = "sourceId";
            if (fixtureId != null && !"".equals(fixtureId)) {
                FixtureComponent selectedFixtureComponent = te.getFixtures().get(fixtureId);
                Class<?> clazz = selectedFixtureComponent.getResourceResource().getClass();
                ResourceDef annotation = clazz.getAnnotation(ResourceDef.class);
                if (annotation != null) {
                    fixtureResourceName = annotation.name();
                    fixtureProfileUrl = URLEncoder.encode(annotation.profile(), StandardCharsets.UTF_8.toString());
                }
                if (selectedFixtureComponent.getCreatedByUIEvent() != null) {
                    // It appears the getResourceWrapper isRequest is false for static fixture, so the condition below is needed
                    direction = selectedFixtureComponent.isStatic()? "request" : selectedFixtureComponent.getResourceWrapper().isRequest() ? "request" : "response";
//                    URI eventUri = getAnalysisURI(selectedFixtureComponent.getCreatedByUIEvent(), direction);
//                    analysisUrl = URLEncoder.encode(eventUri.toString(), StandardCharsets.UTF_8.toString());
                    analysisUrl = URLEncoder.encode(selectedFixtureComponent.getCreatedByUIEvent().getEventName(), StandardCharsets.UTF_8.toString());
                }
            }
        } catch (Exception ex) {
            state.sendDebugAssertionEvalResultStr("error", "Fixture analysis exception: " + ex.toString(), propKey);
            return;
        }

        try {
            propKey = "expression";
            String fhirPath = copy.getExpression();
            if (fhirPath != null && ! "".equals(fhirPath)) {
                BaseResource resource = te.getFixtures().get(fixtureId).getResourceResource();
                List<Base> resources = FhirPathEngineBuilder.evalForResources(resource, fhirPath);
                List<String> baseNames = new ArrayList<>();
                if (resources != null) {
                    int counter = 0;
                    for (Base b : resources) {
                        // Assume all baseTypes are the same
                        String indexHint =   resources.size() > 1 ? "[" + counter++ + "]":"";
                        if (!fhirPath.endsWith(".resource")) {
                            try {
                                List<Base> resourceType = FhirPathEngineBuilder.evalForResources(resource, fhirPath + indexHint + ".resource");
                                if (resourceType != null && resourceType.size() == 1) {
                                    indexHint += ": " + resourceType.get(0).getClass().getSimpleName();
                                }
                            } catch (Exception ex) {}
                            baseNames.add(((resources.size()==1 && !indexHint.equals(""))?".resource":b.fhirType()) + indexHint);
                        } else {
                            baseNames.add(b.fhirType() + indexHint);
                        }
                    }
                    List<String> myList =
                            baseNames
                                    .stream()
                                    .map(TestScriptDebugState::quoteString)
                                    .collect(Collectors.toList());
                    resourcesString = String.join(",", myList);
                    if (resources.size() == 1 && ! resources.get(0).isResource()) {
                        List<Base> valueOnlyType = FhirPathEngineBuilder.evalForResources(resource, fhirPath + (fhirPath.endsWith(".value")?"":".value"));
                        if (valueOnlyType != null && valueOnlyType.size() == 1) {
                            scalarValueString = URLEncoder.encode(FhirPathEngineBuilder.evalForString(valueOnlyType.get(0)), StandardCharsets.UTF_8.toString());
                        }
                    }
                }
            }
        } catch (Exception ex) {
            state.sendDebugAssertionEvalResultStr("error", "Expression resource exception: " + ex.toString(), propKey);
            return;
        }

        state.sendEvalForResourcesResult(code, actionReport.getMessage(), "", resourcesString, fixtureResourceName, fixtureProfileUrl, analysisUrl, direction, scalarValueString);
        return;
    }

    private void doEval(TestScript.SetupActionAssertComponent assertComponent, TestScriptDebugState state, List<String> evalElementList, String evalJsonString) {
        TestScript.SetupActionAssertComponent copy = null;
        try {
            copy = makeAssert(assertComponent, state, evalElementList, evalJsonString);
            if (copy != null) {
                String typePrefix = "contained.action";
                TestReport.SetupActionAssertComponent actionReport = new TestReport.SetupActionAssertComponent();
                te.doAssert(typePrefix, copy, actionReport);
                String code = actionReport.getResult().toCode();
                if ("fail".equals(code)) {
                    log.info("copy eval failed.");
                } else if ("error".equals(code)) {
                    log.info("copy eval error.");
                }
                state.sendDebugAssertionEvalResultStr(code, actionReport.getMessage(), "");
            }
        } catch (DebugCopyAssertException ex) {
            state.sendDebugAssertionEvalResultStr("error", ex.toString(), ex.getPropKey());
        }
    }

    private TestScript.SetupActionAssertComponent makeAssert(TestScript.SetupActionAssertComponent assertComponent, TestScriptDebugState state, List<String> evalElementList, String evalJsonString) throws DebugCopyAssertException  {
        ListIterator<String> it = evalElementList.listIterator();
        TestScript.SetupActionAssertComponent copy = null;
        try {
            Map<String, String> myMap = new Gson().fromJson(evalJsonString, Map.class);
            if (! myMap.keySet().containsAll(evalElementList)) {
                throw new RuntimeException("myMap does not contain all the required keys");
            }
            copy = assertComponent.copy();
            copy.setLabel(myMap.get(it.next()));
            copy.setDescription(myMap.get(it.next()));
            copy.setDirection(TestScript.AssertionDirectionType.fromCode(myMap.get(it.next())));
            copy.setCompareToSourceId(myMap.get(it.next()));
            copy.setCompareToSourceExpression(myMap.get(it.next()));
            copy.setCompareToSourcePath(myMap.get(it.next()));
            copy.setContentType(myMap.get(it.next()));
            copy.setExpression(myMap.get(it.next()));
            copy.setHeaderField(myMap.get(it.next()));
            copy.setMinimumId(myMap.get(it.next()));
            copy.setNavigationLinks(Boolean.parseBoolean(myMap.get(it.next())));
            copy.setOperator(TestScript.AssertionOperatorType.fromCode(myMap.get(it.next())));
            copy.setPath(myMap.get(it.next()));
            copy.setRequestMethod(TestScript.TestScriptRequestMethodCode.fromCode(myMap.get(it.next())));
            copy.setRequestURL(myMap.get(it.next()));
            copy.setResource(myMap.get(it.next()));
            copy.setResponse(TestScript.AssertionResponseTypes.fromCode(myMap.get(it.next())));
            copy.setResponseCode(myMap.get(it.next()));
            copy.setSourceId(myMap.get(it.next()));
            copy.setValidateProfileId(myMap.get(it.next()));
            copy.setValue(myMap.get(it.next()));
            copy.setWarningOnly(Boolean.parseBoolean(myMap.get(it.next())));
            return copy;
        } catch (Exception ex) {
            throw new DebugCopyAssertException("Copy exception: " + ex.toString(),  (it.hasPrevious()?it.previous():""));
        }
    }

    private AssertionFieldDescription formatField(String fieldName, List<String> values) {
        List<AssertionFieldValueDescription> fieldValues = new ArrayList<>();
        for (String s : values) {
            fieldValues.add(new AssertionFieldValueDescription(s, s, ""));
        }
       AssertionFieldDescription fieldDescription =  new AssertionFieldDescription(fieldName, "", "", fieldValues);
        return fieldDescription;
    }

    private void setAssertionEnumerationTypes(List<String> evalElementList, List<AssertionFieldDescription> fieldDescriptions) {
        try {
            for (String eStr : evalElementList) {
                String shortDefinition = "";
                String formalDefinition = "";
                List<AssertionFieldValueDescription> fieldValueTypes = new ArrayList<>();
                // Property names must exist in evalElementList
                Field f = TestScript.SetupActionAssertComponent.class.getDeclaredField(eStr);
                if (f != null) {
                    Description annotation = f.getAnnotation(Description.class);
                    if (annotation != null) {
                        shortDefinition = annotation.shortDefinition();
                        formalDefinition = annotation.formalDefinition();
                    }
                    if (f.getType().isAssignableFrom(Enumeration.class)) {
                        Type type = f.getGenericType();
                        if (type instanceof ParameterizedType) {
                            ParameterizedType paramType = (ParameterizedType)type;
                            if (paramType.getActualTypeArguments().length == 1) {
                                String typeArg = paramType.getActualTypeArguments()[0].getTypeName();
                                if (typeArg != null && ! "".equals(typeArg)) {
                                    Optional<Class<?>> optionalClass = Arrays.asList(TestScript.class.getDeclaredClasses()).stream()
                                            .filter(s -> s.getName().equals(typeArg))
                                            .findFirst();
                                    if (optionalClass.isPresent()) {
                                        for (Object o : optionalClass.get().getEnumConstants()) {
                                            if (o instanceof Enum) {
                                                if (! ((Enum) o).name().equals("NULL")) {
                                                    fieldValueTypes.add(new TsEnumerationCodeExtractor().apply(o));
                                                }
                                            }
                                        }

                                    }
                                }
                            }
                        }

                    }
                }
                fieldDescriptions.add(new AssertionFieldDescription(eStr, shortDefinition, formalDefinition, fieldValueTypes));
            }

        } catch (Exception ex) {
            // formalDefinition = "Not available.";
        }
    }

    @Override
    public void pauseIfBreakpoint(String parentType, Integer parentIndex) {
        pauseIfBreakpoint(parentType, parentIndex, null, false);
    }

    @Override
    public void pauseIfBreakpoint(String parentType, Integer parentIndex, Integer childPartIndex, boolean hasImportExtension) {
        TestScriptDebugState state = getState();
        state.setHasImportExtension(hasImportExtension);
        state.setCurrentExecutionIndex(parentType, parentIndex, childPartIndex);
        pauseIfBreakpoint();
    }


    @Override
    public void waitOnBreakpoint() {
        TestScriptDebugState state = getState();
        state.cancelResumeMode();

        synchronized (state.getLock()) {
            while (state.isWait()) { // Condition must be false to exit the wait and to protect from spurious wake-ups
                try {
                    state.getLock().wait(); // Release the lock and wait for getResume to be True
                } catch (InterruptedException ie) {
                }
            }
            if (state.getResume().get()) {
                log.info("Resuming " +  state.getSession().getId());
            } else if (state.getStopDebug().get()) {
//                throw new Error("KILL session: " + getSession().getId()); // This needs to throw a custom exception that does not show up in the test report
                throw new StopDebugTestScriptException("STOP debug session: " + state.getSession().getId());
            } else if (state.getDebugEvaluateModeWasRequested().get()) {
                log.info("Eval mode is true.");
            }
        }
    }

    private void sendBreakpointHit(boolean isEvaluable) {
        TestScriptDebugState state = getState();
        String reportsAsJson = getLogAtBreakpoint();
        String breakpointIndex = state.getCurrentExecutionIndex();
        log.info("pausing at " + breakpointIndex);
        state.getSession().getAsyncRemote().sendText(
                "{\"messageType\":\"breakpoint-hit\""
                        + ",\"testScriptIndex\":\"" + state.getTestScriptIndex() + "\""
                        + ",\"breakpointIndex\":\"" + breakpointIndex + "\""
                        + ",\"debugButtonLabel\":\"Resume\""
                        + ",\"isEvaluable\":\""+ isEvaluable +"\""
                        + ",\"testReport\":" + reportsAsJson  + "}"); // getModularEngine().reportsAsJson()

    }

    static URI getAnalysisURI(UIEvent uiEvent, String directionStr) {
        try {
            return new URI(ServiceProperties.getInstance().getPropertyOrThrow(ServicePropertiesEnum.FHIR_TOOLKIT_BASE)
                    + "/" + "analysis"
                    + "/" + "event"
                    + "/" + uiEvent.getTestSession()
                    + "/" + uiEvent.getChannelId()
                    + "/" + uiEvent.getEventName()
                    + "/" + directionStr
            );

        } catch (URISyntaxException e) {
            throw new Error(e);
        }

    }


}
