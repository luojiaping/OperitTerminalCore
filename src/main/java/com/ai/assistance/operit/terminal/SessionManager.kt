package com.ai.assistance.operit.terminal

import android.util.Log
import com.ai.assistance.operit.terminal.data.SessionPresentation
import com.ai.assistance.operit.terminal.data.TerminalSessionData
import com.ai.assistance.operit.terminal.data.TerminalState
import com.ai.assistance.operit.terminal.provider.type.TerminalType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 终端会话管理器
 * 负责管理多个终端会话的可观察状态
 */
class SessionManager {
    
    private val _state = MutableStateFlow(TerminalState())
    val state: StateFlow<TerminalState> = _state.asStateFlow()
    
    /**
     * 创建新会话
     * 
     * @param title 会话标题
     * @param terminalType 终端类型
     * @param presentation 会话是否展示为终端标签页
     */
    fun createNewSession(
        title: String? = null,
        terminalType: TerminalType,
        presentation: SessionPresentation = SessionPresentation.TAB
    ): TerminalSessionData {
        lateinit var newSession: TerminalSessionData
        _state.update { currentState ->
            val sessionCount = currentState.sessions.size + 1
            val defaultTitle = when (terminalType) {
                TerminalType.LOCAL -> "Ubuntu $sessionCount"
                TerminalType.SSH -> "SSH $sessionCount"
                else -> "Terminal $sessionCount"
            }
            newSession = TerminalSessionData(
                title = title ?: defaultTitle,
                terminalType = terminalType,
                presentation = presentation
            )
            currentState.copy(
                sessions = currentState.sessions + newSession,
                currentSessionId =
                    if (presentation == SessionPresentation.TAB) {
                        newSession.id
                    } else {
                        currentState.currentSessionId
                    }
            )
        }
        
        Log.d("SessionManager", "Created new session: ${newSession.id} (type: $terminalType)")
        return newSession
    }
    
    /**
     * 切换到指定会话
     */
    fun switchToSession(sessionId: String): Boolean {
        var switched = false
        _state.update { currentState ->
            if (currentState.sessions.any {
                    it.id == sessionId && it.presentation == SessionPresentation.TAB
                }) {
                switched = true
                currentState.copy(currentSessionId = sessionId)
            } else {
                currentState
            }
        }
        if (switched) {
            Log.d("SessionManager", "Switched to session: $sessionId")
        } else {
            Log.w("SessionManager", "Session not found: $sessionId")
        }
        return switched
    }
    
    internal fun detachSession(sessionId: String): TerminalSessionData? {
        var detachedSession: TerminalSessionData? = null
        _state.update { currentState ->
            val sessionToClose = currentState.sessions.find { it.id == sessionId }
            if (sessionToClose == null) return@update currentState

            detachedSession = sessionToClose
            val updatedSessions = currentState.sessions.filter { it.id != sessionId }
            val newCurrentSessionId = if (currentState.currentSessionId == sessionId) {
                updatedSessions.firstOrNull {
                    it.presentation == SessionPresentation.TAB
                }?.id
            } else {
                currentState.currentSessionId
            }
            
            currentState.copy(
                sessions = updatedSessions,
                currentSessionId = newCurrentSessionId
            )
        }

        if (detachedSession != null) {
            Log.d("SessionManager", "Detached session: $sessionId")
        }
        return detachedSession
    }
    
    /**
     * 更新会话数据
     */
    fun updateSession(sessionId: String, updater: (TerminalSessionData) -> TerminalSessionData) {
        _state.update { currentState ->
            val updatedSessions = currentState.sessions.map { session ->
                if (session.id == sessionId) {
                    updater(session)
                } else {
                    session
                }
            }
            currentState.copy(sessions = updatedSessions)
        }
    }
    
    /**
     * 保存会话的滚动位置
     */
    fun saveScrollOffset(sessionId: String, scrollOffset: Float) {
        updateSession(sessionId) { session ->
            session.copy(scrollOffsetY = scrollOffset)
        }
    }
    
    /**
     * 获取会话的滚动位置
     */
    fun getScrollOffset(sessionId: String): Float {
        return getSession(sessionId)?.scrollOffsetY ?: 0f
    }
    
    /**
     * 获取当前会话
     */
    fun getCurrentSession(): TerminalSessionData? {
        return _state.value.currentSession
    }
    
    /**
     * 获取指定会话
     */
    fun getSession(sessionId: String): TerminalSessionData? {
        return _state.value.sessions.find { it.id == sessionId }
    }
    
}
