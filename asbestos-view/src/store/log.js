import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

import {LOG} from '../common/http-common'

export const logStore = {
    state() {
        return {
            // private to the log viewer
            eventSummaries: [],  // list { eventName: xx, resourceType: yy, verb: GET|POST, status: true|false, ipAddr: addr }
            session: null,
            channel: null,
            loaded: false,
            analysis: null,
            validationServer: null,
            validationResult: null,
//            eventIdOfAnalysis: null,
        }
    },
    mutations: {
        setValidationResult(state, result) {
            state.validationResult = result
        },
        setValidationServer(state, server) {
            state.validationServer = server
        },
        setAnalysis(state, analysis) {
//            const analysis = data.analysis
//            const eventId = data.eventId
            state.analysis = analysis
//            state.eventIdOfAnalysis = eventId
        },
        resetLogLoaded(state) {
            state.loaded = false
        },
        setEventSummaries(state, summaries) {
            state.eventSummaries = summaries
            state.loaded = true
        },
        setLogSession(state, session) {
            state.session = session
        },
        setLogChannel(state, channel) {
            state.channel = channel
        }
    },
    getters: {
        ipAddresses: state => {
            const holder =  state.eventSummaries.map(x => {
                return x.ipAddr
            })
            return holder.filter((item, index) => {
                return holder.indexOf(item) === index
            })
        }
    },
    actions: {
        async loadEventSummaries({commit, rootState}, parms) {
            const channel = parms.channel
            const session = parms.session
            commit('setLogChannel', channel)
            commit('setLogSession', session)
            if (!rootState.base.session) {
                commit('setError', 'Session not set in logStore.loadEventSummaries')
                console.error('Session not set')
                return
            }
            if (!rootState.base.channelId) {
                commit('setError', 'Channel not set in logStore.loadEventSummaries')
                console.error('Channel not set')
                return
            }
            try {
                const rawSummaries = await LOG.get(`${rootState.base.session}/${rootState.base.channelId}`, {
                    params: {
                        summaries: 'true'
                    }
                })
                const eventSummaries = rawSummaries.data.sort((a, b) => {
                    if (a.eventName < b.eventName) return 1
                    return -1
                })
                commit('setEventSummaries', eventSummaries)
            } catch (error) {
                commit('setError', error)
                console.error(error)
            }
        },
        async getLogEventAnalysis({commit}, parms) {
            const channel = parms.channel
            const session = parms.session
            const eventId = parms.eventId
            const requestOrResponse = parms.requestOrResponse

            try {
                const url = `analysis/event/${session}/${channel}/${eventId}/${requestOrResponse}?validation=true`
                const result = await LOG.get(url)
                //const data = {analysis: result.data, eventId: eventId}
                commit('setAnalysis', result.data)
                //console.log(`analysis available`)
            } catch (error) {
                commit('setError', error)
                console.error(error)
            }
        },
        async getLogEventAnalysisForObject({commit}, parms) {
            const ignoreBadRefs = parms.ignoreBadRefs
            const resourceUrl = parms.resourceUrl
            const gzip = parms.gzip
            try {
                const url = `analysis/url/?url=${resourceUrl};gzip=${gzip};ignoreBadRefs=${ignoreBadRefs}`
                console.log(`getAnalysis ${url}`)
                const result = await LOG.get(url)
                //const data = {analysis: result.data, eventId: eventId}
                commit('setAnalysis', result.data)
            } catch (error) {
                commit('setError', error)
                console.error(error)
            }
        },
        async getValidationServer({commit}) {
            try {
                const url = `ValidationServer`
                const result = await LOG.get(url)
                commit('setValidationServer', result.data.value)
            } catch (error) {
                commit('setError', error)
                console.error(error)
            }
        },
        async getValidation({commit}, parms) {
            const resourceType = parms.resourceType
            const qurl = parms.url
            try {
                const url = `Validation/${resourceType}?url=${qurl}`
                const result = await LOG.get(url)
                commit('setValidationResult', result.data)
            } catch (error) {
                commit('setError', error)
                console.error(error)
            }
        }
    }
}
