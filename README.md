# Terminal Core Module

This is a standalone Android module that provides the core functionality for the `Operit Terminal` application. It is designed as a reusable component, exposing its features through a centralized `TerminalManager` and a corresponding AIDL interface for inter-process communication.

## Module Responsibilities

The `terminal-core` module is responsible for the following core tasks:

-   **Session Management**: Creating, switching, and closing multiple independent terminal sessions.
-   **Command Execution**: Handling the dispatch of commands and interacting with the underlying shell environment.
-   **State Management**: Acting as a single source of truth for the terminal's state, including session details, command history, and the current working directory. This state is exposed via Kotlin Flows.
-   **Event Notification**: Broadcasting events such as command output and directory changes through Kotlin Flows and an AIDL callback mechanism.

## Key Components

-   **`TerminalManager`**: A singleton class that serves as the main entry point for interacting with the module. It encapsulates all core logic and exposes reactive streams (Kotlin Flows) for observing the terminal's state.

-   **`TerminalService`**: An Android `Service` that wraps the `TerminalManager`. It exposes the terminal's functionality via AIDL (`ITerminalService`), allowing it to be used as a background service and enabling communication from other processes.

-   **AIDL Interface (`ITerminalService.aidl`, `ITerminalCallback.aidl`)**: Defines the contract for communication between the `TerminalService` and its clients. This allows the UI to run in a separate process from the terminal engine, preventing the terminal session from being terminated if the UI is closed.

## Technical Implementation

-   **Architecture**: The module utilizes a reactive architecture, with Kotlin Flows at its core for state management and event propagation.
-   **Concurrency**: Asynchronous operations are managed using Kotlin Coroutines, ensuring that the main thread is not blocked.
-   **Communication**: While designed for IPC with AIDL, the `TerminalManager` can also be used directly within the same process for a simpler setup.

## Usage

This module can be integrated as a Git Submodule. The client application can either bind to the `TerminalService` for background operation and IPC, or directly access the `TerminalManager` singleton if running in the same process. Refer to the main project's `README.md` for a detailed example of the AIDL interface.

### Session-scoped shell commands

Direct `TerminalManager` clients can create a session that is not shown as a terminal tab and execute isolated shell commands in that exact session:

```kotlin
val session = terminalManager.createNewSession(
    title = "Tool execution",
    presentation = SessionPresentation.BACKGROUND
)
val commandId = terminalManager.sendShellCommandToSession(
    sessionId = session.id,
    command = command
)
```

Observe `commandExecutionEvents` by both `sessionId` and `commandId`. A completed shell command includes its real `exitCode`. Cancellation code can call `cancelCommand(sessionId, commandId)` from a non-cancellable flow cleanup path. Queued commands are removed without interrupting the active command; an active command receives Ctrl+C only when both identifiers match.

Call `closeSessionAndWait(sessionId)` when session ownership must be released deterministically. A successful return means the provider session and PTY have been closed and the read job has finished. `TerminalService.closeSession` uses this waiting path. `TerminalManager.closeSession(sessionId)` is the non-blocking UI wrapper: it schedules the same close operation and returns before completion.

`sendCommand` and `sendCommandToSession` remain raw terminal-input APIs. They do not use the isolated `sh -c` execution protocol and do not report a shell exit code.
