package gov.nist.asbestos.debug.testScript;


import gov.nist.asbestos.asbestosProxy.requests.Request;
import gov.nist.asbestos.sharedObjects.TestScriptDebugState;
import gov.nist.asbestos.simapi.tk.installation.Installation;
import gov.nist.asbestos.debug.testScript.requests.DebugTestScriptRequest;
import org.apache.log4j.Logger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;

@ServerEndpoint("/debugTestScript/{userType}/{param1}")
public class DebugTestScriptWebSocketEndpoint {
    private static Logger log = Logger.getLogger(DebugTestScriptWebSocketEndpoint.class);
    private static final ConcurrentHashMap<String, TestScriptDebugState> debugStateMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, ExecutorService> debugExecutorMap = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(@PathParam("userType") String userType, @PathParam("param1") String param1, Session session) {
        String sessionId = session.getId();
        log.info(String.format("New session Id: %s. userType: %s.", sessionId, userType));
        log.info(String.format("Open session(s): %d. stateMap size: %d. executorMap size: %d.", session.getOpenSessions().size(), debugStateMap.size(), debugExecutorMap.size()));

        if ("developer".equals(userType) && param1 != null) {
            // Only one debug request for the channel/testCollection/testScript is allowed
            debugStateMap.put(sessionId, new TestScriptDebugState(session, userType, param1, new ConcurrentSkipListSet()));
        } else if ("admin".equals(userType) && param1 != null) { // Should be protected by a servlet filter
//            } else if (myMap.get("count") != null) {
//            } else if (myMap.get("killAllDebuggers") != null) {
            // map may not contain sessionId when session is automatically closed!
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) throws Exception {
        log.info("raw onMessage: " + message);

        TestScriptDebugState state = debugStateMap.get(session.getId());
        if (message != null) {
            Map<String, Object> myMap = new Gson().fromJson(message, Map.class);

            if (myMap.get("uri") != null) {
                String uriString = (String) myMap.get("uri");
                if (uriString != null) {
                    if (myMap.get("breakpointList") != null) {
                        List<String> myList = (List<String>) myMap.get("breakpointList");
//                        String testScriptIndex = (String) myMap.get("testScriptIndex");
                        state.getBreakpointSet().addAll(myList);
                        startDebuggerThread(state, uriString);
                    } else {
                        String exception = "breakpointList is empty.";
                        log.error(exception);
                        throw new RuntimeException(exception);
                    }
                }
            } else if (myMap.get("resumeBreakpoint") != null) {
                List<String> updateList = (List<String>) myMap.get("breakpointList");
                doResumeBreakpoint(state, updateList);
            } else if (myMap.get("killDebug") != null) {
                killSession(state);
                // Create new exception for Kill because the next Test is run and the KILL exception is shown in the next Test.
                // - See how failures in Setup are handled. The Kill should act that way.
            } else if (myMap.get("requestOriginalAssertion") != null) {
                doRequestOriginalAssertion(state);
            } else if (myMap.get("debugEvalAssertion") != null) {
                String base64String = (String)myMap.get("base64String");
                doDebugEvaluate(state, base64String);
            }
            // else if stepOver
            // else if Restart

        }

    }

    @OnClose
    public void onClose(Session session) {
        final String sessionId = session.getId();
        log.info("Close: " + sessionId + ". debugStateMap has: " + debugStateMap.keySet().toString());

        if (debugStateMap.containsKey(sessionId)) {
            ExecutorService service = debugExecutorMap.get(sessionId);
            if (debugStateMap.get(sessionId).getKill().get()) {
                try {
                    service.awaitTermination(2, TimeUnit.SECONDS);
                } catch (Throwable t) {
                } finally {
                    log.info(String.format("Session %s was terminated: %s", sessionId, service.isTerminated()));

                }
            }

            log.info("is service terminated? " + service.isTerminated() + ". is shutdown? " + service.isShutdown());
            if (service.isTerminated()) {
                debugExecutorMap.remove(sessionId);
                debugStateMap.remove(sessionId);
            }

        }
    }

    @OnError
    public void onError(Throwable t) {
       log.error(t.toString());
    }

    private void startDebuggerThread(TestScriptDebugState state, String uriString) throws Exception {
        String sessionId = state.getSessionId();
        Request request = new Request(uriString, Installation.instance().externalCache());

        if (DebugTestScriptRequest.isRequest(request)) {
            ExecutorService executorService = Executors.newSingleThreadExecutor();
            if (executorService != null) {
                debugExecutorMap.put(sessionId, executorService);
                DebugTestScriptRequest debugger = new DebugTestScriptRequest(request, state);

                executorService.execute(debugger);
                executorService.shutdown();
            }
        }
    }

    private void doResumeBreakpoint(TestScriptDebugState state, List<String> updateList) {
        if (updateList != null) {
            state.getBreakpointSet().clear();
            state.getBreakpointSet().addAll(updateList);
        }

        synchronized (state.getLock()) {
           state.getResume().set(true);
           state.getLock().notify();
        }
    }

    private void doRequestOriginalAssertion(TestScriptDebugState state) {
        synchronized (state.getLock()) {
            state.resetEvalJsonString();
            state.getDebugEvaluateModeWasRequested().set(true);
            state.getLock().notify();
        }
    }

    private void doDebugEvaluate(TestScriptDebugState state, String base64String) {
        if (base64String != null) {
            state.setEvalJsonString(new String(Base64.getDecoder().decode(base64String)));
            synchronized (state.getLock()) {
                state.getDebugEvaluateModeWasRequested().set(true);
                state.getLock().notify();
            }
        }
    }


    private void killSession(TestScriptDebugState state) {
        String sessionId = state.getSessionId();
        log.info("killSession: " + sessionId);
        synchronized (state.getLock()) {
            state.cancelResumeMode();
            state.resetEvalModeWasRequested();
            state.getKill().set(true);
            state.getLock().notify();
        }
    }


}