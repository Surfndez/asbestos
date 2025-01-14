export default {
    data() {
        return {
        }
    },
     methods: {
         /**
          *
          * @param testScriptIndex - Only the index
          * @returns {string} - The fully qualified index including the test collection plus the test script index
          */
        getFqTestScriptIndexKey(testScriptIndex) {
            const testCollectionIndex = this.$store.getters.allServerTestCollectionNames.indexOf(this.testCollection)
            const key = testCollectionIndex + '.' + testScriptIndex // Follow proper key format
            return key
        },
        isDebuggable(testScriptIndex) {
            const hasDebugLabel = 'Debug' === this.getDebugActionButtonLabel(testScriptIndex)
            const key = this.getFqTestScriptIndexKey(testScriptIndex)
            const hasBreakpoints = this.$store.getters.hasBreakpoints(key)
            return (hasBreakpoints && hasDebugLabel)
        },
        isResumable(testScriptIndex) {
             return 'Resume' === this.getDebugActionButtonLabel(testScriptIndex)
        },
        isPreviousDebuggerStillAttached(testScriptIndex) {
            const key = this.getFqTestScriptIndexKey(testScriptIndex)
            const indexList = this.$store.state.debugTestScript.debugMgmtIndexList
            if (indexList !== null || indexList !== undefined) {
                // return  indexList.filter(o => o.testScriptIndex === key).length === 1
                return indexList.includes(key)
            }
            return false
        },
         async removeDebugger(testScriptIndex) {
             const key = this.getFqTestScriptIndexKey(testScriptIndex)
             await this.$store.dispatch('debugMgmt', {'cmd':'removeDebugger','testScriptIndex':key})
         },
        getDebugActionButtonLabel(testScriptIndex) {
            const fqTsIndex = this.getFqTestScriptIndexKey(testScriptIndex)
            if (fqTsIndex in this.$store.state.debugTestScript.showDebugButton) {
                let valObj = this.$store.state.debugTestScript.showDebugButton[fqTsIndex]
                if (valObj != undefined) {
                    return valObj.debugButtonLabel
                }
            }
            return "X"
        },
         getBreakpointCount(testScriptIndex) {
             const key = this.getFqTestScriptIndexKey(testScriptIndex)
             if (key in this.$store.state.debugTestScript.showDebugButton) {
                 const breakpointSet = this.$store.state.debugTestScript.breakpointMap.get(key)
                 if (breakpointSet)
                    return breakpointSet.size
             }
             return 0
         },
         getBreakpointsInDetails(obj) {
             let retObj = {'key': obj.breakpointIndex, 'childCount' : 0}
             const breakpointMap = this.$store.state.debugTestScript.breakpointMap
             if (breakpointMap.has(obj.testScriptIndex)) {
                 const breakpointSet = breakpointMap.get(obj.testScriptIndex)

                 var searchFn = function mySearchFn(currentVal /*, currentKey, set */) {
                     if (currentVal) {
                         if (currentVal.startsWith(this.key) && currentVal.length > this.key.length) {
                             this.childCount++
                         }
                     }
                 }
                 breakpointSet.forEach(searchFn, retObj /* retObj becomes 'this' inside the searchFn*/)
             }
             return retObj.childCount
         },
         isEvaluableAction(testScriptIndex) {
            const isCurrentTest = (testScriptIndex === this.$store.getters.getIndexOfCurrentTest)
            return isCurrentTest && this.$store.state.debugTestScript.evalMode
        },
        async stopDebugging(testScriptIndex) {
            await this.$store.dispatch('stopDebugTs', this.getFqTestScriptIndexKey(testScriptIndex))
        },
        async doDebug(testName) {  // server tests
            if (!testName)
                return
            await this.$store.dispatch('debugTestScript', testName)
        },
        async doStepOver(testScriptIndex) {
           await this.$store.dispatch('doStepOver', testScriptIndex)
        },
        async doFinish(testScriptIndex) {
            await this.$store.dispatch('doFinishRun', testScriptIndex)
        },
        async doDebugEvalMode() {
            await this.$store.dispatch('doDebugEvalMode')
        },
        getBreakpointIndex(testType, testIndex, actionIndex) {
            return testType + testIndex + (actionIndex !== undefined ?   "." + actionIndex : "")
        },
        getParentBreakpointIndex(parentTestIndex, testType, testIndex, actionIndex) {
            const normalIndex = this.getBreakpointIndex(testType, testIndex, actionIndex)
            if (parentTestIndex !== undefined && parentTestIndex !== null) {
               return parentTestIndex + '/' + normalIndex
            }
            return normalIndex
         },
         getBreakpointObj(breakpointIndex) {
            let obj = {testScriptIndex: this.currentMapKey, breakpointIndex: breakpointIndex}
            return obj
        },
        toggleBreakpointIndex(obj) {
            if (! this.$store.getters.hasBreakpoint(obj)) {
                return this.$store.dispatch('addBreakpoint', obj)

            } else {
                return this.$store.dispatch('removeBreakpoint', obj)
            }
        },
        removeAllBreakpoints(obj) {
            return this.$store.dispatch('removeAllBreakpoints', this.getBreakpointObj(obj))
        },
        debugTitle(testScriptIndex, testType, testIndex, actionIndex) {
            let obj = {testScriptIndex: testScriptIndex, breakpointIndex: testType + testIndex + "." + actionIndex}
            return this.$store.getters.getDebugTitle(obj);
        },
        closeModal() {
            this.$store.commit('setShowDebugEvalModal', false)
        },
        displayAdditionalIndexLabel(isDisplayOpen, breakpointIndex) {
            let bkptOptionEl = document.querySelector("span.breakpointGutterOption[data-breakpoint-index='"+ breakpointIndex + "']")
            if (bkptOptionEl) {
                if (isDisplayOpen) {
                    bkptOptionEl.classList.add('breakpointOptionHidden')
                } else {
                    bkptOptionEl.classList.remove('breakpointOptionHidden')
                }
            }
        },
     },
    computed: {
        isDebugFeatureEnabled() {
            return this.$store.getters.isDebugFeatureEnabled
        },
        currentMapKey()  {
            const testId = this.$store.state.testRunner.currentTest
            const mapKey = this.$store.getters.getMapKey(testId)
            return mapKey
        },
        isWaitingForBreakpoint() {
            return this.$store.state.debugTestScript.waitingForBreakpoint
        }
    }
}